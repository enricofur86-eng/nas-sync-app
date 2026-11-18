package it.furlan.nassync

import android.annotation.SuppressLint
import android.content.Context
import android.util.Xml
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Il NAS ha risposto, ma con un errore: riprovare non serve. */
class NasHttpException(val code: Int, message: String) : IOException(message)

/** Un file o una cartella sul NAS. path = percorso relativo, es. "Foto/2024/img.jpg" ("" = radice). */
data class RemoteEntry(
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
) {
    val name: String get() = path.substringAfterLast('/')
}

fun parentOf(path: String): String = path.substringBeforeLast('/', "")

class WebDavClient(
    baseUrl: String,
    user: String,
    pass: String,
    pin: String,
) {
    val base: HttpUrl = normalize(baseUrl)
    private val baseSegs = base.pathSegments.filter { it.isNotEmpty() }
    private val client: OkHttpClient

    init {
        val creds = Credentials(user, pass)
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val auth = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(creds))
            .with("basic", BasicAuthenticator(creds))
            .build()
        val b = OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(auth, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (pin.isNotEmpty()) {
            // Certificato "fatto in casa" del NAS: accetto solo quello confermato dall'utente
            val tm = PinningTrustManager(pin)
            b.sslSocketFactory(pinnedSslContext(tm).socketFactory, tm)
            b.hostnameVerifier(pinnedHostnameVerifier(pin))
        }
        client = b.build()
    }

    fun urlFor(path: String, dir: Boolean): HttpUrl {
        val b = base.newBuilder()
        if (path.isNotEmpty()) path.split('/').forEach { b.addPathSegment(it) }
        if (dir && path.isNotEmpty()) b.addPathSegment("")
        return b.build()
    }

    /** Elenca il contenuto di una cartella (un livello). */
    fun list(path: String): List<RemoteEntry> {
        val url = urlFor(path, true)
        val req = Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw NasHttpException(401, "Utente o password sbagliati")
            if (path.isEmpty() && (resp.code == 405 || resp.code == 404 || resp.code == 501)) {
                throw NasHttpException(
                    resp.code,
                    "L'indirizzo risponde, ma non è il WebDAV del NAS (errore ${resp.code}). " +
                        "Controlla porta e percorso: devono essere quelli del WebDAV del NAS."
                )
            }
            if (resp.code != 207 && !resp.isSuccessful) {
                throw NasHttpException(resp.code, "Il NAS ha risposto con errore ${resp.code} sulla cartella /$path")
            }
            val body = resp.body ?: throw IOException("Risposta vuota dal NAS")
            return parse(body.byteStream(), url, path)
        }
    }

    /** Carica un file del telefono sul NAS, nel percorso [path]. */
    fun put(path: String, file: File) {
        val req = Request.Builder()
            .url(urlFor(path, false))
            .put(file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw NasHttpException(resp.code, "Errore ${resp.code} caricando $path sul NAS")
        }
    }

    /** Elimina un file o una cartella (con tutto il contenuto) dal NAS. */
    fun delete(path: String, dir: Boolean) {
        val req = Request.Builder().url(urlFor(path, dir)).delete().build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return // gia' sparito
            if (!resp.isSuccessful) throw NasHttpException(resp.code, "Errore ${resp.code} eliminando $path dal NAS")
        }
    }

    /** Crea una cartella sul NAS. */
    fun mkcol(path: String) {
        val req = Request.Builder().url(urlFor(path, true)).method("MKCOL", null).build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 405) throw NasHttpException(405, "Esiste già una cartella con questo nome")
            if (!resp.isSuccessful) throw NasHttpException(resp.code, "Errore ${resp.code} creando la cartella")
        }
    }

    /** Scarica un file in [dest] passando da un file temporaneo. */
    fun download(
        path: String,
        dest: File,
        tmpSuffix: String,
        isCancelled: () -> Boolean,
        onBytes: (Long) -> Unit,
    ) {
        val req = Request.Builder().url(urlFor(path, false)).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw NasHttpException(resp.code, "Errore ${resp.code} scaricando $path")
            val body = resp.body ?: throw IOException("Risposta vuota per $path")
            val dir = dest.parentFile ?: throw IOException("Percorso non valido")
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Impossibile creare la cartella ${dir.name}")
            val tmp = File(dir, dest.name + tmpSuffix)
            try {
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            if (isCancelled()) throw IOException("Interrotto")
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            onBytes(n.toLong())
                        }
                    }
                }
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) throw IOException("Impossibile salvare $path")
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
    }

    private fun parse(input: InputStream, reqUrl: HttpUrl, parentPath: String): List<RemoteEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)
        val out = ArrayList<RemoteEntry>()
        val reqSegs = reqUrl.pathSegments.filter { it.isNotEmpty() }
        var href: String? = null
        var isDir = false
        var size = 0L
        var mtime = 0L
        val text = StringBuilder()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "response" -> { href = null; isDir = false; size = 0L; mtime = 0L }
                        "collection" -> isDir = true
                    }
                    text.setLength(0)
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "href" -> href = text.toString().trim()
                    "getcontentlength" -> text.toString().trim().toLongOrNull()?.let { size = it }
                    "getlastmodified" -> parseDate(text.toString().trim()).let { if (it > 0) mtime = it }
                    "response" -> {
                        val h = href
                        if (h != null) {
                            val p = hrefToPath(h, reqUrl, reqSegs, parentPath)
                            if (p != null && p.isNotEmpty() && p != parentPath) {
                                out.add(RemoteEntry(p, isDir, if (isDir) 0L else size, mtime))
                            }
                        }
                    }
                }
            }
            ev = parser.next()
        }
        return out
    }

    private fun hrefToPath(href: String, reqUrl: HttpUrl, reqSegs: List<String>, parentPath: String): String? {
        val u = reqUrl.resolve(href) ?: return null
        val segs = u.pathSegments.filter { it.isNotEmpty() }
        if (segs == reqSegs) return null // e' la cartella stessa
        val matchesBase = segs.size > baseSegs.size && segs.subList(0, baseSegs.size) == baseSegs
        if (matchesBase) return segs.drop(baseSegs.size).joinToString("/")
        // Il server usa un prefisso diverso: prendo solo il nome finale.
        val name = segs.lastOrNull() ?: return null
        return if (parentPath.isEmpty()) name else "$parentPath/$name"
    }

    private fun parseDate(s: String): Long = try {
        ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (e: Exception) {
        0L
    }

    companion object {
        private const val PROPFIND_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>" +
                "</d:prop></d:propfind>"

        fun normalize(input: String): HttpUrl {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
            val u = s.toHttpUrlOrNull() ?: throw IOException("Indirizzo non valido: $input")
            return if (u.encodedPath.endsWith("/")) u
            else u.newBuilder().encodedPath(u.encodedPath + "/").build()
        }
    }
}

/** Tiene un solo client per le impostazioni correnti. */
object Net {
    private var key: String? = null
    private var cached: WebDavClient? = null

    @Synchronized
    fun client(ctx: Context): WebDavClient {
        val p = Prefs(ctx)
        val k = "${p.url}|${p.user}|${p.password}|${p.certPin}"
        val c = cached
        if (c != null && k == key) return c
        val n = WebDavClient(p.url, p.user, p.password, p.certPin)
        cached = n
        key = k
        return n
    }
}
