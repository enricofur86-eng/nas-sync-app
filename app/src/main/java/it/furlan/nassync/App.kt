package it.furlan.nassync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sincronizzazione", NotificationManager.IMPORTANCE_LOW)
        )
        SyncScheduler.cleanOldWork(this)
        // Riallinea i controlli automatici alle impostazioni (spenti se manuale)
        SyncScheduler.schedulePeriodic(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    companion object {
        const val CHANNEL = "sync"
    }
}

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
private val VIDEO_EXT = setOf("mp4", "mov", "mkv", "avi", "3gp", "webm", "m4v")

fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()
fun isImage(name: String) = extOf(name) in IMAGE_EXT
fun isVideo(name: String) = extOf(name) in VIDEO_EXT

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format(Locale.ITALY, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ITALY, "%.1f MB", mb)
    return String.format(Locale.ITALY, "%.2f GB", mb / 1024.0)
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(ms))

/** Apre un file con l'app giusta del telefono (PDF, video, documenti…). */
/** Valore speciale: il visualizzatore di immagini dentro NAS Sync. */
const val INTERNAL_VIEWER = "internal"

/** Un'app del telefono che puo' aprire un file. */
data class AppChoice(
    val label: String,
    val pkg: String,
    val cls: String,
    val icon: android.graphics.drawable.Drawable?,
) {
    val key: String get() = "$pkg/$cls"
}

fun mimeOf(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(name)) ?: "*/*"

object FileOpener {
    /** Le app installate che sanno aprire questo tipo di file. */
    fun appsFor(ctx: Context, name: String): List<AppChoice> {
        val pm = ctx.packageManager
        val probe = Intent(Intent.ACTION_VIEW).setDataAndType(
            android.net.Uri.parse("content://${ctx.packageName}.files/probe/file.${extOf(name)}"),
            mimeOf(name),
        )
        val found = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(
                probe,
                android.content.pm.PackageManager.ResolveInfoFlags.of(
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        }
        return found
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppChoice(
                    label = it.loadLabel(pm).toString(),
                    pkg = it.activityInfo.packageName,
                    cls = it.activityInfo.name,
                    icon = try { it.loadIcon(pm) } catch (_: Exception) { null },
                )
            }
            .distinctBy { it.key }
            .sortedBy { it.label.lowercase() }
    }

    /** L'app scelta e' ancora installata? */
    fun isInstalled(ctx: Context, key: String): Boolean {
        val pkg = key.substringBefore('/')
        val cls = key.substringAfter('/')
        return try {
            ctx.packageManager.getActivityInfo(android.content.ComponentName(pkg, cls), 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Nome da mostrare per una scelta salvata. */
    fun labelOf(ctx: Context, key: String): String {
        if (key == INTERNAL_VIEWER) return "Visualizzatore di NAS Sync"
        return try {
            val info = ctx.packageManager.getActivityInfo(
                android.content.ComponentName(key.substringBefore('/'), key.substringAfter('/')), 0,
            )
            info.loadLabel(ctx.packageManager).toString()
        } catch (_: Exception) {
            "App non più installata"
        }
    }

    /** Apre il file con un'app precisa scelta dall'utente. */
    fun openWith(ctx: Context, file: File, key: String): Boolean {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(file.name))
            .setClassName(key.substringBefore('/'), key.substringAfter('/'))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun open(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(file.name)) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Nessuna app per aprire questo file", Toast.LENGTH_SHORT).show()
        }
    }

    /** Condivide piu' file insieme. */
    fun shareMany(ctx: Context, files: List<File>) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
        val mime = when {
            files.all { isImage(it.name) } -> "image/*"
            files.all { isVideo(it.name) } -> "video/*"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(mime)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = android.content.ClipData.newRawUri("", uris[0])
        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        intent.clipData = clip
        ctx.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extOf(file.name)) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
