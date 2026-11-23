package app.securify.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.securify.BuildConfig
import app.securify.R
import app.securify.core.AppMode
import app.securify.core.Geo
import app.securify.core.GeoSource
import app.securify.core.PingMethod
import app.securify.core.Store
import app.securify.core.SubAgent
import app.securify.core.SubUpdateJob
import app.securify.core.UpdateState
import app.securify.core.Updater
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsScreen(modifier: Modifier, onApps: () -> Unit, onRules: () -> Unit, onLogs: () -> Unit) {
    val ctx = LocalContext.current
    val p = Store.state.collectAsState().value.prefs
    val scope = rememberCoroutineScope()
    var editUrl by rememberSaveable { mutableStateOf(false) }
    var editDns by rememberSaveable { mutableStateOf(false) }
    var geoBusy by remember { mutableStateOf(false) }
    val restart = { SecurifyVpn.restartIfOn(ctx) }
    fun updateGeo(src: GeoSource) {
        if (geoBusy) return
        geoBusy = true
        scope.launch {
            // finishes even if the screen goes away, so the files on disk and the saved source stay in sync
            runCatching { withContext(Dispatchers.IO + NonCancellable) { Geo.update(ctx.filesDir, src, BuildConfig.VERSION_CODE.toString()) { ctx.assets.open(it) }; restart() } }.onFailure {
                if (it !is CancellationException) Toast.makeText(ctx, ctx.getString(R.string.geo_failed, it.message ?: it.toString()), Toast.LENGTH_LONG).show()
            }
            geoBusy = false
        }
    }
    val xray = remember { runCatching { Libv2ray.checkVersionX() }.getOrDefault("") }
    val rules = listOf(p.proxyRules, p.directRules, p.blockRules).map(::ruleCount)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = { ScreenTopBar(stringResource(R.string.tab_settings)) },
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
                        else stringResource(R.string.geo_updated, DateUtils.getRelativeTimeSpanString(p.geoUpdated)),
                        onSelectedIndexChange = { i -> if (i != p.geo.ordinal) updateGeo(GeoSource.entries[i]) },
                    )
                    if (p.geo != GeoSource.BUNDLED) {
                        RowDivider()
                        ArrowPreference(
                            title = stringResource(R.string.refresh),
                            summary = stringResource(R.string.geo_updated, DateUtils.getRelativeTimeSpanString(p.geoUpdated)),
                            enabled = !geoBusy,
                            onClick = { updateGeo(p.geo) },
                            endActions = {
                                if (geoBusy) CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
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
                    RowDivider()
                    ArrowPreference(
                        title = stringResource(R.string.always_on),
                        summary = stringResource(R.string.always_on_hint),
                        onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.sec_notification)) }
            item {
                PrefGroup {
                    SwitchPreference(
                        checked = p.notifServer,
                        onCheckedChange = { v -> Store.prefs { it.copy(notifServer = v) } },
                        title = stringResource(R.string.notif_server),
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.notifSub,
                        onCheckedChange = { v -> Store.prefs { it.copy(notifSub = v) } },
                        title = stringResource(R.string.notif_sub),
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.notifSpeed,
                        onCheckedChange = { v -> Store.prefs { it.copy(notifSpeed = v) } },
                        title = stringResource(R.string.notif_speed),
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.notifTraffic,
                        onCheckedChange = { v -> Store.prefs { it.copy(notifTraffic = v) } },
                        title = stringResource(R.string.notif_traffic),
                    )
                    RowDivider()
                    SwitchPreference(
                        checked = p.notifTime,
                        onCheckedChange = { v -> Store.prefs { it.copy(notifTime = v) } },
                        title = stringResource(R.string.notif_time),
                    )
                    if (Build.VERSION.SDK_INT >= 36) {
                        RowDivider()
                        LiveUpdatesRow()
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
                        onCheckedChange = { toggleAuto(ctx, it) },
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
                    BasicComponent(title = "Securify ${BuildConfig.VERSION_NAME}", summary = xray)
                    RowDivider()
                    UpdateItem()
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
        AppDialog(title = stringResource(R.string.ping_url), onDismiss = { editUrl = false }) {
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
    val ctx = LocalContext.current
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
            if (u is UpdateState.Checking) CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
        },
        onClick = {
            when (val s = u) {
                is UpdateState.Available -> if (ctx.packageManager.canRequestPackageInstalls()) Updater.install(s.release)
                else {
                    Toast.makeText(ctx, R.string.update_allow, Toast.LENGTH_LONG).show()
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
                    }
                }
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

private fun toggleAuto(ctx: android.content.Context, on: Boolean) {
    Store.prefs { it.copy(autoUpdate = on) }
    SubUpdateJob.schedule(ctx, on)
}

@Composable
private fun LiveUpdatesRow() {
    if (Build.VERSION.SDK_INT < 36) return
    val ctx = LocalContext.current
    val nm = remember(ctx) { ctx.getSystemService(NotificationManager::class.java) }
    var allowed by remember { mutableStateOf(nm.canPostPromotedNotifications()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, nm) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) allowed = nm.canPostPromotedNotifications()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    ArrowPreference(
        title = stringResource(R.string.live_updates),
        summary = stringResource(if (allowed) R.string.live_updates_on else R.string.live_updates_off),
        onClick = {
            val promo = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            val appNotif = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            runCatching { ctx.startActivity(promo) }.onFailure { runCatching { ctx.startActivity(appNotif) } }
        },
    )
}
