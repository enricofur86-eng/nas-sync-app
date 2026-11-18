package it.furlan.nassync

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Browse(val path: String) : Screen
    data class Viewer(val dir: String, val start: String) : Screen
    data class Settings(val firstRun: Boolean) : Screen
    data object Picker : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val stack = mutableStateListOf<Screen>(if (prefs.configured) Screen.Home else Screen.Settings(true))

    var folderName by mutableStateOf(prefs.folderName)
        private set
    /** Tema scelto: "dark", "light" o "system". */
    var themeMode by mutableStateOf(prefs.themeMode)
        private set

    fun setTheme(mode: String) {
        themeMode = mode
        prefs.themeMode = mode
    }

    /** Cartella di cui si sta scegliendo il colore. */
    var colorTarget by mutableStateOf<String?>(null)
        private set

    fun askColor(path: String) {
        menuTarget = null
        colorTarget = path
    }

    fun closeColor() {
        colorTarget = null
    }

    fun setFolderColor(path: String, color: androidx.compose.ui.graphics.Color?) {
        val key = path.ifEmpty { ROOT_KEY }
        if (color == null || color == FOLDER_YELLOW) FolderColors.map.remove(key)
        else FolderColors.map[key] = colorToLong(color)
        prefs.folderColors = FolderColors.map.toMap()
        colorTarget = null
    }

    /** Cartelle mostrate a griglia (le altre a lista). */
    var gridFolders by mutableStateOf(prefs.gridFolders)
        private set

    fun isGrid(path: String) = (path.ifEmpty { ROOT_KEY }) in gridFolders

    /** Posizione di scorrimento di ogni cartella, per ritrovarla tornando indietro. */
    private val scrollPositions = HashMap<String, Pair<Int, Int>>()

    fun savedScroll(key: String): Pair<Int, Int> = scrollPositions[key] ?: (0 to 0)

    fun saveScroll(key: String, index: Int, offset: Int) {
        scrollPositions[key] = index to offset
    }

    /** Cartelle scelte da copiare (vuoto = tutto il NAS). */
    var selectedFolders by mutableStateOf(prefs.selectedFolders)
        private set

    // --- Scelta delle cartelle (naviga il NAS dal vivo) ---
    val pickerSelection = mutableStateListOf<String>()
    var pickerPath by mutableStateOf("")
        private set
    var pickerItems by mutableStateOf<List<RemoteEntry>>(emptyList())
        private set
    var pickerLoading by mutableStateOf(false)
        private set
    var pickerError by mutableStateOf<String?>(null)
        private set

    // --- Modalita': solo WebDAV (di base) oppure backup sul telefono ---

    var backupEnabled by mutableStateOf(prefs.backupEnabled)
        private set

    /** true mentre l'utente sta scegliendo le cartelle per accendere il backup. */
    private var enablingBackup = false

    fun setBackup(on: Boolean) {
        val app = getApplication<Application>()
        if (on) {
            // Il backup si accende davvero solo quando l'utente salva la scelta delle cartelle
            enablingBackup = true
            openPicker()
        } else {
            enablingBackup = false
            prefs.backupEnabled = false
            backupEnabled = false
            SyncScheduler.cancelAll(app)
        }
    }

    /** Elimina dal telefono tutta la copia (backup e file aperti). Sul NAS non cambia niente. */
    fun clearLocalCopy() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Storage.mirrorRoot(app).deleteRecursively()
                Storage.indexFile(app).delete()
                Storage.stateFile(app).delete()
                File(app.cacheDir, "webdav").deleteRecursively()
            }
            Repo.set(emptyList())
            Repo.bumpLocal()
            Toast.makeText(app, "Copia eliminata dal telefono", Toast.LENGTH_LONG).show()
        }
    }

    /** Contenuto delle cartelle letto dal vivo dal NAS (modalita' WebDAV). */
    val live = mutableStateMapOf<String, List<RemoteEntry>>()
    var liveLoading by mutableStateOf<String?>(null)
        private set
    var liveError by mutableStateOf<Pair<String, String>?>(null)
        private set

    private fun sortEntries(list: List<RemoteEntry>) = list
        .filter { !SyncEngine.isJunk(it.name) }
        .sortedWith(compareBy<RemoteEntry>({ !it.isDir }, { it.name.lowercase() }))

    /** Cosa c'e' in una cartella: dall'indice del backup, oppure letto dal vivo. */
    fun children(path: String): List<RemoteEntry> =
        if (backupEnabled) Repo.children(path) else live[path] ?: emptyList()

    fun loadLive(path: String) {
        if (backupEnabled) return
        val app = getApplication<Application>()
        liveLoading = path
        liveError = null
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Net.client(app).list(path) }
                live[path] = sortEntries(list)
            } catch (e: Exception) {
                liveError = path to (e.message ?: e.javaClass.simpleName)
            } finally {
                if (liveLoading == path) liveLoading = null
            }
        }
    }

    /** Dopo caricamenti/cartelle nuove: aggiorno l'indice o la lista dal vivo. */
    private fun refreshFolder(folder: String, fresh: List<RemoteEntry>) {
        if (backupEnabled) Repo.replaceChildren(getApplication(), folder, fresh)
        else live[folder] = sortEntries(fresh)
    }

    fun canUpload(path: String): Boolean =
        !backupEnabled || SyncEngine.inSelection(path, SyncEngine.normalizeSelection(selectedFolders))

    fun openPicker() {
        pickerSelection.clear()
        pickerSelection.addAll(SyncEngine.normalizeSelection(prefs.selectedFolders))
        push(Screen.Picker)
        loadPicker("")
    }

    fun loadPicker(path: String) {
        val app = getApplication<Application>()
        pickerPath = path
        pickerLoading = true
        pickerError = null
        pickerItems = emptyList()
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Net.client(app).list(path) }
                    .filter { it.isDir && !SyncEngine.isJunk(it.name) }
                    .sortedBy { it.name.lowercase() }
                if (pickerPath == path) pickerItems = list
            } catch (e: Exception) {
                if (pickerPath == path) pickerError = e.message ?: e.javaClass.simpleName
            } finally {
                if (pickerPath == path) pickerLoading = false
            }
        }
    }

    fun pickerUp() = loadPicker(parentOf(pickerPath))

    fun toggleSelect(path: String) {
        if (path in pickerSelection) {
            pickerSelection.remove(path)
        } else {
            pickerSelection.removeAll { it.startsWith("$path/") }
            pickerSelection.add(path)
        }
    }

    fun clearSelection() = pickerSelection.clear()

    fun saveSelection() {
        val app = getApplication<Application>()
        val sel = SyncEngine.normalizeSelection(pickerSelection).toSet()
        prefs.selectedFolders = sel
        selectedFolders = sel
        if (enablingBackup) {
            enablingBackup = false
            prefs.backupEnabled = true
            backupEnabled = true
            SyncScheduler.schedulePeriodic(app)
        }
        goHome()
        SyncScheduler.restart(app)
    }

    /** Download "al volo" quando apri un file non ancora copiato. */
    var downloadingName by mutableStateOf<String?>(null)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set

    init {
        FolderColors.load(prefs)
        viewModelScope.launch(Dispatchers.IO) { Repo.ensureLoaded(app) }
    }

    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        selectedItems.clear()
        stack.add(s)
    }

    fun pop() {
        selectedItems.clear()
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun goHome() {
        selectedItems.clear()
        stack.clear()
        stack.add(Screen.Home)
    }

    fun rename(name: String) {
        val n = name.trim().ifEmpty { "NAS" }
        prefs.folderName = n
        folderName = n
    }

    /** Cambia lista/griglia solo per questa cartella. */
    fun toggleGrid(path: String) {
        val key = path.ifEmpty { ROOT_KEY }
        gridFolders = if (key in gridFolders) gridFolders - key else gridFolders + key
        prefs.gridFolders = gridFolders
    }

    fun syncNow() {
        SyncScheduler.syncNow(getApplication())
    }

    fun open(ctx: Context, e: RemoteEntry) {
        if (e.isDir) {
            push(Screen.Browse(e.path))
            return
        }
        // C'e' un'app predefinita per questo tipo di file?
        val def = defaultApps[extOf(e.name)]
        when {
            def == INTERNAL_VIEWER && isImage(e.name) -> push(Screen.Viewer(parentOf(e.path), e.path))
            def != null && def != INTERNAL_VIEWER && FileOpener.isInstalled(ctx, def) -> launchWith(ctx, e, def)
            else -> openWithTarget = e // chiedo con quale app aprirlo
        }
    }

    // --- "Apri con…" e app predefinite ---

    /** File per cui si sta mostrando l'elenco delle app. */
    var openWithTarget by mutableStateOf<RemoteEntry?>(null)
        private set

    var defaultApps by mutableStateOf(prefs.defaultApps)
        private set

    fun askOpenWith(e: RemoteEntry) {
        menuTarget = null
        openWithTarget = e
    }

    fun closeOpenWith() {
        openWithTarget = null
    }

    /** L'utente ha scelto l'app (o il visualizzatore interno); se richiesto la ricordo. */
    fun chooseApp(ctx: Context, e: RemoteEntry, key: String, remember: Boolean) {
        openWithTarget = null
        val ext = extOf(e.name)
        if (remember && ext.isNotEmpty()) {
            defaultApps = defaultApps + (ext to key)
            prefs.defaultApps = defaultApps
        }
        if (key == INTERNAL_VIEWER) push(Screen.Viewer(parentOf(e.path), e.path))
        else launchWith(ctx, e, key)
    }

    private fun launchWith(ctx: Context, e: RemoteEntry, key: String) {
        ensureLocal(e) { file ->
            if (!FileOpener.openWith(ctx, file, key)) {
                Toast.makeText(ctx, "Non riesco ad aprire il file con questa app", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun forgetDefault(ext: String) {
        defaultApps = defaultApps - ext
        prefs.defaultApps = defaultApps
    }

    /** Elemento su cui e' aperto il menu della pressione lunga. */
    var menuTarget by mutableStateOf<RemoteEntry?>(null)
        private set

    /** Elemento in attesa di conferma per l'eliminazione. */
    var deleteTarget by mutableStateOf<RemoteEntry?>(null)
        private set

    /** Titolo della finestra di attesa (caricamento, eliminazione…). */
    var busyTitle by mutableStateOf("Caricamento sul NAS…")
        private set

    fun longPress(@Suppress("UNUSED_PARAMETER") ctx: Context, e: RemoteEntry) {
        menuTarget = e
    }

    fun closeMenu() {
        menuTarget = null
    }

    /** Si puo' eliminare solo dentro le cartelle copiate, e mai una cartella scelta "intera". */
    fun canDelete(e: RemoteEntry): Boolean {
        if (!backupEnabled) {
            // WebDAV: tutto tranne le cartelle principali del NAS
            return e.path.isNotEmpty() && (!e.isDir || e.path.contains('/'))
        }
        val sel = SyncEngine.normalizeSelection(selectedFolders)
        return e.path.isNotEmpty() && SyncEngine.inSelection(e.path, sel) && e.path !in sel
    }

    fun askDelete(e: RemoteEntry) {
        menuTarget = null
        if (canDelete(e)) deleteTarget = e
    }

    fun cancelDelete() {
        deleteTarget = null
    }

    fun confirmDelete() {
        val e = deleteTarget ?: return
        deleteTarget = null
        if (uploadStatus != null) return
        val app = getApplication<Application>()
        busyTitle = "Eliminazione…"
        uploadStatus = e.name
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    Net.client(app).delete(e.path, e.isDir)
                    listOf(Storage.local(app, e.path), Storage.cached(app, e.path)).forEach { local ->
                        if (e.isDir) local.deleteRecursively() else local.delete()
                    }
                    Repo.removePath(app, e.path)
                    val parent = parentOf(e.path)
                    live[parent]?.let { l -> live[parent] = l.filter { it.path != e.path } }
                    null
                } catch (ex: Exception) {
                    ex.message ?: ex.javaClass.simpleName
                }
            }
            uploadStatus = null
            busyTitle = "Caricamento sul NAS…"
            Repo.bumpLocal()
            Toast.makeText(
                app,
                if (err == null) "Eliminato: ${e.name}" else "Non riesco a eliminare: $err",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Apre direttamente una cartella (da un collegamento sulla Home). */
    fun openFolder(path: String) {
        goHome()
        if (path.isNotEmpty()) push(Screen.Browse(path))
    }

    /** Chiede ad Android di mettere sulla Home un'icona che apre questa cartella. */
    /** Cartella per cui si sta preparando il collegamento (finestra di personalizzazione). */
    var shortcutTarget by mutableStateOf<String?>(null)
        private set

    /** Apre la finestra per scegliere nome, colore e simbolo del collegamento. */
    fun pinFolder(ctx: Context, path: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            Toast.makeText(ctx, "Il telefono non permette di aggiungere collegamenti alla Home", Toast.LENGTH_LONG).show()
            return
        }
        shortcutTarget = path
    }

    fun closeShortcut() {
        shortcutTarget = null
    }

    /** Crea il collegamento con il nome e l'icona scelti (il nome vale solo sulla Home). */
    fun createShortcut(ctx: Context, path: String, label: String, bg: androidx.compose.ui.graphics.Color, symbolRes: Int) {
        shortcutTarget = null
        val name = label.trim().ifEmpty { path.substringAfterLast('/').ifEmpty { folderName } }
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_FOLDER)
            .putExtra(MainActivity.EXTRA_PATH, path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // id diverso ogni volta: si possono avere piu' collegamenti alla stessa cartella
        val info = ShortcutInfoCompat.Builder(ctx, "folder:$path:${System.currentTimeMillis()}")
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(shortcutBitmap(ctx, bg, symbolRes)))
            .setIntent(intent)
            .build()
        val ok = ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
        if (!ok) Toast.makeText(ctx, "Non riesco ad aggiungere il collegamento", Toast.LENGTH_LONG).show()
    }

    fun share(ctx: Context, e: RemoteEntry) {
        if (e.isDir) return
        ensureLocal(e) { FileOpener.share(ctx, it) }
    }

    fun ensureLocal(e: RemoteEntry, onReady: (File) -> Unit) {
        val app = getApplication<Application>()
        val existing = Storage.find(app, e)
        if (existing != null) {
            onReady(existing)
            return
        }
        // Backup: nella copia; WebDAV: nella memoria temporanea
        val f = if (backupEnabled) Storage.local(app, e.path) else Storage.cached(app, e.path)
        if (downloadingName != null) return
        downloadingName = e.name
        downloadProgress = 0f
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    var got = 0L
                    Net.client(app).download(e.path, f, ".nsOpen", { false }) { n ->
                        got += n
                        if (e.size > 0) downloadProgress = (got.toFloat() / e.size).coerceIn(0f, 1f)
                    }
                    if (e.mtime > 0) f.setLastModified(e.mtime)
                }
                Repo.bumpLocal()
                onReady(f)
            } catch (ex: Exception) {
                Toast.makeText(app, "Non riesco a scaricare il file: ${ex.message}", Toast.LENGTH_LONG).show()
            } finally {
                downloadingName = null
            }
        }
    }

    // --- Caricamento sul NAS (pulsante + e "Condividi -> NAS Sync") ---

    /** File arrivati da "Condividi", in attesa che l'utente scelga la cartella. */
    var pendingShare by mutableStateOf<List<Uri>>(emptyList())
        private set

    /** Testo mostrato mentre carica, null = nessun caricamento in corso. */
    var uploadStatus by mutableStateOf<String?>(null)
        private set

    fun setShared(uris: List<Uri>) {
        pendingShare = uris
        goHome()
    }

    fun cancelShare() {
        pendingShare = emptyList()
    }

    fun uploadShared(folder: String) {
        val uris = pendingShare
        pendingShare = emptyList()
        upload(folder, uris)
    }

    // --- Selezione di piu' file ---

    /** Percorsi selezionati nella cartella aperta. */
    val selectedItems = mutableStateListOf<String>()

    fun toggleItem(e: RemoteEntry) {
        if (e.path in selectedItems) selectedItems.remove(e.path) else selectedItems.add(e.path)
    }

    fun clearItems() = selectedItems.clear()

    fun selectAll(entries: List<RemoteEntry>) {
        selectedItems.clear()
        selectedItems.addAll(entries.map { it.path })
    }

    /** Scarica (se serve) il file sul telefono e lo restituisce. Da chiamare in background. */
    private fun fetchLocal(app: Application, e: RemoteEntry): File {
        Storage.find(app, e)?.let { return it }
        val f = if (backupEnabled) Storage.local(app, e.path) else Storage.cached(app, e.path)
        Net.client(app).download(e.path, f, ".nsOpen", { false }) { }
        if (e.mtime > 0) f.setLastModified(e.mtime)
        return f
    }

    /** Condivide piu' file insieme (le cartelle vengono ignorate). */
    fun shareMany(ctx: Context, entries: List<RemoteEntry>) {
        val files = entries.filter { !it.isDir }
        if (files.isEmpty() || uploadStatus != null) return
        if (files.size == 1) {
            share(ctx, files[0])
            return
        }
        val app = getApplication<Application>()
        busyTitle = "Preparo i file…"
        uploadStatus = "0 di ${files.size}"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    files.mapIndexed { i, e ->
                        uploadStatus = "${i + 1} di ${files.size}: ${e.name}"
                        fetchLocal(app, e)
                    }
                } catch (ex: Exception) {
                    null
                }
            }
            uploadStatus = null
            busyTitle = "Caricamento sul NAS…"
            Repo.bumpLocal()
            if (result == null) {
                Toast.makeText(app, "Non riesco a scaricare tutti i file", Toast.LENGTH_LONG).show()
            } else {
                clearItems()
                FileOpener.shareMany(ctx, result)
            }
        }
    }

    /** Elementi in attesa di conferma per l'eliminazione multipla. */
    var deleteBatch by mutableStateOf<List<RemoteEntry>?>(null)
        private set

    fun askDeleteMany(entries: List<RemoteEntry>) {
        val ok = entries.filter { canDelete(it) }
        if (ok.isNotEmpty()) deleteBatch = ok
    }

    fun cancelDeleteMany() {
        deleteBatch = null
    }

    fun confirmDeleteMany() {
        val list = deleteBatch ?: return
        deleteBatch = null
        if (uploadStatus != null) return
        val app = getApplication<Application>()
        busyTitle = "Eliminazione…"
        uploadStatus = "0 di ${list.size}"
        viewModelScope.launch {
            var done = 0
            var lastErr: String? = null
            withContext(Dispatchers.IO) {
                val dav = Net.client(app)
                list.forEachIndexed { i, e ->
                    uploadStatus = "${i + 1} di ${list.size}: ${e.name}"
                    try {
                        dav.delete(e.path, e.isDir)
                        listOf(Storage.local(app, e.path), Storage.cached(app, e.path)).forEach { f ->
                            if (e.isDir) f.deleteRecursively() else f.delete()
                        }
                        Repo.removePath(app, e.path)
                        val parent = parentOf(e.path)
                        live[parent]?.let { l -> live[parent] = l.filter { it.path != e.path } }
                        done++
                    } catch (ex: Exception) {
                        lastErr = ex.message ?: ex.javaClass.simpleName
                    }
                }
            }
            uploadStatus = null
            busyTitle = "Caricamento sul NAS…"
            clearItems()
            Repo.bumpLocal()
            Toast.makeText(
                app,
                if (lastErr == null) "Eliminati $done elementi" else "Eliminati $done su ${list.size}. Errore: $lastErr",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // --- File con lo stesso nome durante il caricamento ---

    enum class ConflictChoice { OVERWRITE, KEEP_BOTH, SKIP }

    data class ConflictPrompt(
        val name: String,
        val existing: RemoteEntry,
        val newSize: Long?,
        val remaining: Int,
    )

    /** Domanda in corso: "il file esiste gia', cosa faccio?" (null = nessuna). */
    var conflictPrompt by mutableStateOf<ConflictPrompt?>(null)
        private set
    private var conflictAnswer: kotlinx.coroutines.CompletableDeferred<Pair<ConflictChoice, Boolean>>? = null

    fun answerConflict(choice: ConflictChoice, applyToAll: Boolean) {
        conflictPrompt = null
        conflictAnswer?.complete(choice to applyToAll)
        conflictAnswer = null
    }

    private fun sizeOf(ctx: Context, uri: Uri): Long? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun upload(folder: String, uris: List<Uri>) {
        if (uris.isEmpty() || uploadStatus != null) return
        val app = getApplication<Application>()
        busyTitle = "Caricamento sul NAS…"
        uploadStatus = "Preparazione…"
        viewModelScope.launch {
            var ok = 0
            var skipped = 0
            var lastError: String? = null
            withContext(Dispatchers.IO) {
                val dav = Net.client(app)
                // Leggo dal NAS cosa c'e' gia' nella cartella (se non riesco, uso quello che so)
                val present = try {
                    dav.list(folder)
                } catch (_: Exception) {
                    children(folder)
                }
                val existingFiles = present.filter { !it.isDir }.associateBy { it.name.lowercase() }.toMutableMap()
                val taken = present.map { it.name.lowercase() }.toMutableSet()
                var choiceForAll: ConflictChoice? = null
                uris.forEachIndexed { i, uri ->
                    var dest: java.io.File? = null
                    try {
                        val original = displayName(app, uri)
                        var name = original
                        if (original.lowercase() in taken) {
                            val ex = existingFiles[original.lowercase()]
                            val choice = when {
                                ex == null -> ConflictChoice.KEEP_BOTH // c'e' una cartella con quel nome
                                choiceForAll != null -> choiceForAll!!
                                else -> {
                                    // Mi fermo e chiedo all'utente
                                    val answer = kotlinx.coroutines.CompletableDeferred<Pair<ConflictChoice, Boolean>>()
                                    withContext(Dispatchers.Main) {
                                        conflictAnswer = answer
                                        conflictPrompt = ConflictPrompt(original, ex!!, sizeOf(app, uri), uris.size - i - 1)
                                    }
                                    val (c, all) = answer.await()
                                    if (all) choiceForAll = c
                                    c
                                }
                            }
                            when (choice) {
                                ConflictChoice.SKIP -> {
                                    skipped++
                                    return@forEachIndexed
                                }
                                ConflictChoice.KEEP_BOTH -> name = uniqueName(original, taken)
                                ConflictChoice.OVERWRITE -> name = original
                            }
                        }
                        taken.add(name.lowercase())
                        uploadStatus = "${i + 1} di ${uris.size}: $name"
                        val path = if (folder.isEmpty()) name else "$folder/$name"
                        SyncEngine.recentUploads.add(path)
                        val f = if (backupEnabled) Storage.local(app, path) else Storage.cached(app, path)
                        dest = f
                        f.parentFile?.mkdirs()
                        val input = app.contentResolver.openInputStream(uri)
                            ?: throw java.io.IOException("Impossibile leggere il file")
                        input.use { inp -> f.outputStream().use { out -> inp.copyTo(out, 256 * 1024) } }
                        dav.put(path, f)
                        ok++
                    } catch (e: Exception) {
                        dest?.delete()
                        lastError = e.message ?: e.javaClass.simpleName
                    }
                }
                try {
                    refreshFolder(folder, dav.list(folder))
                } catch (_: Exception) {
                }
            }
            Repo.bumpLocal()
            uploadStatus = null
            val skippedText = if (skipped > 0) ", $skipped saltati" else ""
            val msg = if (lastError == null) "Caricati $ok file sul NAS$skippedText"
            else "Caricati $ok file su ${uris.size}$skippedText. Errore: $lastError"
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun createFolder(parent: String, rawName: String) {
        val app = getApplication<Application>()
        val name = rawName.trim().replace('/', '_')
        if (name.isEmpty() || uploadStatus != null) return
        busyTitle = "Nuova cartella…"
        uploadStatus = "Creo la cartella $name…"
        viewModelScope.launch {
            var createdPath: String? = null
            val err = withContext(Dispatchers.IO) {
                try {
                    val dav = Net.client(app)
                    val path = if (parent.isEmpty()) name else "$parent/$name"
                    dav.mkcol(path)
                    SyncEngine.recentUploads.add(path)
                    if (backupEnabled) Storage.local(app, path).mkdirs()
                    refreshFolder(parent, dav.list(parent))
                    createdPath = path
                    null
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            uploadStatus = null
            busyTitle = "Caricamento sul NAS…"
            if (err != null) Toast.makeText(app, "Non riesco a creare la cartella: $err", Toast.LENGTH_LONG).show()
            // Se stavo caricando file condivisi, entro subito nella cartella nuova
            val created = createdPath
            if (err == null && created != null && pendingShare.isNotEmpty()) push(Screen.Browse(created))
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        val fromProvider = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        val raw = fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
        return raw.replace('/', '_').trim().ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    /** Se esiste gia' un file con lo stesso nome, aggiungo (1), (2)… cosi' non sovrascrivo niente. */
    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name.lowercase() !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while ("$base ($n)$ext".lowercase() in taken) n++
        return "$base ($n)$ext"
    }

    /** Salva le impostazioni dopo che la connessione e' stata provata. */
    /** Certificato del NAS da far confermare all'utente (null = nessuna domanda in corso). */
    var certPrompt by mutableStateOf<CertInfo?>(null)
        private set

    fun dismissCert() {
        certPrompt = null
    }

    /** Aggiornamento automatico acceso/spento (per mostrare il pulsante nella schermata iniziale). */
    var autoSync by mutableStateOf(prefs.autoSync)
        private set

    /** Interruttore dell'aggiornamento automatico: vale subito. */
    fun setAuto(on: Boolean) {
        val app = getApplication<Application>()
        prefs.autoSync = on
        autoSync = on
        if (on) SyncScheduler.schedulePeriodic(app) else SyncScheduler.cancelAuto(app)
    }

    var syncInterval by mutableStateOf(prefs.syncIntervalHours)
        private set

    fun setInterval(hours: Int) {
        prefs.syncIntervalHours = hours
        syncInterval = hours
        SyncScheduler.schedulePeriodic(getApplication())
    }

    fun saveSettings(
        url: String, user: String, pass: String, pin: String,
        mobile: Boolean,
    ) {
        val app = getApplication<Application>()
        prefs.url = url.trim()
        prefs.user = user.trim()
        prefs.password = pass
        prefs.certPin = pin
        prefs.useMobileData = mobile
        val first = stack.lastOrNull() == Screen.Settings(true)
        SyncScheduler.schedulePeriodic(app)
        goHome()
        if (first) {
            // Prima configurazione: modalita' solo WebDAV, non si copia niente
        } else if (prefs.autoSync && prefs.backupEnabled) {
            SyncScheduler.restart(app)
        }
    }

    suspend fun testConnection(url: String, user: String, pass: String, pin: String): String =
        withContext(Dispatchers.IO) {
            try {
                val n = WebDavClient(url, user.trim(), pass, pin).list("")
                "OK! Collegato: nella cartella principale ci sono ${n.size} elementi."
            } catch (e: javax.net.ssl.SSLException) {
                // Certificato non verificato (tipico dei NAS di casa): lo leggo e chiedo all'utente
                val info = try {
                    fetchServerCert(WebDavClient.normalize(url))
                } catch (_: Exception) {
                    null
                }
                if (info != null && !info.sha256.equals(pin, ignoreCase = true)) {
                    certPrompt = info
                    "Il NAS usa un certificato non verificato: controlla i dati e conferma."
                } else {
                    "Errore di certificato HTTPS: ${e.message ?: e.javaClass.simpleName}"
                }
            } catch (e: Exception) {
                "Errore: ${e.message ?: e.javaClass.simpleName}"
            }
        }
}
