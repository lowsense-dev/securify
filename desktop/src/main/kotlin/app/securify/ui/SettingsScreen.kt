package app.securify.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.securify.core.AppMode
import app.securify.core.Geo
import app.securify.core.GeoSource
import app.securify.core.PingMethod
import app.securify.core.Store
import app.securify.core.SubAgent
import app.securify.core.UpdateState
import app.securify.core.Updater
import app.securify.desktop.Env
import app.securify.desktop.addToMenu
import app.securify.desktop.xrayVersion
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(modifier: Modifier, onApps: () -> Unit, onRules: () -> Unit, onLogs: () -> Unit) {
    val p = Store.state.collectAsState().value.prefs
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
    val rules = listOf(p.proxyRules, p.directRules, p.blockRules).map(::ruleCount)

    Scaffold(
        modifier = modifier,
        topBar = { ScreenTopBar(stringResource(R.string.tab_settings)) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            modifier = Modifier.padding(pad).centeredContent(),
        ) {
            item { SectionTitle(stringResource(R.string.sec_routing)) }
            item {
                PrefGroup {
                    OverlayDropdownPreference(
                        items = listOf(stringResource(R.string.routing_all), stringResource(R.string.routing_ru)),
                        selectedIndex = if (p.ruDirect) 1 else 0,
                        title = stringResource(R.string.routing),
                        summary = stringResource(R.string.routing_hint),
                        onSelectedIndexChange = { i ->
                            Store.prefs { it.copy(ruDirect = i == 1) }
                            restart()
                        },
                    )
                    RowDivider()
                    ArrowPreference(
                        title = stringResource(R.string.rules),
                        summary = if (rules.sum() == 0) stringResource(R.string.rules_none) else stringResource(R.string.rules_summary, rules[0], rules[1], rules[2]),
                        onClick = onRules,
                    )
                    RowDivider()
                    OverlayDropdownPreference(
                        items = listOf(stringResource(R.string.geo_bundled), "Loyalsoldier", "runetfreedom"),
                        selectedIndex = p.geo.ordinal,
                        title = stringResource(R.string.geo),
                        summary = if (p.geo == GeoSource.BUNDLED) stringResource(R.string.geo_bundled_hint)
                        else stringResource(R.string.geo_updated, relativeTime(p.geoUpdated)),
                        onSelectedIndexChange = { i -> if (i != p.geo.ordinal) updateGeo(GeoSource.entries[i]) },
                    )
                    if (p.geo != GeoSource.BUNDLED) {
                        RowDivider()
                        ArrowPreference(
                            title = stringResource(R.string.refresh),
                            summary = stringResource(R.string.geo_updated, relativeTime(p.geoUpdated)),
                            enabled = !geoBusy,
                            onClick = { updateGeo(p.geo) },
                            endActions = {
                                if (geoBusy) CircularProgressIndicator(Modifier.size(18.dp), progress = null, strokeWidth = 2.dp)
                            },
                        )
                    }
                    RowDivider()
                    ArrowPreference(
                        title = stringResource(R.string.apps),
                        summary = when (p.appMode) {
                            AppMode.OFF -> stringResource(R.string.apps_off)
                            AppMode.ONLY -> stringResource(R.string.apps_only_n, p.apps.size)
                            AppMode.BYPASS -> stringResource(R.string.apps_bypass_n, p.apps.size)
                        },
                        onClick = onApps,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.sec_network)) }
            item {
                PrefGroup {
                    SwitchPreference(
                        checked = p.fragment,
                        onCheckedChange = { v -> Store.prefs { it.copy(fragment = v) }; restart() },
                        title = stringResource(R.string.fragment),
                        summary = stringResource(R.string.fragment_hint),
                    )
                    RowDivider()
                    ArrowPreference(
                        title = stringResource(R.string.dns),
                        summary = p.dns.ifEmpty { stringResource(R.string.dns_default) },
                        onClick = { editDns = true },
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.fakeDns,
                        onCheckedChange = { v -> Store.prefs { it.copy(fakeDns = v) }; restart() },
                        title = stringResource(R.string.fakedns),
                        summary = stringResource(R.string.fakedns_hint),
                    )
                    if (Env.appImage != null) {
                        RowDivider()
                        ArrowPreference(
                            title = stringResource(R.string.desktop_menu),
                            summary = stringResource(R.string.desktop_menu_hint),
                            onClick = { if (addToMenu()) scope.launch { snack.showSnackbar(str(R.string.desktop_menu_done)) } },
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.sec_ping)) }
            item {
                PrefGroup {
                    OverlayDropdownPreference(
                        items = listOf("TCP", "ICMP", stringResource(R.string.ping_real)),
                        selectedIndex = p.ping.ordinal,
                        title = stringResource(R.string.ping_method),
                        summary = stringResource(
                            when (p.ping) {
                                PingMethod.TCP -> R.string.ping_tcp_hint
                                PingMethod.ICMP -> R.string.ping_icmp_hint
                                PingMethod.REAL -> R.string.ping_real_hint
                            },
                        ),
                        onSelectedIndexChange = { i ->
                            Store.prefs { it.copy(ping = PingMethod.entries[i]) }
                            Store.pings.value = emptyMap()
                        },
                    )
                    if (p.ping == PingMethod.REAL) {
                        RowDivider()
                        ArrowPreference(title = stringResource(R.string.ping_url), summary = p.pingUrl, onClick = { editUrl = true })
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.sec_subs)) }
            item {
                PrefGroup {
                    SwitchPreference(
                        checked = p.autoUpdate,
                        onCheckedChange = { toggleAuto(it) },
                        title = stringResource(R.string.auto_update),
                        summary = stringResource(R.string.auto_update_hint),
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.hwid,
                        onCheckedChange = { v -> Store.prefs { it.copy(hwid = v) } },
                        title = stringResource(R.string.hwid),
                        summary = stringResource(R.string.hwid_hint),
                    )
                    RowDivider()
                    OverlayDropdownPreference(
                        items = listOf("Securify", "v2rayNG", "Happ"),
                        selectedIndex = p.agent.ordinal,
                        title = stringResource(R.string.agent),
                        summary = stringResource(R.string.agent_hint),
                        onSelectedIndexChange = { i -> Store.prefs { it.copy(agent = SubAgent.entries[i]) } },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.sec_about)) }
            item {
                PrefGroup {
                    BasicComponent(title = "Securify ${BuildInfo.VERSION_NAME}", summary = xray)
                    if (Env.appImage != null) {
                        RowDivider()
                        UpdateItem()
                    }
                    RowDivider()
                    ArrowPreference(
                        title = stringResource(R.string.logs),
                        summary = stringResource(R.string.logs_hint),
                        onClick = onLogs,
                    )
                }
            }
        }
    }

    if (editDns) DnsDialog(p.dns, onDismiss = { editDns = false }) { v ->
        editDns = false
        if (v != p.dns) { Store.prefs { it.copy(dns = v) }; restart() }
    }

    if (editUrl) {
        var url by rememberSaveable { mutableStateOf(p.pingUrl) }
        AppDialog(
            title = stringResource(R.string.ping_url),
            onDismiss = { editUrl = false },
        ) {
            TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ping_url),
                singleLine = true,
            )
            DialogButtons(
                confirm = stringResource(R.string.save),
                enabled = url.startsWith("http://") || url.startsWith("https://"),
                onConfirm = { Store.prefs { it.copy(pingUrl = url.trim()) }; editUrl = false },
                onDismiss = { editUrl = false },
            )
        }
    }
}

