package app.securify.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.securify.R
import app.securify.core.PING_PENDING
import app.securify.core.Sub
import app.securify.core.protocolLabel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.BarcodeEncoder
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProtocolBadge(protocol: String, selected: Boolean = false) {
    val c = MiuixTheme.colorScheme
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primary else c.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (protocol == "auto") "A" else protocolLabel(protocol).take(2).uppercase(),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) c.onPrimary else c.onTertiaryContainer,
        )
    }
}

@Composable
fun PingLabel(ms: Long?) {
    if (ms == null) return
    if (ms == PING_PENDING) {
        CircularProgressIndicator(Modifier.size(14.dp), progress = null, strokeWidth = 1.5.dp)
        return
    }
    val c = MiuixTheme.colorScheme
    Text(
        if (ms < 0) stringResource(R.string.ping_fail) else stringResource(R.string.ping_ms, ms),
        style = MiuixTheme.textStyles.footnote1,
        color = when { ms < 0 -> c.error; ms < 300 -> c.primary; ms < 800 -> c.onSurfaceVariantSummary; else -> c.error },
    )
}

@Composable
fun subTitle(sub: Sub) = sub.title.ifEmpty { stringResource(R.string.manual_group) }

@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SmallTopAppBar(
        title = title,
        subtitle = subtitle.orEmpty(),
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        defaultWindowInsetsPadding = false,
    )
}

fun Modifier.centeredContent() = fillMaxSize().wrapContentWidth().widthIn(max = 720.dp)

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** A list block that holds Miuix preference rows, divided the way HyperOS settings are. */
@Composable
fun PrefGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
    ) {
        content()
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

@Composable
fun DialogButtons(
    confirm: String,
    onConfirm: () -> Unit,
    enabled: Boolean = true,
    dismiss: String = stringResource(R.string.cancel),
    onDismiss: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(dismiss, onDismiss)
        Spacer(Modifier.width(8.dp))
        TextButton(confirm, onConfirm, enabled = enabled, colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

/** Centered on tablets, a sheet on phones: Miuix picks the presentation from the window size. */
@Composable
fun AppDialog(
    title: String? = null,
    summary: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        titleColor = MiuixTheme.colorScheme.onSurface,
        onDismissRequest = onDismiss,
        content = content,
    )
}

fun copyText(ctx: Context, label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

fun clipboardText(ctx: Context): String? =
    ctx.getSystemService(ClipboardManager::class.java).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()

fun segmentShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
    bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
)

@Composable
fun QrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val qr = remember(text) {
        runCatching {
            BarcodeEncoder().encodeBitmap(
                text, BarcodeFormat.QR_CODE, 900, 900,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1),
            ).asImageBitmap()
        }.getOrNull()
    }
    AppDialog(title = title, onDismiss = onDismiss) {
        if (qr == null) {
            Text(stringResource(R.string.qr_too_long), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            Image(
                qr, null,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(stringResource(R.string.close), onDismiss)
        }
    }
}