package app.securify.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.core.AppMode
import app.securify.core.Store
import app.securify.desktop.desktopApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class AppEntry(val pkg: String, val label: String)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val p = Store.state.collectAsState().value.prefs
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { desktopApps().map { AppEntry(it.process, it.name) } }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.apps),
                navigationIcon = { IconButton(onClick = onBack) { Icon(MiuixIcons.ChevronBackward, null, Modifier.size(22.dp)) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).centeredContent().padding(horizontal = 16.dp)) {
            TabRow(
                tabs = listOf(stringResource(R.string.apps_mode_off), stringResource(R.string.apps_mode_only), stringResource(R.string.apps_mode_bypass)),
                selectedTabIndex = p.appMode.ordinal,
                onTabSelected = { i -> Store.prefs { it.copy(appMode = AppMode.entries[i]) } },
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = stringResource(R.string.search),
                useLabelAsPlaceholder = true,
                singleLine = true,
                enabled = p.appMode != AppMode.OFF,
                leadingIcon = { Icon(MiuixIcons.Search, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(MiuixIcons.Clear, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                } else null,
            )

            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), progress = null, strokeWidth = 2.dp)
                }
            } else {
                val q = query.trim()
                // processes added by hand show up next to the found apps, picked ones first
                val all = (p.apps.filter { pkg -> list.none { it.pkg == pkg } }.map { AppEntry(it, it) } + list)
                    .sortedBy { it.pkg !in p.apps }
                val shown = all.filter { q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true) }
                val canAdd = q.isNotEmpty() && p.appMode != AppMode.OFF && all.none { it.pkg == q }
                LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                    if (canAdd) item(key = "+add") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            cornerRadius = 18.dp,
                            insideMargin = PaddingValues(0.dp),
                            onClick = { Store.prefs { it.copy(apps = it.apps + q) }; query = "" },
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(MiuixIcons.Add, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.apps_add) + ": " + q,
                                    Modifier.padding(start = 12.dp),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
                        val on = a.pkg in p.apps
                        val toggle = { Store.prefs { it.copy(apps = if (on) it.apps - a.pkg else it.apps + a.pkg) } }
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(segmentShape(i == 0, i == shown.lastIndex))
                                .background(MiuixTheme.colorScheme.surfaceContainer),
                        ) {
                            CheckboxPreference(
                                title = a.label,
                                summary = a.pkg,
                                checked = on,
                                onCheckedChange = { toggle() },
                                enabled = p.appMode != AppMode.OFF,
                                checkboxLocation = CheckboxLocation.End,
                                startAction = { AppIcon(a.label) },
                            )
                            if (i != shown.lastIndex) RowDivider()
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
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onTertiaryContainer,
        )
    }
}