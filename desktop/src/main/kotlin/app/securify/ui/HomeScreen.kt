package app.securify.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.securify.core.Store
import app.securify.core.Traffic
import app.securify.core.VpnState
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(modifier: Modifier, onToggle: () -> Unit, onServers: (() -> Unit)?) {
    val vpn by SecurifyVpn.state.collectAsState()

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.app_name),
            style = MiuixTheme.textStyles.title2,
            modifier = Modifier.padding(top = 20.dp),
        )
        Spacer(Modifier.weight(1f))
        ConnectButton(vpn, onToggle)
        Spacer(Modifier.height(28.dp))
        Status(vpn)
        Spacer(Modifier.weight(1f))
        ServerCard(onServers)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit, size: Dp = 196.dp) {
    val on = state is VpnState.On
    val busy = state is VpnState.Connecting
    val c = MiuixTheme.colorScheme
    val background = when { on -> c.primary; busy -> c.primaryContainer; else -> c.tertiaryContainer }
    val content = when { on -> c.onPrimary; busy -> c.onPrimaryContainer; else -> c.primary }
    val ring by animateFloatAsState(if (on) 0.16f else 0f)
    val label = stringResource(if (on || busy) R.string.action_disconnect else R.string.action_connect)

    Box(Modifier.size(size + 18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.matchParentSize().clip(CircleShape)
                .border(1.dp, c.primary.copy(alpha = ring), CircleShape),
        )
        Box(
            Modifier.size(size).clip(CircleShape).background(background)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(size * 0.28f), progress = null, strokeWidth = 3.dp)
            } else {
                Icon(painterResource("ic_shield.xml"), null, Modifier.size(size * 0.38f), tint = content)
            }
        }
    }
}



@Composable
private fun Status(state: VpnState) {
    val c = MiuixTheme.colorScheme
    val title = when (state) {
        VpnState.Off -> R.string.status_off
        VpnState.Connecting -> R.string.status_connecting
        is VpnState.On -> R.string.status_on
        is VpnState.Failed -> R.string.status_failed
    }
    val online by SecurifyVpn.online.collectAsState()
    val broken = state is VpnState.Failed || (state is VpnState.On && !online)
    val sub = when (state) {
        is VpnState.On -> {
            val now by produceState(System.currentTimeMillis(), state.since) {
                while (true) { value = System.currentTimeMillis(); delay(1000) }
            }
            val s = (now - state.since).coerceAtLeast(0) / 1000
            "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) +
                if (online) "" else " · " + stringResource(R.string.status_no_link)
        }
        is VpnState.Failed -> state.message
        VpnState.Off -> stringResource(R.string.status_off_hint)
        VpnState.Connecting -> ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is VpnState.Connecting) {
                CircularProgressIndicator(Modifier.size(16.dp), progress = null, strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                stringResource(title),
                style = MiuixTheme.textStyles.title3,
                color = if (broken) c.error else c.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MiuixTheme.textStyles.body2,
                color = if (broken) c.error else c.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            )
        }
        if (state is VpnState.On) Connection()
    }
}

@Composable
private fun Connection() {
    val exit by SecurifyVpn.exit.collectAsState()
    Spacer(Modifier.height(12.dp))
    if (!app.securify.desktop.Os.windows) {
        val traffic by SecurifyVpn.traffic.collectAsState()
        TrafficLine(traffic)
    }
    Text(
        exit ?: stringResource(R.string.exit_detecting),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun TrafficLine(t: Traffic) {
    val c = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("↑", style = MiuixTheme.textStyles.footnote1, color = c.primary)
        Text(" ${stringResource(R.string.speed, bytes(t.up))}", style = MiuixTheme.textStyles.footnote1)
        Spacer(Modifier.size(12.dp))
        Text("↓", style = MiuixTheme.textStyles.footnote1, color = c.primary)
        Text(" ${stringResource(R.string.speed, bytes(t.down))}", style = MiuixTheme.textStyles.footnote1)
    }
    Text(
        stringResource(R.string.traffic_used) + "  ↑ ${bytes(t.upTotal)}   ↓ ${bytes(t.downTotal)}",
        style = MiuixTheme.textStyles.footnote2,
        color = c.onSurfaceVariantSummary,
    )
}

@Composable
private fun ServerCard(onClick: (() -> Unit)?) {
    val app by Store.state.collectAsState()
    val pings by Store.pings.collectAsState()
    val server = app.selected
    val content = @Composable {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (server == null) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MiuixTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(MiuixIcons.Add, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary) }
                Text(
                    stringResource(R.string.add_first_sub),
                    Modifier.weight(1f).padding(start = 14.dp),
                    style = MiuixTheme.textStyles.body1,
                )
            } else {
                ProtocolBadge(server.protocol)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(server.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (server.group.isNotEmpty()) stringResource(R.string.auto_hint, server.group.size)
                        else app.subs.firstOrNull { s -> s.servers.any { it.key == server.key } }?.let { subTitle(it) }.orEmpty(),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                PingLabel(pings[server.key])
            }
            if (onClick != null) {
                Icon(
                    MiuixIcons.ChevronForward, null, Modifier.padding(start = 10.dp).size(16.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        cornerRadius = 18.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        onClick = onClick,
    ) { content() }
}
