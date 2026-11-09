package app.securify.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.securify.vpn.PasswordPrompt
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.launch

private data class Tab(val label: String, val on: androidx.compose.ui.graphics.vector.ImageVector, val off: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_home, Icons.Filled.Shield, Icons.Outlined.Shield),
    Tab(R.string.tab_servers, Icons.Filled.Dns, Icons.Outlined.Dns),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecurifyApp() {
    BoxWithConstraints {
        CompositionLocalProvider(LocalLandscape provides (maxWidth > maxHeight && maxWidth >= 600.dp)) { Content() }
    }
    SecurifyVpn.passwordPrompt.collectAsState().value?.let { PasswordDialog(it) }
}

@Composable
private fun PasswordDialog(prompt: PasswordPrompt) {
    var pw by remember(prompt) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (pw.isNotEmpty()) prompt.answer.complete(pw) }
    AlertDialog(
        onDismissRequest = { prompt.answer.complete(null) },
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text(stringResource(R.string.password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.tun_password_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    pw, { pw = it }, Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    isError = prompt.retry,
                    supportingText = if (prompt.retry) ({ Text(stringResource(R.string.password_wrong)) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = pw.isNotEmpty()) { Text(stringResource(R.string.action_connect)) } },
        dismissButton = { TextButton(onClick = { prompt.answer.complete(null) }) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(prompt) { focus.requestFocus() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Content() {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val go = { i: Int -> scope.launch { pager.animateScrollToPage(i) }; Unit }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }

    val toggle = { if (SecurifyVpn.isActive) SecurifyVpn.stop() else SecurifyVpn.start() }

    overlay?.let { o ->
        val close = { overlay = null; if (o != "logs") SecurifyVpn.restartIfOn() }
        when (o) {
            "rules" -> RulesScreen(onBack = close)
            "logs" -> LogsScreen(onBack = close)
            else -> AppsScreen(onBack = close)
        }
        return
    }

    val pages = @Composable { m: Modifier ->
        HorizontalPager(pager, m) { i ->
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
