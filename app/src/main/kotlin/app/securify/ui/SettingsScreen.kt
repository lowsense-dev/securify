package app.securify.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.securify.BuildConfig
import app.securify.R
import app.securify.core.AppMode
import app.securify.core.PingMethod
import app.securify.core.Store
import app.securify.core.SubUpdateJob
import app.securify.core.Subs
import app.securify.vpn.SecurifyVpn
import libv2ray.Libv2ray

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(modifier: Modifier, onApps: () -> Unit) {
    val ctx = LocalContext.current
    val p = Store.state.collectAsState().value.prefs
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editUrl by rememberSaveable { mutableStateOf(false) }
    val hwid = remember { Subs.hwid(ctx) }
    val xray = remember { runCatching { Libv2ray.checkVersionX() }.getOrDefault("") }

    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.tab_settings)) }, scrollBehavior = scroll) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), modifier = Modifier.padding(pad)) {
            item { Header(R.string.sec_routing) }
            item {
                Item(first = true, title = stringResource(R.string.routing), summary = stringResource(R.string.routing_hint)) {
                    Choice(
                        listOf(stringResource(R.string.routing_all), stringResource(R.string.routing_ru)),
                        if (p.ruDirect) 1 else 0, Modifier.padding(top = 12.dp),
                    ) { i -> Store.prefs { it.copy(ruDirect = i == 1) }; SecurifyVpn.restartIfOn(ctx) }
                }
            }
            item {
                Item(
                    last = true, title = stringResource(R.string.apps),
                    summary = when (p.appMode) {
                        AppMode.OFF -> stringResource(R.string.apps_off)
                        AppMode.ONLY -> stringResource(R.string.apps_only_n, p.apps.size)
                        AppMode.BYPASS -> stringResource(R.string.apps_bypass_n, p.apps.size)
                    },
                    onClick = onApps,
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                )
            }

            item { Header(R.string.sec_ping) }
            item {
                Item(
                    first = true, last = p.ping != PingMethod.REAL, title = stringResource(R.string.ping_method),
                    summary = stringResource(
                        when (p.ping) {
                            PingMethod.TCP -> R.string.ping_tcp_hint
                            PingMethod.ICMP -> R.string.ping_icmp_hint
                            PingMethod.REAL -> R.string.ping_real_hint
                        },
                    ),
                ) {
                    Choice(
                        listOf("TCP", "ICMP", stringResource(R.string.ping_real)), p.ping.ordinal, Modifier.padding(top = 12.dp),
                    ) { i -> Store.prefs { it.copy(ping = PingMethod.entries[i]) }; Store.pings.value = emptyMap() }
                }
            }
            if (p.ping == PingMethod.REAL) item {
                Item(last = true, title = stringResource(R.string.ping_url), summary = p.pingUrl, onClick = { editUrl = true })
            }

            item { Header(R.string.sec_subs) }
            item {
                Item(
                    first = true, title = stringResource(R.string.auto_update), summary = stringResource(R.string.auto_update_hint),
                    onClick = { toggleAuto(ctx, !p.autoUpdate) },
                    trailing = { Switch(p.autoUpdate, { toggleAuto(ctx, it) }) },
                )
            }
            item {
                Item(
                    last = true, title = stringResource(R.string.hwid), summary = stringResource(R.string.hwid_hint, hwid),
                    onClick = {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("HWID", hwid))
                    },
                    trailing = { Switch(p.hwid, { v -> Store.prefs { it.copy(hwid = v) } }) },
                )
            }

            item { Header(R.string.sec_about) }
            item { Item(first = true, last = true, title = "Securify ${BuildConfig.VERSION_NAME}", summary = xray) }
        }
    }

    if (editUrl) {
        var url by rememberSaveable { mutableStateOf(p.pingUrl) }
        AlertDialog(
            onDismissRequest = { editUrl = false },
            title = { Text(stringResource(R.string.ping_url)) },
            text = { OutlinedTextField(url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(
                    enabled = url.startsWith("http://") || url.startsWith("https://"),
                    onClick = { Store.prefs { it.copy(pingUrl = url.trim()) }; editUrl = false },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editUrl = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun toggleAuto(ctx: android.content.Context, on: Boolean) {
    Store.prefs { it.copy(autoUpdate = on) }
    SubUpdateJob.schedule(ctx, on)
}

@Composable
private fun Header(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Item(
    title: String,
    summary: String,
    first: Boolean = false,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val content = @Composable {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing?.let { Row(Modifier.padding(start = 16.dp)) { it() } }
            }
            extra?.invoke(this)
        }
    }
    val m = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    val shape = segmentShape(first, last)
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) Surface(onClick = onClick, shape = shape, color = color, modifier = m) { content() }
    else Surface(shape = shape, color = color, modifier = m) { content() }
}
