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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.securify.R
import app.securify.vpn.SecurifyVpn

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SecurifyTheme { SecurifyApp() } }
    }
}

fun Context.bytes(b: Long): String = Formatter.formatShortFileSize(this, b)

private data class Tab(val label: Int, val on: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_home, Icons.Filled.Shield, Icons.Outlined.Shield),
    Tab(R.string.tab_servers, Icons.Filled.Dns, Icons.Outlined.Dns),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurifyApp() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var appsOpen by rememberSaveable { mutableStateOf(false) }

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

    if (appsOpen) {
        val close = { appsOpen = false; SecurifyVpn.restartIfOn(ctx) }
        BackHandler(onBack = close)
        AppsScreen(onBack = close)
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            ShortNavigationBar {
                tabs.forEachIndexed { i, t ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(if (tab == i) t.on else t.off, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad).consumeWindowInsets(pad)
        when (tab) {
            0 -> HomeScreen(m, onToggle = toggle, onServers = { tab = 1 })
            1 -> ServersScreen(m)
            else -> SettingsScreen(m, onApps = { appsOpen = true })
        }
    }
}
