package app.securify.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import app.securify.R
import app.securify.core.Store
import app.securify.core.Traffic
import app.securify.core.VpnState
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(modifier: Modifier, onToggle: () -> Unit, onServers: () -> Unit) {
    val vpn by SecurifyVpn.state.collectAsState()

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
        val height = maxHeight
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ConnectButton(vpn, onToggle, size = min(248.dp, height - 48.dp))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Title()
                    Spacer(Modifier.height(16.dp))
                    Status(vpn)
                    Connection(vpn)
                    Spacer(Modifier.height(20.dp))
                    ServerCard(onServers)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Title(Modifier.padding(top = 20.dp))
                Spacer(Modifier.weight(1f))
                ConnectButton(vpn, onToggle)
                Spacer(Modifier.height(36.dp))
                Status(vpn)
                Connection(vpn)
                Spacer(Modifier.weight(1f))
                ServerCard(onServers)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Title(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Connection(vpn: VpnState) {
    val traffic by SecurifyVpn.traffic.collectAsState()
    val exit by SecurifyVpn.exit.collectAsState()
    AnimatedVisibility(vpn is VpnState.On) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TrafficPill(traffic)
            Text(
                exit ?: stringResource(R.string.exit_detecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ServerCard(onClick: () -> Unit) {
    val app by Store.state.collectAsState()
    val pings by Store.pings.collectAsState()
    val server = app.selected
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (server == null) {
                Icon(Icons.Filled.Add, null, Modifier.padding(horizontal = 10.dp))
                Text(stringResource(R.string.add_first_sub), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            } else {
                ProtocolBadge(server.protocol)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (server.group.isNotEmpty()) stringResource(R.string.auto_hint, server.group.size)
                        else app.subs.firstOrNull { s -> s.servers.any { it.key == server.key } }?.let { subTitle(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                PingLabel(pings[server.key])
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = morph.toPath(progress).asComposePath()
        p.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(p)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectButton(state: VpnState, onClick: () -> Unit, size: Dp = 248.dp) {
    val on = state is VpnState.On
    val busy = state is VpnState.Connecting
    val c = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val progress by animateFloatAsState(if (on) 1f else 0f, motion.slowSpatialSpec())
    val container by animateColorAsState(
        when { on -> c.primary; busy -> c.secondaryContainer; else -> c.surfaceContainerHighest }, motion.slowEffectsSpec(),
    )
    val content by animateColorAsState(
        when { on -> c.onPrimary; busy -> c.onSecondaryContainer; else -> c.onSurfaceVariant }, motion.slowEffectsSpec(),
    )
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(busy, on) {
        while (busy || on) rotation.animateTo(rotation.value + 360f, tween(if (busy) 1400 else 30_000, easing = LinearEasing))
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, motion.fastSpatialSpec())
    val label = stringResource(if (on || busy) R.string.action_disconnect else R.string.action_connect)

    Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = rotation.value
                    shape = MorphShape(morph, progress)
                    clip = true
                }
                .background(container)
                .clickable(interaction, ripple(), onClick = onClick)
                .semantics { role = Role.Button; contentDescription = label },
        )
        AnimatedContent(busy, transitionSpec = { (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) }) { b ->
            if (b) LoadingIndicator(Modifier.size(size * 0.39f), color = content)
            else Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(size * 0.39f), tint = content)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Status(state: VpnState) {
    val title = when (state) {
        VpnState.Off -> R.string.status_off
        VpnState.Connecting -> R.string.status_connecting
        is VpnState.On -> R.string.status_on
        is VpnState.Failed -> R.string.status_failed
    }
    AnimatedContent(title, label = "status") { t ->
        Text(stringResource(t), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
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
    Text(
        sub,
        style = MaterialTheme.typography.bodyLarge,
        color = if (broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 3, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
    )
}

@Composable
private fun TrafficPill(t: Traffic) {
    val ctx = LocalContext.current
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(top = 16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // current speed
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ArrowUpward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.speed, ctx.bytes(t.up)), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.ArrowDownward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.speed, ctx.bytes(t.down)), style = MaterialTheme.typography.labelLarge)
            }
            // used totals below with prefix
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(stringResource(R.string.traffic_used), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowUpward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                    Text(ctx.bytes(t.upTotal), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDownward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    Text(ctx.bytes(t.downTotal), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
