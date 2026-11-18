package it.furlan.nassync

import android.annotation.SuppressLint
import okhttp3.HttpUrl
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Dati di un certificato da mostrare all'utente prima di fidarsi. */
data class CertInfo(
    val sha256: String,
    val subject: String,
    val issuer: String,
    val validUntil: String,
)

fun sha256Fingerprint(cert: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString(":") { "%02X".format(it) }

private fun systemTrustManager(): X509TrustManager {
    val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    f.init(null as KeyStore?)
    return f.trustManagers.filterIsInstance<X509TrustManager>().first()
}

/**
 * Accetta i certificati normali (verificati da Android) e, in piu', SOLO il certificato
 * del NAS che l'utente ha confermato (riconosciuto dalla sua impronta SHA-256).
 * Qualsiasi altro certificato viene rifiutato.
 */
class PinningTrustManager(private val pin: String) : X509TrustManager {
    private val system = systemTrustManager()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        system.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("Il NAS non ha inviato un certificato")
        try {
            system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            if (!sha256Fingerprint(chain[0]).equals(pin, ignoreCase = true)) {
                throw CertificateException("Il certificato del NAS non è quello confermato", e)
            }
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
}

fun pinnedSslContext(tm: X509TrustManager): SSLContext =
    SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }

/** Nome host valido per il certificato, oppure certificato confermato dall'utente. */
fun pinnedHostnameVerifier(pin: String): HostnameVerifier {
    val default = HttpsURLConnection.getDefaultHostnameVerifier()
    return HostnameVerifier { host, session ->
        if (default.verify(host, session)) return@HostnameVerifier true
        val leaf = try {
            session.peerCertificates.firstOrNull() as? X509Certificate
        } catch (_: Exception) {
            null
        }
        leaf != null && sha256Fingerprint(leaf).equals(pin, ignoreCase = true)
    }
}

/**
 * Legge il certificato del NAS SENZA fidarsi: la connessione viene interrotta subito
 * dopo averlo letto, quindi non viene inviato niente (ne' utente ne' password).
 */
fun fetchServerCert(url: HttpUrl): CertInfo? {
    var captured: X509Certificate? = null

    @SuppressLint("CustomX509TrustManager")
    val recorder = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Non usato")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            captured = chain?.firstOrNull()
            throw CertificateException("Solo lettura del certificato")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val ctx = pinnedSslContext(recorder)
    try {
        (ctx.socketFactory.createSocket(url.host, url.port) as SSLSocket).use { s ->
            s.soTimeout = 15_000
            s.startHandshake()
        }
    } catch (_: Exception) {
        // atteso: il recorder rifiuta sempre il certificato
    }
    val c = captured ?: return null
    return CertInfo(
        sha256 = sha256Fingerprint(c),
        subject = c.subjectX500Principal.name,
        issuer = c.issuerX500Principal.name,
        validUntil = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(c.notAfter),
    )
}
