package io.github.ddagunts.screencast.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ddagunts.screencast.CastMode
import io.github.ddagunts.screencast.WebRtcForegroundService
import io.github.ddagunts.screencast.util.LogRepository

class MainActivity : ComponentActivity() {

    private val vm: CastViewModel by viewModels()
    private val webRtcVm: WebRtcViewModel by viewModels()
    private val atvVm: AndroidTvViewModel by viewModels()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op; caller falls back if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogRepository.initialize(applicationContext)
        LogRepository.log(LogRepository.Level.I, "MainActivity", "app started; persistent log=${LogRepository.filePath(this).name}")
        requestBatteryAllowlist()
        requestPermissionsIfNeeded()
        setContent {
            ScreenCastTheme {
                Surface(Modifier.fillMaxSize()) { AppScaffold(vm, webRtcVm, atvVm) }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isNotEmpty()) permissionRequest.launch(needed.toTypedArray())
    }

    private fun requestBatteryAllowlist() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            LogRepository.log(LogRepository.Level.I, "MainActivity", "battery optimization already disabled")
            return
        }
        LogRepository.log(LogRepository.Level.W, "MainActivity", "requesting battery optimization exemption")
        runCatching {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }.onFailure {
            LogRepository.log(LogRepository.Level.W, "MainActivity", "battery exemption dialog unavailable: ${it.message}")
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val session = WebRtcForegroundService.sessionFlow.value
            if (session != null && !session.volume.isFixed) {
                val step = 0.05
                val next = when (event.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> (session.volume.level + step).coerceAtMost(1.0)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> (session.volume.level - step).coerceAtLeast(0.0)
                    else -> null
                }
                if (next != null) {
                    webRtcVm.setVolume(next)
                    LogRepository.log(LogRepository.Level.I, "MainActivity", "hardware volume routed to Chromecast: $next")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

// Dynamic color pulls the system's Material You palette on API 31+ so the
// app mirrors the user's wallpaper/accent. Pre-31 devices fall back to the
// stock dark scheme; we don't ship a bespoke palette.
@Composable
private fun ScreenCastTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // LocalConfiguration (not ctx.resources.configuration) so recomposition
    // fires when the user toggles dark mode without restarting the Activity.
    val uiMode = LocalConfiguration.current.uiMode
    val colors = when {
        Build.VERSION.SDK_INT >= 31 &&
            uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO ->
                dynamicLightColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicDarkColorScheme(ctx)
        else -> darkColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Screen { Cast, Settings, Logs }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    vm: CastViewModel,
    webRtcVm: WebRtcViewModel,
    atvVm: AndroidTvViewModel,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Cast) }
    val castMode by vm.castMode.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(when (screen) {
                    Screen.Cast -> "StreamCast"
                    Screen.Settings -> "Settings"
                    Screen.Logs -> "Logs"
                })
            },
            navigationIcon = {
                if (screen != Screen.Cast) {
                    IconButton(onClick = { screen = Screen.Cast }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                if (screen == Screen.Cast) CastScreenActions(
                    onOpenSettings = { screen = Screen.Settings },
                    onOpenLogs = { screen = Screen.Logs },
                )
            },
        )
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Cast -> CastScreenRoot(
                    castMode = castMode,
                    onModeChange = { vm.setCastMode(it) },
                    vm = vm,
                    webRtcVm = webRtcVm,
                    atvVm = atvVm,
                )
                Screen.Settings -> SettingsScreen(vm, webRtcVm)
                Screen.Logs -> LogPanelScreen()
            }
        }
    }
}

// Single unified main screen. Mode toggle at the top, the selected mode's
// device picker + active-session UI below. All three VMs stay alive so
// switching modes is instant and doesn't drop in-flight discovery — the
// ATV discovery in particular benefits, since the TVs sometimes take a
// few seconds to respond to the first mDNS query after boot.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastScreenRoot(
    castMode: CastMode,
    onModeChange: (CastMode) -> Unit,
    vm: CastViewModel,
    webRtcVm: WebRtcViewModel,
    atvVm: AndroidTvViewModel,
) {
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val modes = CastMode.values()
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = castMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) {
                    Text(when (mode) {
                        CastMode.HLS -> "HLS"
                        CastMode.WEBRTC -> "WebRTC"
                        CastMode.REMOTE -> "Remote"
                    })
                }
            }
        }
        when (castMode) {
            CastMode.HLS -> CastControlScreen(vm)
            CastMode.WEBRTC -> WebRtcCastBody(webRtcVm)
            CastMode.REMOTE -> AndroidTvRemoteScreen(atvVm)
        }
    }
}

@Composable
private fun CastScreenActions(
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    IconButton(onClick = onOpenSettings) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings")
    }
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Logs") },
            onClick = { menuOpen = false; onOpenLogs() },
        )
    }
}
