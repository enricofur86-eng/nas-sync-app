@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package it.furlan.nassync

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.Apps
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: MainViewModel) {
    val status by SyncEngine.status.collectAsStateWithLifecycle()
    val ver by Repo.version.collectAsStateWithLifecycle()
    var showRename by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val summary = remember(ver, vm.backupEnabled) {
        when {
            !vm.backupEnabled -> "WebDAV · i file restano sul NAS"
            Repo.loaded && Repo.fileCount > 0 -> "${Repo.fileCount} file sul telefono · ${fmtSize(Repo.totalBytes)}"
            else -> "Backup non ancora eseguito"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File") },
                actions = {
                    if (vm.backupEnabled) {
                        IconButton(onClick = { vm.syncNow() }, enabled = !status.running) {
                            Icon(Icons.Filled.Sync, contentDescription = "Aggiorna il backup adesso")
                        }
                    }
                    IconButton(onClick = { vm.push(Screen.Settings(false)) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            ListItem(
                headlineContent = { Text(vm.folderName, fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Column {
                        Text(summary)
                        if (vm.backupEnabled) {
                            Text(
                                selectionLabel(vm.selectedFolders),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    FolderIcon("", 44.dp)
                },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Altro")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rinomina") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { menu = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Colore cartella") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = { menu = false; vm.askColor("") },
                            )
                            if (vm.backupEnabled) {
                                DropdownMenuItem(
                                    text = { Text("Scegli cartelle da copiare") },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                    onClick = { menu = false; vm.openPicker() },
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.combinedClickable(
                    onClick = { vm.push(Screen.Browse("")) },
                    onLongClick = withHaptic { showRename = true },
                ),
            )
            HorizontalDivider()
            if (vm.pendingShare.isNotEmpty()) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${vm.pendingShare.size} file da caricare sul NAS", fontWeight = FontWeight.SemiBold)
                        Text("Apri la cartella dove vuoi metterli e tocca \"Carica qui\".")
                        OutlinedButton(onClick = { vm.cancelShare() }) { Text("Annulla") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (vm.backupEnabled) {
                SyncPanel(status, vm.prefs.lastSync, manual = !vm.autoSync, onSync = { vm.syncNow() })
            } else {
                WebDavPanel(onEnableBackup = { vm.setBackup(true) })
            }
        }
    }

    if (showRename) {
        RenameDialog(
            current = vm.folderName,
            onDismiss = { showRename = false },
            onConfirm = { vm.rename(it); showRename = false },
        )
    }
}

@Composable
private fun WebDavPanel(onEnableBackup: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Modalità WebDAV", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Le cartelle si leggono direttamente dal NAS e sul telefono non viene copiato niente. " +
                    "I file si scaricano solo quando li apri."
            )
            Text(
                "Se vuoi avere alcune cartelle sempre sul telefono, anche senza rete, attiva il backup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onEnableBackup) { Text("Attiva backup…") }
        }
    }
}

@Composable
private fun SyncPanel(s: SyncStatus, lastSync: Long, manual: Boolean, onSync: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                s.running -> {
                    Text(s.phase, fontWeight = FontWeight.SemiBold)
                    if (s.filesTotal > 0) {
                        val frac = if (s.bytesTotal > 0) (s.bytesDone.toFloat() / s.bytesTotal).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        Text("${s.filesDone} di ${s.filesTotal} file · ${fmtSize(s.bytesDone)} di ${fmtSize(s.bytesTotal)}")
                        if (s.current.isNotEmpty()) {
                            Text(
                                s.current, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Puoi chiudere l'app: la copia continua in background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Cartelle lette: ${s.foldersScanned}")
                    }
                }
                s.waitingWifi -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("In attesa del Wi-Fi", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Mancano ${s.pendingFiles} file (${fmtSize(s.pendingBytes)}). " +
                            "La copia parte appena sei in Wi-Fi. Puoi abilitare i dati mobili nelle impostazioni."
                    )
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (s.error != null) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (lastSync > 0) "Ultima sincronizzazione: ${fmtDate(lastSync)}" else "Mai sincronizzato",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (manual && !s.running) {
                Text(
                    "Aggiornamento manuale: l'archivio si aggiorna solo quando lo chiedi tu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSync) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiorna adesso")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rinomina cartella") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun DownloadDialog(name: String, progress: Float) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        title = { Text("Scarico dal NAS…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

// ---------------------------------------------------------------- Cartelle

@Composable
fun BrowseScreen(vm: MainViewModel, path: String) {
    val ctx = LocalContext.current
    val ver by Repo.version.collectAsStateWithLifecycle()
    val tick by Repo.localTick.collectAsStateWithLifecycle()
    val status by SyncEngine.status.collectAsStateWithLifecycle()
    // Backup: indice sul telefono; WebDAV: letto dal vivo dal NAS ogni volta che apri la cartella
    LaunchedEffect(path, vm.backupEnabled) { vm.loadLive(path) }
    val items = if (vm.backupEnabled) remember(path, ver) { Repo.children(path) } else vm.live[path] ?: emptyList()
    val liveBusy = !vm.backupEnabled && vm.liveLoading == path
    val liveErr = vm.liveError?.takeIf { !vm.backupEnabled && it.first == path }?.second
    val title = if (path.isEmpty()) vm.folderName else path.substringAfterLast('/')
    // Si puo' caricare solo dentro le cartelle che l'app copia
    val canUpload = vm.canUpload(path)
    // Selezione di piu' elementi (si attiva tenendo premuto)
    val selecting = vm.selectedItems.isNotEmpty()
    val selEntries = items.filter { it.path in vm.selectedItems }
    BackHandler(enabled = selecting) { vm.clearItems() }
    // Ricordo dove ero arrivato a scorrere in questa cartella
    val listKey = "L:$path"
    val gridKey = "G:$path"
    val listState = remember(path) { vm.savedScroll(listKey).let { LazyListState(it.first, it.second) } }
    val gridState = remember(path) { vm.savedScroll(gridKey).let { LazyGridState(it.first, it.second) } }
    DisposableEffect(path) {
        onDispose {
            vm.saveScroll(listKey, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            vm.saveScroll(gridKey, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
    }
    var fabMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.upload(path, uris)
    }

    Scaffold(
        floatingActionButton = {
            if (canUpload && vm.pendingShare.isEmpty() && !selecting) {
                Box {
                    FloatingActionButton(onClick = { fabMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi")
                    }
                    DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Carica file sul NAS") },
                            leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
                            onClick = { fabMenu = false; filePicker.launch(arrayOf("*/*")) },
                        )
                        DropdownMenuItem(
                            text = { Text("Nuova cartella") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = { fabMenu = false; newFolder = true },
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (vm.pendingShare.isNotEmpty()) {
                ShareBar(
                    count = vm.pendingShare.size,
                    canUpload = canUpload,
                    onCancel = { vm.cancelShare() },
                    onUpload = { vm.uploadShared(path) },
                )
            }
        },
        topBar = {
            Column {
                if (selecting) TopAppBar(
                    title = { Text("${vm.selectedItems.size} selezionati") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearItems() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Annulla selezione")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll(items) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Seleziona tutto")
                        }
                        if (selEntries.any { !it.isDir }) {
                            IconButton(onClick = { vm.shareMany(ctx, selEntries) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Condividi")
                            }
                        }
                        if (selEntries.any { vm.canDelete(it) }) {
                            IconButton(onClick = { vm.askDeleteMany(selEntries) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                            }
                        }
                        if (selEntries.size == 1) {
                            IconButton(onClick = { vm.longPress(ctx, selEntries[0]) }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Altro")
                            }
                        }
                    },
                ) else TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { vm.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    actions = {
                        if (!vm.backupEnabled) {
                            IconButton(onClick = { vm.loadLive(path) }, enabled = !liveBusy) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Ricarica")
                            }
                        }
                        IconButton(onClick = { vm.toggleGrid(path) }) {
                            Icon(
                                if (vm.isGrid(path)) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                contentDescription = "Cambia vista",
                            )
                        }
                        if (canUpload) {
                            IconButton(onClick = { newFolder = true }) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nuova cartella")
                            }
                        }
                        IconButton(onClick = { vm.goHome() }) {
                            Icon(Icons.Filled.Home, contentDescription = "Inizio")
                        }
                    },
                )
                if (status.running || liveBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        if (items.isEmpty()) {
            Box(
                Modifier
                    .padding(pad)
                    .fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                when {
                    liveErr != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(liveErr, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Button(onClick = { vm.loadLive(path) }) { Text("Riprova") }
                    }
                    liveBusy || (!vm.backupEnabled && vm.live[path] == null) -> CircularProgressIndicator()
                    else -> Text(
                        if (status.running) "Sto leggendo il NAS…" else "Cartella vuota",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (vm.isGrid(path)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(
                    start = 6.dp, end = 6.dp,
                    top = pad.calculateTopPadding() + 6.dp,
                    bottom = pad.calculateBottomPadding() + 6.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.path }) { e ->
                    GridCell(
                        e, tick, selected = e.path in vm.selectedItems,
                        onClick = { if (selecting) vm.toggleItem(e) else vm.open(ctx, e) },
                        onLongClick = { vm.toggleItem(e) },
                    )
                }
            }
        } else {
            LazyColumn(state = listState, contentPadding = pad, modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.path }) { e ->
                    RowItem(
                        e, tick, selected = e.path in vm.selectedItems,
                        onClick = { if (selecting) vm.toggleItem(e) else vm.open(ctx, e) },
                        onLongClick = { vm.toggleItem(e) },
                    )
                }
            }
        }
    }

    if (newFolder) {
        NewFolderDialog(
            onDismiss = { newFolder = false },
            onConfirm = { vm.createFolder(path, it); newFolder = false },
        )
    }
}

@Composable
private fun ShareBar(count: Int, canUpload: Boolean, onCancel: () -> Unit, onUpload: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (canUpload) "Caricare qui $count file sul NAS?"
                else "Apri una delle cartelle che l'app copia per caricare i $count file",
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Annulla") }
                Button(onClick = onUpload, enabled = canUpload) { Text("Carica qui") }
            }
        }
    }
}

@Composable
fun UploadDialog(title: String, text: String) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova cartella sul NAS") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Nome") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Crea") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private fun iconFor(e: RemoteEntry): ImageVector = when {
    e.isDir -> Icons.Filled.Folder
    isImage(e.name) -> Icons.Filled.Image
    isVideo(e.name) -> Icons.Filled.Movie
    extOf(e.name) == "pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.Filled.Description
}

@Composable
private fun Thumb(e: RemoteEntry, local: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (!e.isDir && local && (isImage(e.name) || isVideo(e.name))) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(Storage.find(ctx, e)).size(320).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else if (e.isDir) {
        FolderIcon(e.path, size)
    } else {
        FileTypeIcon(e.name, size)
    }
}

@Composable
private fun RowItem(e: RemoteEntry, tick: Int, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    val local = remember(e.path, e.size, tick) { Storage.isLocal(ctx, e) }
    val sub = remember(e, tick) {
        if (e.isDir) Repo.children(e.path).size.let { if (it > 0) "$it elementi" else "Cartella" }
        else listOf(fmtSize(e.size), fmtDate(e.mtime)).filter { it.isNotEmpty() }.joinToString(" · ")
    }
    ListItem(
        headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(sub) },
        leadingContent = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Thumb(e, local, 44.dp, Modifier.fillMaxSize())
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle, contentDescription = "Selezionato",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surface,
        ),
        trailingContent = if (!e.isDir && !local) {
            {
                Icon(
                    Icons.Filled.CloudDownload, contentDescription = "Non ancora sul telefono",
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = withHaptic(onLongClick)),
    )
}

@Composable
private fun GridCell(e: RemoteEntry, tick: Int, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ctx = LocalContext.current
    val local = remember(e.path, e.size, tick) { Storage.isLocal(ctx, e) }
    Column(
        Modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = withHaptic(onLongClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Thumb(e, local, if (e.isDir) 64.dp else 58.dp, Modifier.fillMaxSize())
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Selezionato",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
            if (!e.isDir && !local) {
                Icon(
                    Icons.Filled.CloudDownload, contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
        Text(
            e.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------- Foto

@Composable
fun ViewerScreen(vm: MainViewModel, s: Screen.Viewer) {
    val ctx = LocalContext.current
    val ver by Repo.version.collectAsStateWithLifecycle()
    val tick by Repo.localTick.collectAsStateWithLifecycle()
    val source = vm.children(s.dir)
    val images = remember(s.dir, ver, source) { source.filter { !it.isDir && isImage(it.name) } }
    if (images.isEmpty()) {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val start = images.indexOfFirst { it.path == s.start }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { images.size }
    var showBars by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { images[it].path },
        ) { page ->
            ZoomableImage(vm, images[page], tick, onTap = { showBars = !showBars })
        }
        if (showBars) {
            val cur = images.getOrNull(pager.currentPage)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                }
                Text(
                    cur?.name ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("${pager.currentPage + 1}/${images.size}", color = Color.White, modifier = Modifier.padding(end = 4.dp))
                if (cur != null) {
                    IconButton(onClick = { vm.share(ctx, cur) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Condividi", tint = Color.White)
                    }
                    if (vm.canDelete(cur)) {
                        IconButton(onClick = { vm.askDelete(cur) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(vm: MainViewModel, e: RemoteEntry, tick: Int, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val local = remember(e.path, e.size, tick) { Storage.isLocal(ctx, e) }
    if (!local) {
        LaunchedEffect(e.path, tick) { vm.ensureLocal(e) { } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Scarico dal NAS… ${fmtSize(e.size)}", color = Color.White)
            }
        }
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val zoomMod = if (scale > 1f) {
        Modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
        }
    } else Modifier
    AsyncImage(
        model = Storage.find(ctx, e),
        contentDescription = e.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .then(zoomMod)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
    )
}

// ---------------------------------------------------------------- Impostazioni

@Composable
fun SettingsScreen(vm: MainViewModel, firstRun: Boolean) {
    val p = vm.prefs
    var url by remember { mutableStateOf(p.url) }
    var user by remember { mutableStateOf(p.user) }
    var pass by remember { mutableStateOf(p.password) }
    var showPass by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(p.certPin) }
    var mobile by remember { mutableStateOf(p.useMobileData) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "Collega il NAS" else "Impostazioni") },
                navigationIcon = {
                    if (!firstRun) {
                        IconButton(onClick = { vm.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (firstRun) {
                Text("Inserisci i dati WebDAV del tuo NAS: indirizzo con la porta, utente e password.")
            }
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Indirizzo WebDAV") },
                placeholder = { Text("https://mionas.ddns.net:5006") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Utente") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Mostra password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (pin.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Certificato del NAS confermato", fontWeight = FontWeight.Medium)
                        Text(
                            "Impronta: ${pin.take(23)}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { pin = "" }) { Text("Dimentica") }
                }
            }
            if (vm.backupEnabled) SwitchRow(
                "Copia anche con i dati mobili",
                "Se è spento, i file nuovi vengono copiati solo in Wi-Fi. L'elenco delle cartelle si aggiorna comunque.",
                mobile,
            ) { mobile = it }
            if (!firstRun) {
                HorizontalDivider()
                SwitchRow(
                    "Copia sul telefono (backup)",
                    if (vm.backupEnabled) "Le cartelle scelte vengono copiate sul telefono e restano disponibili anche senza rete. " +
                        "La copia è sempre identica al NAS: i file cancellati dal NAS spariscono anche dal telefono."
                    else "Spento: l'app legge i file direttamente dal NAS (WebDAV) e non copia niente. " +
                        "Accendilo per scegliere le cartelle da tenere sul telefono.",
                    vm.backupEnabled,
                ) { vm.setBackup(it) }
            }
            if (!firstRun && vm.backupEnabled) {
                SwitchRow(
                    "Aggiornamento automatico",
                    if (vm.autoSync) "Aggiorna quando apri l'app e alla frequenza scelta qui sotto."
                    else "Spento: l'archivio si aggiorna solo quando tocchi \"Aggiorna adesso\".",
                    vm.autoSync,
                ) { vm.setAuto(it) }
                if (vm.autoSync) {
                    Text("Frequenza del controllo automatico", fontWeight = FontWeight.Medium)
                    listOf(
                        1 to "Ogni ora", 2 to "Ogni 2 ore", 6 to "Ogni 6 ore",
                        12 to "Ogni 12 ore", 24 to "Ogni 24 ore", 168 to "Una volta a settimana",
                    ).forEach { (h, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.setInterval(h) },
                        ) {
                            RadioButton(selected = vm.syncInterval == h, onClick = { vm.setInterval(h) })
                            Text(label)
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Tema", fontWeight = FontWeight.Medium)
            listOf("dark" to "Scuro (nero)", "light" to "Chiaro", "system" to "Come il telefono").forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.setTheme(mode) },
                ) {
                    RadioButton(selected = vm.themeMode == mode, onClick = { vm.setTheme(mode) })
                    Text(label)
                }
            }
            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    enabled = !testing && url.isNotBlank(),
                    onClick = {
                        testing = true
                        result = null
                        scope.launch {
                            result = vm.testConnection(url, user, pass, pin)
                            testing = false
                        }
                    },
                ) { Text("Prova connessione") }
                Button(
                    enabled = !testing && url.isNotBlank(),
                    onClick = { vm.saveSettings(url, user, pass, pin, mobile) },
                ) { Text("Salva") }
            }
            if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.certPrompt?.let { info ->
                CertDialog(
                    info = info,
                    onTrust = {
                        pin = info.sha256
                        vm.dismissCert()
                        testing = true
                        result = null
                        scope.launch {
                            result = vm.testConnection(url, user, pass, info.sha256)
                            testing = false
                        }
                    },
                    onCancel = { vm.dismissCert() },
                )
            }
            result?.let {
                Text(
                    it,
                    color = if (it.startsWith("OK")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            if (!firstRun) {
                HorizontalDivider()
                Text("App predefinite", fontWeight = FontWeight.Medium)
                if (vm.defaultApps.isEmpty()) {
                    Text(
                        "Nessuna: quando apri un file ti viene chiesto con quale app aprirlo. " +
                            "Spunta \"Usa sempre\" nell'elenco per ricordare la scelta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val ctx = LocalContext.current
                    vm.defaultApps.entries.sortedBy { it.key }.forEach { (ext, key) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileTypeIcon("x.$ext", 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(".$ext", fontWeight = FontWeight.Medium)
                                Text(
                                    FileOpener.labelOf(ctx, key),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { vm.forgetDefault(ext) }) { Text("Rimuovi") }
                        }
                    }
                }
            }
            if (!firstRun && !vm.backupEnabled) {
                HorizontalDivider()
                var askClear by remember { mutableStateOf(false) }
                Text(
                    "Se prima avevi il backup acceso, i file copiati restano sul telefono finché non li elimini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { askClear = true }) { Text("Elimina la copia dal telefono") }
                if (askClear) {
                    AlertDialog(
                        onDismissRequest = { askClear = false },
                        title = { Text("Eliminare la copia?") },
                        text = { Text("I file copiati sul telefono verranno cancellati per liberare spazio. Sul NAS non cambia niente.") },
                        confirmButton = {
                            TextButton(onClick = { askClear = false; vm.clearLocalCopy() }) {
                                Text("Elimina", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = { TextButton(onClick = { askClear = false }) { Text("Annulla") } },
                    )
                }
            }
            if (!firstRun && vm.backupEnabled) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cartelle da copiare", fontWeight = FontWeight.Medium)
                        Text(
                            selectionLabel(vm.selectedFolders),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { vm.openPicker() }) { Text("Scegli") }
                }
                HorizontalDivider()
                Text(
                    "I file copiati stanno nella memoria del telefono, nella cartella dell'app. " +
                        "Se disinstalli l'app vengono cancellati; reinstallandola e rifacendo il login si ricopia tutto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Solo nella versione per F-Droid: su Google Play i link di donazione non sono ammessi.
            if (!firstRun && BuildConfig.DONATE_URL.isNotEmpty()) {
                val ctx = LocalContext.current
                HorizontalDivider()
                Text("Ti piace NAS Sync?", fontWeight = FontWeight.Medium)
                Text(
                    "L'app è gratuita e senza pubblicità. Se ti è utile puoi offrire un caffè allo sviluppatore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(BuildConfig.DONATE_URL),
                            ),
                        )
                    } catch (e: android.content.ActivityNotFoundException) {
                    }
                }) { Text("Sostieni lo sviluppo (PayPal)") }
            }
        }
    }
}

@Composable
private fun CertDialog(info: CertInfo, onTrust: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Certificato del NAS") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Il NAS usa un certificato HTTPS non verificato da un'autorità: è normale per i NAS di casa. " +
                        "Se è il tuo NAS, confermalo: l'app accetterà solo questo certificato."
                )
                Text("Emesso per: ${info.subject}", style = MaterialTheme.typography.bodySmall)
                Text("Emesso da: ${info.issuer}", style = MaterialTheme.typography.bodySmall)
                Text("Valido fino al: ${info.validUntil}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Impronta SHA-256:\n${info.sha256}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text("Mi fido") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annulla") } },
    )
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


fun selectionLabel(sel: Set<String>): String =
    if (sel.isEmpty()) "Copia tutto il NAS"
    else "Copia solo: " + sel.sorted().joinToString(", ") { "/$it" }

// ---------------------------------------------------------------- Scelta cartelle

@Composable
fun PickerScreen(vm: MainViewModel) {
    val path = vm.pickerPath
    BackHandler(enabled = path.isNotEmpty()) { vm.pickerUp() }
    val sel = vm.pickerSelection

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cartelle da copiare")
                        Text(
                            if (path.isEmpty()) "NAS /" else "NAS /$path",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (path.isNotEmpty()) vm.pickerUp() else vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (sel.isEmpty()) "Nessuna cartella spuntata: verrà copiato tutto il NAS."
                        else "Spuntate: " + sel.sorted().joinToString(", ") { "/$it" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Tocca il nome per entrare in una cartella, la casella per sceglierla. " +
                            "I file già copiati che restano fuori dalla scelta vengono tolti dal telefono (sul NAS restano).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { vm.clearSelection() }, enabled = sel.isNotEmpty()) {
                            Text("Togli tutte")
                        }
                        Button(onClick = { vm.saveSelection() }) { Text("Salva e sincronizza") }
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when {
                vm.pickerLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.pickerError != null -> Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(vm.pickerError ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { vm.loadPicker(path) }) { Text("Riprova") }
                }
                vm.pickerItems.isEmpty() -> Text(
                    "Nessuna sottocartella qui",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.pickerItems, key = { it.path }) { e ->
                        val inherited = sel.any { e.path.startsWith("$it/") }
                        val checked = inherited || e.path in sel
                        val hasChosenInside = !checked && sel.any { it.startsWith("${e.path}/") }
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FolderIcon(e.path, 24.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            supportingContent = when {
                                inherited -> { { Text("Inclusa nella cartella scelta sopra") } }
                                hasChosenInside -> { { Text("Contiene cartelle scelte") } }
                                else -> null
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    enabled = !inherited,
                                    onCheckedChange = { vm.toggleSelect(e.path) },
                                )
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Apri")
                            },
                            modifier = Modifier.clickable { vm.loadPicker(e.path) },
                        )
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------- Menu ed eliminazione

@Composable
fun ItemMenuDialog(vm: MainViewModel, e: RemoteEntry) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { vm.closeMenu() },
        confirmButton = { TextButton(onClick = { vm.closeMenu() }) { Text("Chiudi") } },
        title = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (e.isDir) {
                    ListItem(
                        headlineContent = { Text("Aggiungi alla Home") },
                        leadingContent = { Icon(Icons.Filled.AddToHomeScreen, contentDescription = null) },
                        modifier = Modifier.clickable { vm.closeMenu(); vm.pinFolder(ctx, e.path) },
                    )
                    ListItem(
                        headlineContent = { Text("Colore cartella") },
                        leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                        modifier = Modifier.clickable { vm.askColor(e.path) },
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("Apri con…") },
                        leadingContent = { Icon(Icons.Filled.Apps, contentDescription = null) },
                        modifier = Modifier.clickable { vm.askOpenWith(e) },
                    )
                    ListItem(
                        headlineContent = { Text("Condividi") },
                        leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                        modifier = Modifier.clickable { vm.closeMenu(); vm.share(ctx, e) },
                    )
                }
                if (vm.canDelete(e)) {
                    ListItem(
                        headlineContent = { Text("Elimina dal NAS", color = MaterialTheme.colorScheme.error) },
                        leadingContent = {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier.clickable { vm.askDelete(e) },
                    )
                }
            }
        },
    )
}

@Composable
fun DeleteDialog(vm: MainViewModel, e: RemoteEntry) {
    val count = remember(e.path) { if (e.isDir) Repo.countFilesUnder(e.path) else 0 }
    AlertDialog(
        onDismissRequest = { vm.cancelDelete() },
        title = { Text(if (e.isDir) "Eliminare la cartella?" else "Eliminare il file?") },
        text = {
            Text(
                if (e.isDir) "La cartella \u201c${e.name}\u201d e tutto il suo contenuto ($count file) " +
                    "verranno eliminati dal NAS e dal telefono.\n\nL'app non può annullare l'operazione."
                else "\u201c${e.name}\u201d verrà eliminato dal NAS e dal telefono.\n\nL'app non può annullare l'operazione."
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmDelete() }) {
                Text("Elimina", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text("Annulla") } },
    )
}


// ---------------------------------------------------------------- Colore cartelle

@Composable
fun FolderColorDialog(vm: MainViewModel, path: String) {
    val current = FolderColors.colorFor(path)
    val name = if (path.isEmpty()) vm.folderName else path.substringAfterLast('/')
    AlertDialog(
        onDismissRequest = { vm.closeColor() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolderIcon(path, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FOLDER_PALETTE.forEach { (_, color) ->
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == current) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            )
                            .clickable { vm.setFolderColor(path, color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color == current) Icon(Icons.Filled.Check, contentDescription = "Scelto", tint = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.closeColor() }) { Text("Chiudi") } },
        dismissButton = { TextButton(onClick = { vm.setFolderColor(path, null) }) { Text("Giallo classico") } },
    )
}


// ---------------------------------------------------------------- Collegamento sulla Home

@Composable
fun ShortcutDialog(vm: MainViewModel, path: String) {
    val ctx = LocalContext.current
    var name by remember(path) { mutableStateOf(path.substringAfterLast('/').ifEmpty { vm.folderName }) }
    var bg by remember(path) { mutableStateOf(SHORTCUT_COLORS.first()) }
    var symbol by remember(path) { mutableStateOf(SHORTCUT_SYMBOLS.first().second) }
    val fg = symbolColorOn(bg)

    AlertDialog(
        onDismissRequest = { vm.closeShortcut() },
        title = { Text("Collegamento sulla Home") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Anteprima
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(symbol), contentDescription = null,
                            tint = fg, modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name.ifBlank { "…" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("Nome sul collegamento") },
                    supportingText = { Text("Sul NAS la cartella resta \u201c${path.substringAfterLast('/').ifEmpty { "/" }}\u201d") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Colore", fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SHORTCUT_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (c == bg) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { bg = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == bg) Icon(Icons.Filled.Check, contentDescription = "Scelto", tint = symbolColorOn(c))
                        }
                    }
                }
                Text("Simbolo", fontWeight = FontWeight.Medium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SHORTCUT_SYMBOLS.forEach { (label, res) ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (res == symbol) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { symbol = res },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                androidx.compose.ui.res.painterResource(res), contentDescription = label,
                                tint = if (res == symbol) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.createShortcut(ctx, path, name, bg, symbol) }) { Text("Aggiungi alla Home") }
        },
        dismissButton = { TextButton(onClick = { vm.closeShortcut() }) { Text("Annulla") } },
    )
}


// ---------------------------------------------------------------- Apri con…

@Composable
fun OpenWithDialog(vm: MainViewModel, e: RemoteEntry) {
    val ctx = LocalContext.current
    val apps = remember(e.path) { FileOpener.appsFor(ctx, e.name) }
    val ext = extOf(e.name)
    var rememberChoice by remember(e.path) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { vm.closeOpenWith() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTypeIcon(e.name, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text("Apri con…")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    e.name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (isImage(e.name)) {
                        item {
                            ListItem(
                                headlineContent = { Text("Visualizzatore di NAS Sync") },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Image, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp),
                                    )
                                },
                                modifier = Modifier.clickable { vm.chooseApp(ctx, e, INTERNAL_VIEWER, rememberChoice) },
                            )
                        }
                    }
                    items(apps, key = { it.key }) { a ->
                        val bmp = remember(a.key) {
                            try {
                                a.icon?.toBitmap(96, 96)?.asImageBitmap()
                            } catch (_: Exception) {
                                null
                            }
                        }
                        ListItem(
                            headlineContent = { Text(a.label) },
                            leadingContent = {
                                if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(36.dp))
                                else Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(36.dp))
                            },
                            modifier = Modifier.clickable { vm.chooseApp(ctx, e, a.key, rememberChoice) },
                        )
                    }
                    if (apps.isEmpty() && !isImage(e.name)) {
                        item {
                            Text(
                                "Sul telefono non c'è nessuna app che apre i file .$ext.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
                if (ext.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberChoice = !rememberChoice },
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                        Text("Usa sempre per i file .$ext")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.closeOpenWith() }) { Text("Annulla") } },
    )
}


/** Vibrazione breve quando si tiene premuto (apre il menu). */
@Composable
fun withHaptic(action: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
}


// ---------------------------------------------------------------- Eliminazione multipla e file uguali

@Composable
fun DeleteManyDialog(vm: MainViewModel, list: List<RemoteEntry>) {
    val dirs = list.count { it.isDir }
    val files = list.size - dirs
    AlertDialog(
        onDismissRequest = { vm.cancelDeleteMany() },
        title = { Text("Eliminare ${list.size} elementi?") },
        text = {
            Text(
                listOfNotNull(
                    if (files > 0) "$files file" else null,
                    if (dirs > 0) "$dirs cartelle (con tutto il contenuto)" else null,
                ).joinToString(" e ") + " verranno eliminati dal NAS e dal telefono.\n\nL'app non può annullare l'operazione."
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmDeleteMany() }) {
                Text("Elimina", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { vm.cancelDeleteMany() }) { Text("Annulla") } },
    )
}

@Composable
fun ConflictDialog(vm: MainViewModel, p: MainViewModel.ConflictPrompt) {
    var applyAll by remember(p) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.answerConflict(MainViewModel.ConflictChoice.SKIP, false) },
        title = { Text("File già presente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileTypeIcon(p.name, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("Nella cartella del NAS c'è già un file con questo nome.")
                Text(
                    "Sul NAS: " + listOf(fmtSize(p.existing.size), fmtDate(p.existing.mtime))
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (p.newSize != null) {
                    Text("Nuovo: ${fmtSize(p.newSize)}", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { vm.answerConflict(MainViewModel.ConflictChoice.OVERWRITE, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sovrascrivi") }
                OutlinedButton(
                    onClick = { vm.answerConflict(MainViewModel.ConflictChoice.KEEP_BOTH, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mantieni entrambi (aggiunge \u201c(1)\u201d)") }
                OutlinedButton(
                    onClick = { vm.answerConflict(MainViewModel.ConflictChoice.SKIP, applyAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salta questo file") }
                if (p.remaining > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyAll = !applyAll },
                    ) {
                        Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                        Text("Fai lo stesso per gli altri file uguali")
                    }
                }
            }
        },
        confirmButton = { },
    )
}