@Composable
private fun UpdateItem() {
    val u by Updater.state.collectAsState()
    BasicComponent(
        title = stringResource(R.string.update),
        summary = when (val s = u) {
            UpdateState.Idle -> stringResource(R.string.update_check)
            UpdateState.Checking -> stringResource(R.string.update_checking)
            UpdateState.Latest -> stringResource(R.string.update_latest)
            is UpdateState.Available -> stringResource(R.string.update_available, s.release.version)
            is UpdateState.Downloading -> stringResource(R.string.update_downloading, s.release.version, s.percent)
            is UpdateState.Failed -> stringResource(R.string.update_failed, s.message)
        },
        endActions = {
            if (u is UpdateState.Checking) CircularProgressIndicator(Modifier.size(18.dp), progress = null, strokeWidth = 2.dp)
        },
        onClick = {
            when (val s = u) {
                is UpdateState.Available -> Updater.install(s.release)
                is UpdateState.Downloading, UpdateState.Checking -> {}
                else -> Updater.check()
            }
        },
        bottomAction = {
            (u as? UpdateState.Downloading)?.let { d ->
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), progress = d.percent / 100f)
            }
        },
    )
}

@Composable
private fun DnsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var dns by rememberSaveable { mutableStateOf(current) }
    AppDialog(
        title = stringResource(R.string.dns),
        summary = stringResource(R.string.dns_hint),
        onDismiss = onDismiss,
    ) {
        TextField(
            value = dns,
            onValueChange = { dns = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.dns),
            singleLine = true,
        )
        DialogButtons(
            confirm = stringResource(R.string.save),
            enabled = dns.trim().none { it.isWhitespace() },
            onConfirm = { onSave(dns.trim()) },
            onDismiss = onDismiss,
        )
    }
}

private fun toggleAuto(on: Boolean) = Store.prefs { it.copy(autoUpdate = on) }