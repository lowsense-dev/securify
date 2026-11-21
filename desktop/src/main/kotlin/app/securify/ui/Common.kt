package app.securify.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.core.PING_PENDING
import app.securify.core.Sub
import app.securify.core.protocolLabel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.qrcode.QRCodeWriter
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.net.URI
import java.util.Locale

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
        CircularProgressIndicator(size = 14.dp, strokeWidth = 1.5.dp)
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

/** A white rounded list that holds Miuix preference rows, divided the way HyperOS lists are. */
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
fun RowDivider(inset: Boolean = true) {
    top.yukonga.miuix.kmp.basic.HorizontalDivider(
        modifier = if (inset) Modifier.padding(start = 16.dp) else Modifier,
    )
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

fun copyText(label: String, text: String, sensitive: Boolean = false) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

fun clipboardText(): String? =
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()

fun clipboardImage(): BufferedImage? = runCatching {
    when (val img = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor)) {
        is BufferedImage -> img
        is java.awt.Image -> BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB).also { it.graphics.drawImage(img, 0, 0, null) }
        else -> null
    }
}.getOrNull()

fun decodeQr(img: BufferedImage): String? = runCatching {
    com.google.zxing.MultiFormatReader().decode(
        com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.client.j2se.BufferedImageLuminanceSource(img))),
        mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true),
    ).text
}.getOrNull()

/** Desktop.browse isn't available on every Linux desktop, xdg-open is. */
fun openUrl(url: String) {
    runCatching { ProcessBuilder("xdg-open", url).start() }.onFailure { runCatching { Desktop.getDesktop().browse(URI(url)) } }
}

/** Like Android's formatShortFileSize: 1000-based units and a decimal below 100. */
fun bytes(b: Long): String {
    val ru = Locale.getDefault().language == "ru"
    val units = if (ru) listOf("Б", "кБ", "МБ", "ГБ", "ТБ") else listOf("B", "kB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
    return (if (i == 0 || v >= 100) "%.0f %s" else "%.1f %s").format(v, units[i])
}

/** Like Android's getRelativeTimeSpanString for the recent past. */
fun relativeTime(ms: Long): String {
    val ru = Locale.getDefault().language == "ru"
    val m = (System.currentTimeMillis() - ms).coerceAtLeast(0) / 60_000
    return when {
        m < 1 -> if (ru) "только что" else "just now"
        m < 60 -> if (ru) "$m мин назад" else "$m min ago"
        m < 24 * 60 -> if (ru) "${m / 60} ч назад" else "${m / 60} h ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))
    }
}

fun segmentShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
    bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
)

@Composable
fun AppDialog(
    title: String? = null,
    summary: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    // the window is a desktop one, so dialogs sit centered instead of sliding up like on a phone
    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        titleColor = MiuixTheme.colorScheme.onSurface,
        largeScreen = true,
        onDismissRequest = onDismiss,
        content = content,
    )
}

@Composable
fun QrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val qr = remember(text) {
        runCatching {
            MatrixToImageWriter.toBufferedImage(
                QRCodeWriter().encode(
                    text, BarcodeFormat.QR_CODE, 900, 900,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 1),
                ),
            ).toComposeImageBitmap()
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
