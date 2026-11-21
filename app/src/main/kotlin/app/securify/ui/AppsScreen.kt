package app.securify.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.securify.R
import app.securify.core.AppMode
import app.securify.core.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class AppEntry(val pkg: String, val label: String, val system: Boolean)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val p = Store.state.collectAsState().value.prefs
    var query by rememberSaveable { mutableStateOf("") }
    var showUser by rememberSaveable { mutableStateOf(true) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val picked = Store.state.value.prefs.apps
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .filter { it.packageName != ctx.packageName && it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true }
                .map {
                    AppEntry(
                        it.packageName, it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName,
                        (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
                .sortedWith(compareBy<AppEntry> { it.pkg !in picked }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
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
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.apps_user), showUser, p.appMode != AppMode.OFF) { showUser = !showUser }
                FilterPill(stringResource(R.string.apps_system), showSystem, p.appMode != AppMode.OFF) { showSystem = !showSystem }
            }
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), progress = null, strokeWidth = 2.dp)
                }
            } else {
                val q = query.trim()
                // picked apps stay visible, so a filter never hides what is already in the list
                val shown = list.filter { a -> a.pkg in p.apps || (if (a.system) showSystem else showUser) }
                    .filter { q.isEmpty() || it.label.contains(q, true) || it.pkg.contains(q, true) }
                LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
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
                                startAction = { AppIcon(a.pkg) },
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
private fun FilterPill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = MiuixTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (selected) ButtonDefaults.buttonColors(color = c.primary, contentColor = c.onPrimary)
        else ButtonDefaults.buttonColors(color = c.secondaryContainer, contentColor = c.onSurface),
        cornerRadius = 50.dp,
        minWidth = 0.dp,
        minHeight = 32.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(label, style = MiuixTheme.textStyles.footnote1)
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val pm = LocalContext.current.packageManager
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                pkg.substringAfterLast('.').take(1).uppercase(),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onTertiaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}