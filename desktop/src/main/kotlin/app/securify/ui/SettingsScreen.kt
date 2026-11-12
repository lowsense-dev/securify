package app.securify.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.rememberCoroutineScope
import app.securify.core.AppMode
import app.securify.core.Geo
import app.securify.core.GeoSource
import app.securify.core.SubAgent
import app.securify.core.UpdateState
import app.securify.core.Updater
import androidx.compose.material3.LinearWavyProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.securify.core.PingMethod
import app.securify.core.Store
import app.securify.desktop.Env
import app.securify.desktop.addToMenu
import app.securify.desktop.xrayVersion
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.produceState
import app.securify.vpn.SecurifyVpn

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(modifier: Modifier, onApps: () -> Unit, onRules: () -> Unit, onLogs: () -> Unit) {
    val p = Store.state.collectAsState().value.prefs
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var editUrl by rememberSaveable { mutableStateOf(false) }
    var editDns by rememberSaveable { mutableStateOf(false) }
    var geoBusy by remember { mutableStateOf(false) }
    val restart = { SecurifyVpn.restartIfOn() }
    fun updateGeo(src: GeoSource) {
        if (geoBusy) return
        geoBusy = true
        scope.launch {
            // finishes even if the screen goes away, so the files on disk and the saved source stay in sync
            runCatching { withContext(Dispatchers.IO + NonCancellable) { Geo.update(Env.data, src, BuildInfo.VERSION_CODE.toString(), Env::bundled); restart() } }.onFailure {
                if (it !is CancellationException) snack.showSnackbar(str(R.string.geo_failed, it.message ?: it.toString()))
            }
            geoBusy = false
        }
    }
    val xray by produceState("") { value = withContext(Dispatchers.IO) { xrayVersion() } }

    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { ScreenTopBar(stringResource(R.string.tab_settings), scroll) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), modifier = Modifier.padding(pad).centeredContent()) {
            item { Header(R.string.sec_routing) }
            item {
                Item(first = true, title = stringResource(R.string.routing), summary = stringResource(R.string.routing_hint)) {
                    Choice(
                        listOf(stringResource(R.string.routing_all), stringResource(R.string.routing_ru)),
                        if (p.ruDirect) 1 else 0, Modifier.padding(top = 12.dp),
                    ) { i -> Store.prefs { it.copy(ruDirect = i == 1) }; SecurifyVpn.restartIfOn() }
                }
            }
            item {
                val n = listOf(p.proxyRules, p.directRules, p.blockRules).map(::ruleCount)
                Item(
                    title = stringResource(R.string.rules),
                    summary = if (n.sum() == 0) stringResource(R.string.rules_none) else stringResource(R.string.rules_summary, n[0], n[1], n[2]),
                    onClick = onRules,
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                )
            }
            item {
                Item(
                    title = stringResource(R.string.geo),
                    summary = if (p.geo == GeoSource.BUNDLED) stringResource(R.string.geo_bundled_hint)
                    else stringResource(R.string.geo_updated, relativeTime(p.geoUpdated)),
                    trailing = {
                        if (geoBusy) LoadingIndicator(Modifier.size(40.dp))
                        else if (p.geo != GeoSource.BUNDLED) IconButton(onClick = { updateGeo(p.geo) }) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.refresh))
                        }
                    },
                ) {
                    Choice(
                        listOf(stringResource(R.string.geo_bundled), "Loyalsoldier", "runetfreedom"), p.geo.ordinal, Modifier.padding(top = 12.dp),
                    ) { i -> if (i != p.geo.ordinal) updateGeo(GeoSource.entries[i]) }
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

            item { Header(R.string.sec_network) }
            item {
                Item(
                    first = true, title = stringResource(R.string.fragment), summary = stringResource(R.string.fragment_hint),
                    onClick = { Store.prefs { it.copy(fragment = !it.fragment) }; restart() },
                    trailing = { Switch(p.fragment, { v -> Store.prefs { it.copy(fragment = v) }; restart() }) },
                )
            }
            item {
                Item(
                    title = stringResource(R.string.dns), summary = p.dns.ifEmpty { stringResource(R.string.dns_default) },
                    onClick = { editDns = true },
                )
            }
            item {
                Item(
                    title = stringResource(R.string.fakedns), summary = stringResource(R.string.fakedns_hint),
                    onClick = { Store.prefs { it.copy(fakeDns = !it.fakeDns) }; restart() },
                    trailing = { Switch(p.fakeDns, { v -> Store.prefs { it.copy(fakeDns = v) }; restart() }) },
                )
            }
            if (Env.appImage != null) item {
                Item(
                    last = true, title = stringResource(R.string.desktop_menu), summary = stringResource(R.string.desktop_menu_hint),
                    onClick = { if (addToMenu()) scope.launch { snack.showSnackbar(str(R.string.desktop_menu_done)) } },
                    trailing = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
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
                    onClick = { toggleAuto(!p.autoUpdate) },
                    trailing = { Switch(p.autoUpdate, { toggleAuto(it) }) },
                )
            }
            item {
                Item(
                    title = stringResource(R.string.hwid), summary = stringResource(R.string.hwid_hint),
                    onClick = { Store.prefs { it.copy(hwid = !it.hwid) } },
                    trailing = { Switch(p.hwid, { v -> Store.prefs { it.copy(hwid = v) } }) },
                )
            }

            item {
                Item(last = true, title = stringResource(R.string.agent), summary = stringResource(R.string.agent_hint)) {
                    Choice(listOf("Securify", "v2rayNG", "Happ"), p.agent.ordinal, Modifier.padding(top = 12.dp)) { i ->
                        Store.prefs { it.copy(agent = SubAgent.entries[i]) }
                    }
                }
            }

            item { Header(R.string.sec_about) }
            item { Item(first = true, title = "Securify ${BuildInfo.VERSION_NAME}", summary = xray) }
            if (Env.appImage != null) item { UpdateItem() }
            item {
                Item(
                    last = true, title = stringResource(R.string.logs), summary = stringResource(R.string.logs_hint), onClick = onLogs,
                    trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                )
            }
        }
    }

    if (editDns) DnsDialog(p.dns, onDismiss = { editDns = false }) { v ->
        editDns = false
        if (v != p.dns) { Store.prefs { it.copy(dns = v) }; restart() }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateItem() {
    val u by Updater.state.collectAsState()
    Item(
        title = stringResource(R.string.update),
        summary = when (val s = u) {
            UpdateState.Idle -> stringResource(R.string.update_check)
            UpdateState.Checking -> stringResource(R.string.update_checking)
            UpdateState.Latest -> stringResource(R.string.update_latest)
            is UpdateState.Available -> stringResource(R.string.update_available, s.release.version)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.release.version, s.percent)
            is UpdateState.Failed -> stringResource(R.string.update_failed, s.message)
        },
        onClick = {
            when (val s = u) {
                is UpdateState.Available -> Updater.install(s.release)
                is UpdateState.Downloading, UpdateState.Checking -> {}
                else -> Updater.check()
            }
        },
        trailing = { if (u is UpdateState.Checking) LoadingIndicator(Modifier.size(40.dp)) },
    ) {
        (u as? UpdateState.Downloading)?.let { d ->
            LinearWavyProgressIndicator(progress = { d.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
private fun DnsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var dns by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns)) },
        text = {
            Column {
                Text(stringResource(R.string.dns_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    dns, { dns = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    placeholder = { Text("https://1.1.1.1/dns-query") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = dns.trim().none { it.isWhitespace() }, onClick = { onSave(dns.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun toggleAuto(on: Boolean) = Store.prefs { it.copy(autoUpdate = on) }

@Composable
private fun Header(text: String) {
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
