package app.securify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.securify.vpn.SecurifyVpn
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val lines by SecurifyVpn.log.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(lines) { lines.lastIndex.takeIf { it >= 0 }?.let { list.scrollToItem(it) } }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.logs),
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.ChevronBackward, null, Modifier.size(22.dp)) } },
                actions = {
                    IconButton(enabled = lines.isNotEmpty(), onClick = { copyText("log", lines.joinToString("\n")) }) {
                        Icon(MiuixIcons.Copy, stringResource(R.string.copy_log), Modifier.size(20.dp))
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                lines.isEmpty() -> Text(stringResource(R.string.logs_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                else -> SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp)) {
                        items(lines) {
                            Text(
                                it,
                                style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
}