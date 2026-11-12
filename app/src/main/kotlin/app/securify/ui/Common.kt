package app.securify.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.securify.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.BarcodeEncoder
import app.securify.core.MANUAL_ID
import app.securify.core.PING_PENDING
import app.securify.core.Sub
import app.securify.core.protocolLabel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProtocolBadge(protocol: String, selected: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(44.dp)
            .clip((if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Cookie4Sided).toShape())
            .background(if (selected) c.primary else c.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val color = if (selected) c.onPrimary else c.onTertiaryContainer
        if (protocol == "auto") Icon(Icons.Filled.Bolt, null, tint = color)
        else Text(protocolLabel(protocol).take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PingLabel(ms: Long?) {
    if (ms == null) return
    if (ms == PING_PENDING) return LoadingIndicator(Modifier.size(28.dp))
    val c = MaterialTheme.colorScheme
    Text(
        if (ms < 0) stringResource(R.string.ping_fail) else stringResource(R.string.ping_ms, ms),
        style = MaterialTheme.typography.labelLarge,
        color = when { ms < 0 -> c.error; ms < 300 -> c.primary; ms < 800 -> c.tertiary; else -> c.error },
    )
}

@Composable
fun subTitle(sub: Sub) = if (sub.id == MANUAL_ID) stringResource(R.string.manual_group) else sub.name

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScreenTopBar(
    title: String,
    scroll: TopAppBarScrollBehavior,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    when {
        !isLandscape() -> LargeFlexibleTopAppBar(
            title = { Text(title) }, subtitle = subtitle?.let { { Text(it) } }, actions = actions, scrollBehavior = scroll,
        )
        subtitle != null -> TopAppBar(title = { Text(title) }, subtitle = { Text(subtitle) }, actions = actions, scrollBehavior = scroll)
        else -> TopAppBar(title = { Text(title) }, actions = actions, scrollBehavior = scroll)
    }
}

fun Modifier.centeredContent() = fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)

fun copyText(ctx: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

fun segmentShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 20.dp else 4.dp, topEnd = if (first) 20.dp else 4.dp,
    bottomStart = if (last) 20.dp else 4.dp, bottomEnd = if (last) 20.dp else 4.dp,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Choice(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun QrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val qr = remember(text) {
        runCatching {
            BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, 900, 900,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1)).asImageBitmap()
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            if (qr == null) Text(stringResource(R.string.qr_too_long))
            else Image(
                qr, null,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
