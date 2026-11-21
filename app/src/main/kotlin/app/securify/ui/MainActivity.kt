package app.securify.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import app.securify.R
import app.securify.vpn.SecurifyVpn
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SecurifyTheme {
                Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                    SecurifyApp()
                }
            }
        }
    }
}

fun Context.bytes(b: Long): String = Formatter.formatShortFileSize(this, b)

private data class Tab(val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_home, MiuixIcons.Home),
    Tab(R.string.tab_servers, MiuixIcons.Layers),
    Tab(R.string.tab_settings, MiuixIcons.Settings),
)

@Composable
fun SecurifyApp() {
    val ctx = LocalContext.current
    var current by rememberSaveable { mutableStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(ctx) == null) SecurifyVpn.start(ctx)
    }
    val connect = { VpnService.prepare(ctx)?.let(vpnPermission::launch) ?: SecurifyVpn.start(ctx) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { connect() }
    val toggle = {
        when {
            SecurifyVpn.isActive -> SecurifyVpn.stop(ctx)
            Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> connect()
        }
    }

    overlay?.let { o ->
        val close = { overlay = null; if (o != "logs") SecurifyVpn.restartIfOn(ctx) }
        BackHandler(enabled = o != "rules", onBack = close)
        when (o) {
            "rules" -> RulesScreen(onBack = close)
            "logs" -> LogsScreen(onBack = close)
            else -> AppsScreen(onBack = close)
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(color = MiuixTheme.colorScheme.surface, defaultWindowInsetsPadding = false) {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = current == i,
                        onClick = { current = i },
                        icon = tab.icon,
                        label = stringResource(tab.label),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (current) {
                0 -> HomeScreen(Modifier, onToggle = toggle, onServers = { current = 1 })
                1 -> ServersScreen(Modifier)
                else -> SettingsScreen(Modifier, onApps = { overlay = "apps" }, onRules = { overlay = "rules" }, onLogs = { overlay = "logs" })
            }
        }
    }
}
