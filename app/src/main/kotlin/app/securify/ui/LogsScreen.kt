package app.securify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.securify.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    LaunchedEffect(reload) {
        lines = withContext(Dispatchers.IO) { readLog() }
        lines?.lastIndex?.takeIf { it >= 0 }?.let { list.scrollToItem(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.logs),
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.ChevronBackward, null, Modifier.size(22.dp)) } },
                actions = {
                    IconButton(onClick = { reload++ }) {
                        Icon(MiuixIcons.Refresh, stringResource(R.string.refresh), Modifier.size(20.dp))
                    }
                    IconButton(enabled = !lines.isNullOrEmpty(), onClick = { shareText(ctx, lines.orEmpty().joinToString("\n")) }) {
                        Icon(MiuixIcons.Share, stringResource(R.string.share), Modifier.size(20.dp))
                    }
                },
            )
        },
    ) { pad ->
        val l = lines
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                l == null -> CircularProgressIndicator(size = 28.dp, strokeWidth = 2.dp)
                l.isEmpty() -> Text(stringResource(R.string.logs_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                else -> SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp)) {
                        items(l) {
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

private val prefix = Regex("""^(\S+ \S+) [VDIWEF]/GoLog\s*\(\s*\d+\):\s?""")

/** gomobile sends the core's stdout and stderr to logcat as GoLog, and an app may read its own log lines. */
private fun readLog(): List<String> = runCatching {
    val p = ProcessBuilder("logcat", "-d", "-t", "1000", "-v", "time", "-s", "GoLog").redirectErrorStream(true).start()
    p.inputStream.bufferedReader().use { r -> r.readLines() }.also { p.waitFor() }
        .filter { !it.startsWith("---------") }.map { it.replace(prefix, "$1  ") }
}.getOrDefault(emptyList())
