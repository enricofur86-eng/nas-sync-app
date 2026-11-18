package it.furlan.nassync

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** Simboli tra cui scegliere per i collegamenti sulla Home. */
val SHORTCUT_SYMBOLS: List<Pair<String, Int>> = listOf(
    "Cartella" to R.drawable.sym_folder,
    "Lavoro" to R.drawable.sym_work,
    "Foto" to R.drawable.sym_photo,
    "Documenti" to R.drawable.sym_doc,
    "Casa" to R.drawable.sym_home,
    "Stella" to R.drawable.sym_star,
    "Cuore" to R.drawable.sym_heart,
)

/** Simbolo bianco su sfondi scuri, grigio scuro su sfondi chiari (es. giallo). */
fun symbolColorOn(bg: Color): Color = if (bg.luminance() > 0.5f) Color(0xFF212121) else Color.White

/** Disegna l'icona del collegamento: sfondo colorato pieno e simbolo al centro. */
fun shortcutBitmap(ctx: android.content.Context, bg: Color, symbolRes: Int): android.graphics.Bitmap {
    val size = 432 // 108dp a 4x: formato delle icone adattive
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(bg.toArgb())
    val d = androidx.core.content.ContextCompat.getDrawable(ctx, symbolRes)?.mutate()
    if (d != null) {
        d.setTint(symbolColorOn(bg).toArgb())
        val s = (size * 0.42f).toInt()
        val o = (size - s) / 2
        d.setBounds(o, o, o + s, o + s)
        d.draw(canvas)
    }
    return bmp
}

/** Il classico giallo delle cartelle. */
val FOLDER_YELLOW = Color(0xFFF6C344)

/** Colori per lo sfondo dei collegamenti: blu dell'app, nero e i colori delle cartelle. */
val SHORTCUT_COLORS: List<Color>
    get() = listOf(Color(0xFF1565C0), Color(0xFF212121)) + FOLDER_PALETTE.map { it.second }

/** Colori tra cui scegliere per le cartelle. */
val FOLDER_PALETTE: List<Pair<String, Color>> = listOf(
    "Giallo" to FOLDER_YELLOW,
    "Arancione" to Color(0xFFFB8C00),
    "Rosso" to Color(0xFFE53935),
    "Rosa" to Color(0xFFEC407A),
    "Viola" to Color(0xFF8E24AA),
    "Blu" to Color(0xFF1E88E5),
    "Azzurro" to Color(0xFF29B6F6),
    "Verde" to Color(0xFF43A047),
    "Marrone" to Color(0xFF8D6E63),
    "Grigio" to Color(0xFF9E9E9E),
)

/** Colori personalizzati delle cartelle (percorso -> colore). Restano salvati sul telefono. */
object FolderColors {
    val map = mutableStateMapOf<String, Long>()

    fun load(prefs: Prefs) {
        map.clear()
        map.putAll(prefs.folderColors)
    }

    fun colorFor(path: String): Color = map[path.ifEmpty { ROOT_KEY }]?.let { Color(it.toInt()) }
        ?: FOLDER_YELLOW
}

fun colorToLong(c: Color): Long = c.toArgb().toLong()

@Composable
fun FolderIcon(path: String, size: Dp, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.Folder, contentDescription = null,
        tint = FolderColors.colorFor(path),
        modifier = modifier.size(size),
    )
}

/** Colore della fascia e scritta per ogni tipo di file. */
fun fileTypeStyle(name: String): Pair<Color, String> {
    val ext = extOf(name)
    val label = ext.uppercase().take(4).ifEmpty { "FILE" }
    return when (ext) {
        "pdf" -> Color(0xFFE53935) to "PDF"
        "xls", "xlsx", "xlsm", "xlsb", "csv", "ods" -> Color(0xFF1E8E3E) to label
        "doc", "docx", "odt", "rtf" -> Color(0xFF1E63D6) to label
        "ppt", "pptx", "odp" -> Color(0xFFE8710A) to label
        "dwg", "dxf", "dwf" -> Color(0xFFB71C1C) to label
        "p7m", "p7s" -> Color(0xFF3949AB) to label
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFF8D6E63) to label
        "txt", "md", "log", "xml", "json" -> Color(0xFF757575) to label
        "mp3", "wav", "m4a", "flac", "aac", "ogg" -> Color(0xFFD81B60) to label
        "apk" -> Color(0xFF43A047) to "APK"
        "eml", "msg" -> Color(0xFF00838F) to label
        else -> when {
            isImage(name) -> Color(0xFF00897B) to label
            isVideo(name) -> Color(0xFF8E24AA) to label
            else -> Color(0xFF607D8B) to label
        }
    }
}

/** Foglio bianco con l'angolo piegato e una fascia colorata con il tipo di file. */
@Composable
fun FileTypeIcon(name: String, size: Dp, modifier: Modifier = Modifier) {
    val (color, label) = fileTypeStyle(name)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val pageW = w * 0.74f
            val left = (w - pageW) / 2f + w * 0.04f
            val top = h * 0.05f
            val bottom = h * 0.95f
            val fold = pageW * 0.30f
            val page = Path().apply {
                moveTo(left, top)
                lineTo(left + pageW - fold, top)
                lineTo(left + pageW, top + fold)
                lineTo(left + pageW, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(page, Color.White)
            drawPath(page, Color(0x40000000), style = Stroke(width = w * 0.025f))
            val corner = Path().apply {
                moveTo(left + pageW - fold, top)
                lineTo(left + pageW - fold, top + fold)
                lineTo(left + pageW, top + fold)
                close()
            }
            drawPath(corner, Color(0xFFD6D6D6))
            drawRoundRect(
                color = color,
                topLeft = Offset(left - w * 0.10f, h * 0.48f),
                size = Size(pageW * 0.95f, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            )
        }
        // centro della fascia: un po' a sinistra e in basso rispetto al centro
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.21f).sp,
            lineHeight = (size.value * 0.21f).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.offset(x = size * -0.08f, y = size * 0.13f),
        )
    }
}
