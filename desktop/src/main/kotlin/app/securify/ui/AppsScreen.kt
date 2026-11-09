package app.securify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.core.AppMode
import app.securify.desktop.desktopApps
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Add
import app.securify.core.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(onBack: () -> Unit) {
    val p = Store.state.collectAsState().value.prefs
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { desktopApps().map { AppEntry(it.process, it.name) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.apps)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).centeredContent().padding(horizontal = 16.dp)) {
            Choice(
                listOf(stringResource(R.string.apps_mode_off), stringResource(R.string.apps_mode_only), stringResource(R.string.apps_mode_bypass)),
                p.appMode.ordinal,
            ) { i -> Store.prefs { it.copy(appMode = AppMode.entries[i]) } }
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 12.dp),
                singleLine = true, shape = CircleShape,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) },
                enabled = p.appMode != AppMode.OFF,
            )
            Text(
                stringResource(R.string.apps_process_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
            )
            val list = apps
            if (list == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else {
                val q = query.trim()
                // processes added by hand show up next to the found apps, picked ones first
                val all = (p.apps.filter { pkg -> list.none { it.pkg == pkg } }.map { AppEntry(it, it) } + list)
                    .sortedBy { it.pkg !in p.apps }
                val shown = all.filter { q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true) }
                val canAdd = q.isNotEmpty() && p.appMode != AppMode.OFF && all.none { it.pkg == q }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (canAdd) item(key = "+add") {
                        Surface(
                            onClick = { Store.prefs { it.copy(apps = it.apps + q) }; query = "" },
                            shape = segmentShape(true, true), color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, null)
                                Text(stringResource(R.string.apps_add) + ": " + q, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
                        val on = a.pkg in p.apps
                        val toggle = { Store.prefs { it.copy(apps = if (on) it.apps - a.pkg else it.apps + a.pkg) } }
                        Surface(
                            onClick = toggle, enabled = p.appMode != AppMode.OFF,
                            shape = segmentShape(i == 0, i == shown.lastIndex),
                            color = if (on && p.appMode != AppMode.OFF) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(a.label)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(on, { toggle() }, enabled = p.appMode != AppMode.OFF)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(label: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}
