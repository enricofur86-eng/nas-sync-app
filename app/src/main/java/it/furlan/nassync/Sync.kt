package it.furlan.nassync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SyncStatus(
    val running: Boolean = false,
    val phase: String = "",
    val foldersScanned: Int = 0,
    val filesTotal: Int = 0,
    val filesDone: Int = 0,
    val bytesTotal: Long = 0L,
    val bytesDone: Long = 0L,
    val current: String = "",
    val error: String? = null,
    val waitingWifi: Boolean = false,
    val pendingFiles: Int = 0,
    val pendingBytes: Long = 0L,
)

object SyncEngine {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status
    private val mutex = Mutex()

    private const val PARALLEL = 3
    private const val NET_RETRIES = 12
    private const val PHASE_SCAN = "Lettura delle cartelle del NAS…"
    private const val PHASE_COPY = "Copia dei file sul telefono…"
    private const val SPACE_RESERVE = 1_000_000_000L // lascio sempre 1 GB libero

    suspend fun run(ctx: Context, allowDownloads: Boolean) {
        // Se c'e' gia' una copia in corso aspetto che finisca (o che venga annullata).
        mutex.lock()
        try {
            withContext(Dispatchers.IO) { doRun(ctx.applicationContext, allowDownloads) }
        } catch (e: CancellationException) {
            _status.update { it.copy(running = false, phase = "In pausa", current = "") }
            throw e
        } catch (e: Exception) {
            _status.update {
                it.copy(running = false, phase = "", current = "", error = e.message ?: e.javaClass.simpleName)
            }
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Se cade la connessione (Wi-Fi staccato, cambio di rete…) aspetto e riprovo
     * la stessa operazione, invece di ricominciare tutto da capo.
     * Gli errori "veri" del NAS (password, permessi…) non si riprovano.
     */
    private suspend fun <T> withNetRetry(phase: String, block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: NasHttpException) {
                throw e
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                attempt++
                if (attempt > NET_RETRIES) throw e
                _status.update { it.copy(phase = "Connessione persa, riprovo tra poco…") }
                delay(minOf(5_000L * attempt, 30_000L))
                _status.update { it.copy(phase = phase) }
            }
        }
    }

    /** L'utente ha cambiato le cartelle da copiare mentre questa copia era in corso? */
    private fun selectionChanged(prefs: Prefs, current: List<String>): Boolean =
        normalizeSelection(prefs.selectedFolders) != current

    private fun stopForNewSelection() {
        // Mi fermo: parte subito una nuova copia con le cartelle aggiornate.
        _status.value = SyncStatus(running = true, phase = "Cartelle cambiate, riparto…")
    }

    /** File e cartelle caricati dal telefono in questa sessione: la pulizia non li tocca. */
    val recentUploads: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun inSelection(path: String, selection: Collection<String>): Boolean =
        selection.isEmpty() || selection.any { path == it || path.startsWith("$it/") }

    fun isJunk(name: String): Boolean =
        name.startsWith("@") || name.startsWith(".@") ||
            name.equals("#recycle", true) || name.equals("\$RECYCLE.BIN", true) ||
            name.startsWith(".Trash") || name == ".DS_Store" || name.equals("Thumbs.db", true) ||
            name.endsWith(".nsPart") || name.endsWith(".nsOpen")

