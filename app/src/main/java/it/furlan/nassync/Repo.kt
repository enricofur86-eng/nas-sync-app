package it.furlan.nassync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Elenco di tutto quello che c'e' sul NAS, salvato sul telefono. */
object IndexStore {
    fun save(file: File, entries: List<RemoteEntry>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            for (e in entries) {
                if (e.path.contains('\n')) continue
                w.write(if (e.isDir) "D" else "F")
                w.write("\t"); w.write(e.size.toString())
                w.write("\t"); w.write(e.mtime.toString())
                w.write("\t"); w.write(e.path)
                w.write("\n")
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun load(file: File): List<RemoteEntry> {
        if (!file.isFile) return emptyList()
        val out = ArrayList<RemoteEntry>()
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val p = line.split('\t', limit = 4)
                if (p.size < 4) continue
                out.add(RemoteEntry(p[3], p[0] == "D", p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L))
            }
        }
        return out
    }
}

/** Ricorda quali file sono gia' stati copiati e con quale data di modifica. */
object LocalState {
    fun load(file: File): HashMap<String, Long> {
        val m = HashMap<String, Long>()
        if (!file.isFile) return m
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                val i = line.indexOf('\t')
                if (i <= 0) continue
                val t = line.substring(0, i).toLongOrNull() ?: continue
                m[line.substring(i + 1)] = t
            }
        }
        return m
    }

    fun save(file: File, state: Map<String, Long>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            for ((k, v) in state) {
                if (k.contains('\n')) continue
                w.write(v.toString()); w.write("\t"); w.write(k); w.write("\n")
            }
        }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }
}

/** Indice in memoria, usato dalle schermate. */
object Repo {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** Cambia quando nuovi file arrivano sul telefono. */
    private val _localTick = MutableStateFlow(0)
    val localTick: StateFlow<Int> = _localTick

    @Volatile private var byParent: Map<String, List<RemoteEntry>> = emptyMap()
    @Volatile private var allEntries: List<RemoteEntry> = emptyList()
    @Volatile var loaded = false
        private set
    @Volatile var fileCount = 0
        private set
    @Volatile var totalBytes = 0L
        private set

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (!loaded) set(IndexStore.load(Storage.indexFile(ctx)))
    }

    fun set(entries: List<RemoteEntry>) {
        allEntries = entries
        byParent = entries.groupBy { parentOf(it.path) }.mapValues { (_, list) ->
            list.sortedWith(compareBy<RemoteEntry>({ !it.isDir }, { it.name.lowercase() }))
        }
        fileCount = entries.count { !it.isDir }
        totalBytes = entries.sumOf { it.size }
        loaded = true
        _version.value = _version.value + 1
    }

    fun children(path: String): List<RemoteEntry> = byParent[path] ?: emptyList()

    /** Dopo un'eliminazione: tolgo l'elemento (e se e' una cartella tutto il suo contenuto). */
    @Synchronized
    fun removePath(ctx: Context, path: String) {
        val next = allEntries.filter { it.path != path && !it.path.startsWith("$path/") }
        set(next)
        IndexStore.save(Storage.indexFile(ctx), next)
    }

    fun countFilesUnder(path: String): Int = allEntries.count { !it.isDir && it.path.startsWith("$path/") }

    /** Dopo un caricamento: rimpiazza il contenuto di una cartella con quello appena letto dal NAS. */
    @Synchronized
    fun replaceChildren(ctx: Context, parent: String, fresh: List<RemoteEntry>) {
        val clean = fresh.filter { !SyncEngine.isJunk(it.name) }
        val next = allEntries.filter { parentOf(it.path) != parent } + clean
        set(next)
        IndexStore.save(Storage.indexFile(ctx), next)
    }

    fun bumpLocal() {
        _localTick.value = _localTick.value + 1
    }
}
