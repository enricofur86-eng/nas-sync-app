package it.furlan.nassync

import android.content.Context
import java.io.File

/** Chiave usata per la cartella principale "NAS" (percorso vuoto). */
const val ROOT_KEY = "/"

/** Impostazioni salvate sul telefono (solo per questa app). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v).apply()

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) = sp.edit().putString("user", v).apply()

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(v) = sp.edit().putString("password", v).apply()

    var folderName: String
        get() = sp.getString("folderName", "NAS") ?: "NAS"
        set(v) = sp.edit().putString("folderName", v).apply()

    /** Impronta SHA-256 del certificato del NAS confermato dall'utente ("" = nessuno). */
    var certPin: String
        get() = sp.getString("certPin", "") ?: ""
        set(v) = sp.edit().putString("certPin", v).apply()

    var useMobileData: Boolean
        get() = sp.getBoolean("useMobileData", false)
        set(v) = sp.edit().putBoolean("useMobileData", v).apply()

    /** false (di base) = solo WebDAV, niente copia sul telefono; true = backup delle cartelle scelte. */
    var backupEnabled: Boolean
        get() = sp.getBoolean("backupEnabled", false)
        set(v) = sp.edit().putBoolean("backupEnabled", v).apply()

    /** true = aggiorna all'apertura e ogni 6 ore; false = solo quando lo chiede l'utente. */
    var autoSync: Boolean
        get() = sp.getBoolean("autoSync", true)
        set(v) = sp.edit().putBoolean("autoSync", v).apply()

    /** Ogni quante ore fare il controllo automatico (1, 2, 6, 12, 24, 168). */
    var syncIntervalHours: Int
        get() = sp.getInt("syncIntervalHours", 6)
        set(v) = sp.edit().putInt("syncIntervalHours", v).apply()

    var deleteRemoved: Boolean
        get() = sp.getBoolean("deleteRemoved", true)
        set(v) = sp.edit().putBoolean("deleteRemoved", v).apply()

    /** Cartelle che l'utente vuole vedere a griglia (tutte le altre a lista). */
    var gridFolders: Set<String>
        get() = (sp.getString("gridFolders", "") ?: "").split('\n').filter { it.isNotEmpty() }.toSet()
            .let { if (sp.getBoolean("gridRoot", false)) it + ROOT_KEY else it }
        set(v) = sp.edit()
            .putString("gridFolders", v.filter { it != ROOT_KEY }.joinToString("\n"))
            .putBoolean("gridRoot", ROOT_KEY in v)
            .apply()

    /** Cartelle del NAS da copiare (vuoto = tutto). */
    var selectedFolders: Set<String>
        get() = (sp.getString("selectedFolders", "") ?: "").split('\n').filter { it.isNotBlank() }.toSet()
        set(v) = sp.edit().putString("selectedFolders", v.joinToString("\n")).apply()

    /** Tema: "dark" (nero), "light" (chiaro) o "system" (come il telefono). */
    var themeMode: String
        get() = sp.getString("themeMode", "dark") ?: "dark"
        set(v) = sp.edit().putString("themeMode", v).apply()

    /** Colori personalizzati delle cartelle: percorso -> colore ARGB. */
    var folderColors: Map<String, Long>
        get() = (sp.getString("folderColors", "") ?: "").split('\n').mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(i + 1).toLongOrNull()?.let { line.substring(0, i) to it }
        }.toMap()
        set(v) = sp.edit().putString("folderColors", v.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()

    /** Pulizia una tantum degli aggiornamenti rimasti in sospeso dalle versioni precedenti. */
    var cleanedOldWork: Boolean
        get() = sp.getBoolean("cleanedOldWork19", false)
        set(v) = sp.edit().putBoolean("cleanedOldWork19", v).apply()

    /** App predefinite per estensione: "pdf" -> "pacchetto/attivita'" oppure "internal". */
    var defaultApps: Map<String, String>
        get() = (sp.getString("defaultApps", "") ?: "").split('\n').mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()
        set(v) = sp.edit().putString("defaultApps", v.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()

    var lastSync: Long
        get() = sp.getLong("lastSync", 0L)
        set(v) = sp.edit().putLong("lastSync", v).apply()

    val configured: Boolean get() = url.isNotBlank()
}

/** Dove stanno i file sul telefono. */
object Storage {
    fun mirrorRoot(ctx: Context): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "NAS").also { it.mkdirs() }
    }

    fun local(ctx: Context, path: String): File = File(mirrorRoot(ctx), path)

    /** File aperti in modalita' WebDAV: memoria temporanea che Android svuota quando serve spazio. */
    fun cached(ctx: Context, path: String): File = File(File(ctx.cacheDir, "webdav"), path)

    /** Il file e' gia' sul telefono (copia di backup o aperto di recente)? Ritorna il file o null. */
    fun find(ctx: Context, e: RemoteEntry): File? {
        if (e.isDir) return null
        val m = local(ctx, e.path)
        if (m.isFile && m.length() == e.size) return m
        val c = cached(ctx, e.path)
        if (c.isFile && c.length() == e.size) return c
        return null
    }

    fun isLocal(ctx: Context, e: RemoteEntry): Boolean = e.isDir || find(ctx, e) != null

    fun indexFile(ctx: Context) = File(ctx.filesDir, "index.tsv")
    fun stateFile(ctx: Context) = File(ctx.filesDir, "state.tsv")
}