    private suspend fun doRun(ctx: Context, allowDownloads: Boolean) {
        val prefs = Prefs(ctx)
        val dav = Net.client(ctx)
        _status.value = SyncStatus(running = true, phase = PHASE_SCAN)

        // 1) Leggo tutte le cartelle del NAS
        val all = ArrayList<RemoteEntry>()
        val queue = ArrayDeque<String>()
        val seen = HashSet<String>()
        // Cartelle scelte dall'utente (vuoto = tutto il NAS)
        val selection = normalizeSelection(prefs.selectedFolders)
        if (selection.isEmpty()) {
            queue.add("")
            seen.add("")
        } else {
            // Aggiungo anche le cartelle "genitore", per poterci arrivare navigando.
            val ancestors = LinkedHashSet<String>()
            for (sel in selection) {
                var p = sel
                while (p.isNotEmpty()) {
                    ancestors.add(p)
                    p = parentOf(p)
                }
            }
            ancestors.forEach { all.add(RemoteEntry(it, true, 0L, 0L)) }
            selection.forEach { queue.add(it); seen.add(it) }
        }
        var folders = 0
        val failedDirs = ArrayList<String>()
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (selectionChanged(prefs, selection)) return stopForNewSelection()
            val dir = queue.removeFirst()
            val children = try {
                withNetRetry(PHASE_SCAN) { dav.list(dir) }
            } catch (e: NasHttpException) {
                if (dir.isEmpty() || e.code == 401) throw e // la cartella principale deve funzionare
                failedDirs.add(dir)        // una sola cartella che da' errore: la salto
                continue
            }
            folders++
            for (c in children) {
                if (isJunk(c.name)) continue
                if (c.isDir) {
                    if (!seen.add(c.path)) continue
                    queue.add(c.path)
                }
                all.add(c)
            }
            _status.update { it.copy(foldersScanned = folders, current = dir) }
        }

        // Per le cartelle che non si sono lette tengo quello che sapevo gia',
        // cosi' i file gia' copiati non vengono cancellati dal telefono.
        if (failedDirs.isNotEmpty()) {
            val known = all.mapTo(HashSet()) { it.path }
            IndexStore.load(Storage.indexFile(ctx)).forEach { e ->
                if (e.path !in known && failedDirs.any { e.path.startsWith("$it/") }) all.add(e)
            }
        }
        val dirWarning = if (failedDirs.isEmpty()) null
        else "${failedDirs.size} cartelle non leggibili sul NAS (saltate): " +
            failedDirs.take(3).joinToString(", ") { "/$it" } + if (failedDirs.size > 3) "…" else ""

        IndexStore.save(Storage.indexFile(ctx), all)
        Repo.set(all)

        val root = Storage.mirrorRoot(ctx)
        val stateFile = Storage.stateFile(ctx)
        val state = LocalState.load(stateFile)

        // 2) Tolgo dal telefono quello che non c'e' piu' sul NAS
        if (all.isNotEmpty()) {
            _status.update { it.copy(phase = "Pulizia…", current = "") }
            // Il telefono resta sempre una copia esatta del NAS
            cleanLocal(root, all, state, true, selection)
        }

        // 3) Cosa manca sul telefono
        val todo = all.filter { !it.isDir && needsDownload(root, it, state) }.sortedBy { it.size }
        val todoBytes = todo.sumOf { it.size }
        if (todo.isEmpty()) {
            LocalState.save(stateFile, state)
            prefs.lastSync = System.currentTimeMillis()
            _status.value = SyncStatus(running = false, phase = "Sincronizzato", error = dirWarning)
            Repo.bumpLocal()
            return
        }
        if (!allowDownloads) {
            LocalState.save(stateFile, state)
            _status.value = SyncStatus(
                running = false, phase = "In attesa del Wi-Fi", waitingWifi = true,
                pendingFiles = todo.size, pendingBytes = todoBytes, error = dirWarning,
            )
            return
        }

        // 4) Copio i file mancanti
        _status.update {
            it.copy(phase = PHASE_COPY, filesTotal = todo.size, bytesTotal = todoBytes, current = "")
        }
        val job = currentCoroutineContext()[Job]
        val channel = Channel<RemoteEntry>(Channel.UNLIMITED)
        todo.forEach { channel.trySend(it) }
        channel.close()
        val done = AtomicInteger()
        val bytes = AtomicLong()
        val failures = AtomicInteger()
        var lastError: String? = null
        val lastBump = AtomicLong(0L)
        val pausedForWifi = java.util.concurrent.atomic.AtomicBoolean(false)
        val changed = java.util.concurrent.atomic.AtomicBoolean(false)

