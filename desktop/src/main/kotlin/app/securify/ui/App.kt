package app.securify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.securify.vpn.PasswordPrompt
import app.securify.vpn.SecurifyVpn
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_home, MiuixIcons.Home),
    Tab(R.string.tab_servers, MiuixIcons.Layers),
    Tab(R.string.tab_settings, MiuixIcons.Settings),
)

/**
 * The window is a fixed phone-shaped one, so the layout is the mobile one:
 * three tabs in the bottom bar, no rail and no side-by-side panes.
 */
@Composable
fun SecurifyApp(initialTab: Int = 0) {
    // the root scaffold owns the popup host, so dialogs have somewhere to render
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MiuixTheme.colorScheme.surface,
    ) {
        Content(initialTab)
        SecurifyVpn.passwordPrompt.collectAsState().value?.let { PasswordDialog(it) }
    }
}

@Composable
private fun PasswordDialog(prompt: PasswordPrompt) {
    var pw by remember(prompt) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (pw.isNotEmpty()) prompt.answer.complete(pw) }
    AppDialog(
        title = stringResource(R.string.password_title),
        summary = stringResource(R.string.tun_password_hint),
        onDismiss = { prompt.answer.complete(null) },
    ) {
        LaunchedEffect(prompt) { focus.requestFocus() }
        TextField(
            value = pw,
            onValueChange = { pw = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = stringResource(R.string.password_field),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (prompt.retry) {
            Text(
                stringResource(R.string.password_wrong),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        DialogButtons(
            confirm = stringResource(R.string.action_connect),
            onConfirm = { submit() },
            enabled = pw.isNotEmpty(),
            onDismiss = { prompt.answer.complete(null) },
        )
    }
}

@Composable
private fun Content(initialTab: Int) {
    var current by rememberSaveable { mutableStateOf(initialTab) }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(
                color = MiuixTheme.colorScheme.surface,
                showDivider = true,
                defaultWindowInsetsPadding = false,
            ) {
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
