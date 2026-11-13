package app.securify.ui

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.securify.R
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SecurifyTheme { SecurifyApp() } }
    }
}

@Composable
fun isLandscape() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val go = { i: Int -> scope.launch { pager.animateScrollToPage(i) }; Unit }
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
        when (o) {
            "rules" -> RulesScreen(onBack = close)
            "logs" -> { BackHandler(onBack = close); LogsScreen(onBack = close) }
            else -> { BackHandler(onBack = close); AppsScreen(onBack = close) }
        }
        return
    }

    val pages = @Composable { m: Modifier ->
        HorizontalPager(pager, m, userScrollEnabled = false) { i ->
            when (i) {
                0 -> HomeScreen(Modifier, onToggle = toggle, onServers = { go(1) })
                1 -> ServersScreen(Modifier)
                else -> SettingsScreen(Modifier, onApps = { overlay = "apps" }, onRules = { overlay = "rules" }, onLogs = { overlay = "logs" })
            }
        }
    }

    if (isLandscape()) {
        Surface(color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Spacer(Modifier.weight(1f))
                tabs.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = pager.targetPage == i,
                        onClick = { go(i) },
                        icon = { Icon(if (pager.targetPage == i) t.on else t.off, null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            pages(Modifier.weight(1f).consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)))
        } }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                ShortNavigationBar {
                    tabs.forEachIndexed { i, t ->
                        ShortNavigationBarItem(
                            selected = pager.targetPage == i,
                            onClick = { go(i) },
                            icon = { Icon(if (pager.targetPage == i) t.on else t.off, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            },
        ) { pad -> pages(Modifier.padding(pad).consumeWindowInsets(pad)) }
    }
}