        try {
            coroutineScope {
                repeat(PARALLEL) {
                    launch {
                        for (e in channel) {
                            ensureActive()
                            // Passato dal Wi-Fi ai dati mobili? Mi fermo qui.
                            if (changed.get() || selectionChanged(prefs, selection)) {
                                changed.set(true)
                                break
                            }
                            if (pausedForWifi.get() || !SyncScheduler.allowDownloads(ctx)) {
                                pausedForWifi.set(true)
                                break
                            }
                            if (root.usableSpace < e.size + SPACE_RESERVE) {
                                throw IOException("Spazio quasi esaurito sul telefono")
                            }
                            _status.update { it.copy(current = e.name) }
                            val dest = File(root, e.path)
                            try {
                                var fileBytes = 0L
                                withNetRetry(PHASE_COPY) {
                                    if (fileBytes > 0) {
                                        bytes.addAndGet(-fileBytes)
                                        fileBytes = 0L
                                    }
                                    dav.download(e.path, dest, ".nsPart", { job?.isActive == false }) { n ->
                                        fileBytes += n
                                        val b = bytes.addAndGet(n)
                                        _status.update { it.copy(bytesDone = b) }
                                    }
                                }
                                if (e.mtime > 0) dest.setLastModified(e.mtime)
                                synchronized(state) { state[e.path] = e.mtime }
                            } catch (ex: IOException) {
                                if (job?.isActive == false) throw CancellationException("Interrotto")
                                failures.incrementAndGet()
                                lastError = ex.message
                            }
                            val d = done.incrementAndGet()
                            _status.update { it.copy(filesDone = d) }
                            if (d % 25 == 0) synchronized(state) { LocalState.save(stateFile, state) }
                            val now = System.currentTimeMillis()
                            val prev = lastBump.get()
                            if (now - prev > 1000 && lastBump.compareAndSet(prev, now)) Repo.bumpLocal()
                        }
                    }
                }
            }
        } finally {
            synchronized(state) { LocalState.save(stateFile, state) }
            Repo.bumpLocal()
        }

        if (changed.get()) return stopForNewSelection()
        if (pausedForWifi.get()) {
            val left = all.filter { !it.isDir && needsDownload(root, it, state) }
            _status.value = SyncStatus(
                running = false, phase = "In attesa del Wi-Fi", waitingWifi = true,
                pendingFiles = left.size, pendingBytes = left.sumOf { it.size }, error = dirWarning,
            )
            return
        }

        prefs.lastSync = System.currentTimeMillis()
        val f = failures.get()
        _status.value = SyncStatus(
            running = false,
            phase = "Sincronizzato",
            error = listOfNotNull(
                if (f > 0) "$f file non copiati (riprovo alla prossima apertura). Ultimo errore: $lastError" else null,
                dirWarning,
            ).joinToString("\n").ifEmpty { null },
        )
    }

    private fun needsDownload(root: File, e: RemoteEntry, state: HashMap<String, Long>): Boolean {
        val f = File(root, e.path)
        if (!f.isFile || f.length() != e.size) return true
        val known = state[e.path]
        if (known == null) {
            state[e.path] = e.mtime
            return false
        }
        return known != e.mtime
    }

    /** Tolgo le scelte doppie (una cartella dentro un'altra gia' scelta). */
    fun normalizeSelection(sel: Collection<String>): List<String> {
        val clean = sel.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct()
        return clean.filter { s -> clean.none { o -> o != s && s.startsWith("$o/") } }.sorted()
    }

    /**
     * Cancella dal telefono:
     * - i file fuori dalle cartelle scelte (sempre);
     * - i file spariti dal NAS dentro le cartelle scelte (copia sempre identica al NAS).
     */
    private fun cleanLocal(
        root: File,
        all: List<RemoteEntry>,
        state: HashMap<String, Long>,
        deleteRemoved: Boolean,
        selection: List<String>,
    ) {
        fun inScope(rel: String) = selection.isEmpty() || selection.any { rel == it || rel.startsWith("$it/") }
        val keep = HashSet<String>(all.size * 2)
        all.forEach { keep.add(it.path) }
        val old = System.currentTimeMillis() - 10 * 60 * 1000
        root.walkBottomUp().forEach { f ->
            if (f == root) return@forEach
            if (f.relativeTo(root).path.replace(File.separatorChar, '/') in recentUploads) return@forEach
            if (f.name.endsWith(".nsPart") || f.name.endsWith(".nsOpen")) {
                if (f.lastModified() < old) f.delete()
                return@forEach
            }
            val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
            if (rel !in keep && (deleteRemoved || !inScope(rel) || f.isDirectory)) {
                if (f.isDirectory) {
                    if (f.list()?.isEmpty() == true) f.delete()
                } else {
                    f.delete()
                }
                state.remove(rel)
            }
        }
        state.keys.retainAll(keep)
    }
}

