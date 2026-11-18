package it.furlan.nassync

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val dark = when (vm.themeMode) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(dark) { AppRoot(vm) }
        }
        if (savedInstanceState == null) handleShare(intent)
        if (savedInstanceState == null) handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
        handleShortcut(intent)
    }

    /** File arrivati da "Condividi -> NAS Sync" da un'altra app. */
    /** Collegamento sulla Home: apre direttamente la cartella. */
    private fun handleShortcut(i: Intent?) {
        if (i?.action != ACTION_OPEN_FOLDER || !Prefs(this).configured) return
        vm.openFolder(i.getStringExtra(EXTRA_PATH) ?: "")
    }

    companion object {
        const val ACTION_OPEN_FOLDER = "it.furlan.nassync.OPEN_FOLDER"
        const val EXTRA_PATH = "path"
    }

    private fun handleShare(i: Intent?) {
        if (i == null) return
        val uris: List<Uri> = when (i.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
        if (uris.isNotEmpty() && Prefs(this).configured) vm.setShared(uris)
    }

    override fun onStart() {
        super.onStart()
        // Ogni volta che apri l'app controlla il NAS e copia le novita'.
        SyncScheduler.syncOnOpen(this)
    }
}

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        // Nero profondo con scritte bianche
        darkColorScheme(
            primary = Color(0xFF64B5F6),
            onPrimary = Color.Black,
            secondaryContainer = Color(0xFF1E3A5F),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C1C),
            onSurfaceVariant = Color(0xFFB8B8B8),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0E0E0E),
            surfaceContainer = Color(0xFF141414),
            surfaceContainerHigh = Color(0xFF1C1C1C),
            surfaceContainerHighest = Color(0xFF242424),
            outline = Color(0xFF6E6E6E),
            outlineVariant = Color(0xFF2A2A2A),
        )
    } else {
        lightColorScheme(primary = Color(0xFF1565C0))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(vm: MainViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }
    when (val s = vm.current) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Browse -> BrowseScreen(vm, s.path)
        is Screen.Viewer -> ViewerScreen(vm, s)
        is Screen.Settings -> SettingsScreen(vm, s.firstRun)
        Screen.Picker -> PickerScreen(vm)
    }
    val name = vm.downloadingName
    if (name != null && vm.current !is Screen.Viewer) {
        DownloadDialog(name, vm.downloadProgress)
    }
    if (vm.conflictPrompt == null) vm.uploadStatus?.let { UploadDialog(vm.busyTitle, it) }
    vm.conflictPrompt?.let { ConflictDialog(vm, it) }
    vm.deleteBatch?.let { DeleteManyDialog(vm, it) }
    vm.menuTarget?.let { ItemMenuDialog(vm, it) }
    vm.deleteTarget?.let { DeleteDialog(vm, it) }
    vm.colorTarget?.let { FolderColorDialog(vm, it) }
    vm.shortcutTarget?.let { ShortcutDialog(vm, it) }
    vm.openWithTarget?.let { OpenWithDialog(vm, it) }
}
