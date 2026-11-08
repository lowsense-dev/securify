package app.securify.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                    IconButton(enabled = !lines.isNullOrEmpty(), onClick = { shareText(ctx, lines.orEmpty().joinToString("\n")) }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.share))
                    }
                },
            )
        },
    ) { pad ->
        val l = lines
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                l == null -> LoadingIndicator()
                l.isEmpty() -> Text(stringResource(R.string.logs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp)) {
                        items(l) { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
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