object SyncScheduler {
    private const val NOW = "sync_now"
    private const val AUTO = "sync_auto"
    private const val PERIODIC = "sync_periodic"

    /** Aggiornamento all'apertura dell'app: parte solo se l'automatico e' acceso. */
    fun syncOnOpen(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.configured || !p.autoSync || !p.backupEnabled) return
        // Nessun vincolo di rete: se la connessione cade per un attimo
        // Android non deve fermare la copia (ci pensa withNetRetry).
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_AUTO to true))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(AUTO, ExistingWorkPolicy.KEEP, req)
    }

    /** Aggiornamento chiesto dall'utente (pulsante): parte sempre. */
    fun syncNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    /** Ferma la copia in corso e ne fa partire una nuova (es. dopo aver cambiato le cartelle). */
    fun restart(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(AUTO)
        val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    /** Le versioni precedenti usavano lo stesso nome per automatico e manuale: tolgo gli avanzi. */
    fun cleanOldWork(ctx: Context) {
        val p = Prefs(ctx)
        if (p.cleanedOldWork) return
        WorkManager.getInstance(ctx).cancelUniqueWork(NOW)
        p.cleanedOldWork = true
    }

    /** Backup spento: ferma tutto, anche una copia in corso. */
    fun cancelAll(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(AUTO)
        wm.cancelUniqueWork(NOW)
    }

    /** Spegne tutti gli aggiornamenti automatici (anche quelli rimasti in sospeso). */
    fun cancelAuto(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(AUTO)
    }

    fun schedulePeriodic(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.configured) return
        if (!prefs.autoSync || !prefs.backupEnabled) {
            // Aggiornamento manuale o backup spento: niente controlli automatici
            cancelAuto(ctx)
            return
        }
        val hours = prefs.syncIntervalHours.toLong().coerceAtLeast(1L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.useMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val req = PeriodicWorkRequestBuilder<SyncWorker>(hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(hours, TimeUnit.HOURS)
            .setInputData(workDataOf(SyncWorker.KEY_AUTO to true))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun allowDownloads(ctx: Context): Boolean {
        if (Prefs(ctx).useMobileData) return true
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        return !cm.isActiveNetworkMetered
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val app = applicationContext
        val prefs = Prefs(app)
        if (!prefs.configured) return@coroutineScope Result.success()
        // Modalita' solo WebDAV: non si copia niente sul telefono
        if (!prefs.backupEnabled) return@coroutineScope Result.success()
        // Aggiornamento automatico ma l'utente l'ha spento: non faccio niente
        if (inputData.getBoolean(KEY_AUTO, false) && !prefs.autoSync) return@coroutineScope Result.success()
        try {
            setForeground(info("Avvio…", 0, true))
        } catch (_: Exception) {
            // In background su Android 12+ puo' non essere permesso: continuo lo stesso.
        }
        val updater = launch {
            while (isActive) {
                delay(1500)
                val s = SyncEngine.status.value
                if (!s.running) continue
                val pct = if (s.bytesTotal > 0) ((s.bytesDone * 100) / s.bytesTotal).toInt() else 0
                val text = if (s.filesTotal > 0) {
                    "${s.filesDone} di ${s.filesTotal} file · ${fmtSize(s.bytesDone)} di ${fmtSize(s.bytesTotal)}"
                } else {
                    "Lettura cartelle: ${s.foldersScanned}"
                }
                notify(info(text, pct, s.bytesTotal == 0L))
            }
        }
        SyncEngine.run(app, SyncScheduler.allowDownloads(app))
        updater.cancel()
        Result.success()
    }

    private fun notify(fi: ForegroundInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, fi.notification)
    }

    private fun info(text: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("Copia dal NAS in corso")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(open)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, n)
        }
    }

    companion object {
        const val NOTIF_ID = 42
        const val KEY_AUTO = "auto"
    }
}
