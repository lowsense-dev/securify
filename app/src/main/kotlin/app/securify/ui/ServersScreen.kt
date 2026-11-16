package app.securify.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.R
import app.securify.core.Prefs
import app.securify.core.Server
import app.securify.core.SortMode
import app.securify.core.fuzzyMatch
import app.securify.core.Store
import app.securify.core.Sub
import app.securify.core.SubAgent
import app.securify.core.Subs
import app.securify.core.MANUAL_ID
import app.securify.core.PING_PENDING
import app.securify.core.pingAll
import app.securify.core.protocolLabel
import app.securify.core.stopPing
import app.securify.vpn.SecurifyVpn
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServersScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val app by Store.state.collectAsState()
    val pings by Store.pings.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var renameSub by remember { mutableStateOf<Sub?>(null) }
    var settingsSub by remember { mutableStateOf<Sub?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    val subs = app.subs
    val wanted = subs.indexOfFirst { it.id == pageId }.takeIf { it >= 0 }
        ?: subs.indexOfFirst { s -> s.auto?.key == app.selected?.key || s.servers.any { it.key == app.selected?.key } }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = wanted) { subs.size }
    val latestSubs by rememberUpdatedState(subs)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { i -> latestSubs.getOrNull(i)?.let { pageId = it.id } } }
    LaunchedEffect(wanted) { if (wanted < pager.pageCount && pager.currentPage != wanted) pager.scrollToPage(wanted) }
    val page = subs.getOrNull(pager.currentPage)
    val list = page?.let { sortServers(it.servers, app.prefs.sort, pings) }.orEmpty().filter { fuzzyMatch(it.name, searchQuery) }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val landscape = isLandscape()

    fun runImport(text: String, hwid: Boolean? = null, agent: SubAgent? = null, customHwid: String = "") {
        scope.launch {
            importing = true
            val msg = runCatching { withContext(Dispatchers.IO) { Subs.import(text, hwid, agent, customHwid) } }.fold(
                onSuccess = {
                    when (it) {
                        is Subs.Imported.Subscription -> { pageId = it.id; ctx.getString(R.string.imported_sub, it.name, it.servers) }
                        is Subs.Imported.Servers -> { pageId = MANUAL_ID; ctx.getString(R.string.imported_servers, it.count) }
                        Subs.Imported.Nothing -> ctx.getString(R.string.import_nothing)
                    }
                },
                onFailure = { ctx.getString(R.string.import_failed, it.message ?: it.toString()) },
            )
            importing = false
            snack.showSnackbar(msg)
        }
    }

    fun import(text: String?) {
        if (text.isNullOrBlank()) return
        if (Subs.subscriptionUrls(text).isEmpty()) runImport(text) else importText = text
    }

    fun refresh(subs: List<Sub>) {
        scope.launch {
            refreshing = refreshing + subs.map { it.id }
            val failed = subs.firstNotNullOfOrNull { s ->
                runCatching { withContext(Dispatchers.IO) { Subs.refresh(s) } }.exceptionOrNull()?.let { s to it }
            }
            refreshing = refreshing - subs.map { it.id }.toSet()
            failed?.let { (s, e) -> snack.showSnackbar(ctx.getString(R.string.refresh_failed, s.title, e.message ?: e.toString())) }
        }
    }

    val qr = rememberLauncherForActivityResult(ScanContract()) { import(it.contents) }

    BackHandler(fabOpen || searching) {
        if (searching) { searching = false; searchQuery = "" } else fabOpen = false
    }

    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.tab_servers),
                subtitle = if (searching) null else pluralStringResource(R.plurals.servers, list.size, list.size),
                titleContent = if (searching) {
                    { ServerSearchField(searchQuery, { searchQuery = it }, searchFocus) }
                } else null,
                scroll = scroll,
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; searchQuery = "" }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.close))
                        }
                    } else {
                        IconButton(enabled = list.isNotEmpty(), onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, stringResource(R.string.search_servers))
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort)) }
                            DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                                listOf(SortMode.ORIGIN to R.string.sort_origin, SortMode.NAME to R.string.sort_name, SortMode.PING to R.string.sort_ping)
                                    .forEach { (mode, label) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(label)) },
                                            leadingIcon = { if (app.prefs.sort == mode) Icon(Icons.Filled.Check, null) },
                                            onClick = { Store.prefs { it.copy(sort = mode) }; sortMenu = false },
                                        )
                                    }
                            }
                        }
                        if (pings.containsValue(PING_PENDING)) IconButton(onClick = { stopPing() }) {
                            Icon(Icons.Filled.Stop, stringResource(R.string.ping_stop))
                        }
                        else IconButton(
                            enabled = list.isNotEmpty(),
                            onClick = { pingAll(list, app.prefs.ping, app.prefs.pingUrl) },
                        ) { Icon(Icons.Filled.Speed, stringResource(R.string.ping_all)) }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabOpen,
                button = {
                    ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }) {
                        val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                        if (importing && !fabOpen) LoadingIndicator(Modifier.size(28.dp))
                        else Icon(rememberVectorPainter(icon), stringResource(R.string.add), Modifier.animateIcon({ checkedProgress }))
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; import(clipboardText(ctx)) },
                    icon = { Icon(Icons.Filled.ContentPaste, null) },
                    text = { Text(stringResource(R.string.import_clipboard)) },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabOpen = false
                        qr.launch(
                            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(ctx.getString(R.string.qr_prompt))
                                .setBeepEnabled(false).setOrientationLocked(false),
                        )
                    },
                    icon = { Icon(Icons.Filled.QrCodeScanner, null) },
                    text = { Text(stringResource(R.string.import_qr)) },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; manual = true },
                    icon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text(stringResource(R.string.import_manual)) },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (subs.size > 1) SubTabs(subs, page?.id) { id ->
                scope.launch { pager.animateScrollToPage(subs.indexOfFirst { it.id == id }) }
            }
            if (subs.isEmpty()) EmptyState()
            else HorizontalPager(pager, Modifier.weight(1f), key = { subs.getOrNull(it)?.id ?: it }) { index ->
                val sub = subs.getOrNull(index) ?: return@HorizontalPager
                val servers = sortServers(sub.servers, app.prefs.sort, pings).filter { fuzzyMatch(it.name, searchQuery) }
                val pull = rememberPullToRefreshState()
                val pulling = sub.id in refreshing
                PullToRefreshBox(
                    isRefreshing = pulling,
                    onRefresh = { if (sub.url.isNotEmpty()) refresh(listOf(sub)) },
                    state = pull,
                    modifier = Modifier.fillMaxSize(),
                    indicator = { PullToRefreshDefaults.LoadingIndicator(state = pull, isRefreshing = pulling, modifier = Modifier.align(Alignment.TopCenter)) },
                ) {
                    LazyColumn(Modifier.centeredContent(), contentPadding = PaddingValues(start = 16.dp, end = if (landscape) 104.dp else 16.dp, bottom = 120.dp)) {
                        item(key = "sub:" + sub.id) {
                            SubPanel(
                                sub,
                                refreshing = sub.id in refreshing,
                                onRefresh = { refresh(listOf(sub)) },
                                onDelete = {
                                    Store.removeSub(sub.id)
                                    scope.launch {
                                        val r = snack.showSnackbar(ctx.getString(R.string.sub_deleted, sub.title.ifEmpty { ctx.getString(R.string.manual_group) }), ctx.getString(R.string.undo))
                                        if (r == SnackbarResult.ActionPerformed) Store.putSub(sub)
                                    }
                                },
                                onRename = { renameSub = it },
                                onSettings = { settingsSub = it },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        sub.auto?.let { a ->
                            item(key = a.key) {
                                ServerRow(
                                    a, first = true, last = true, selected = a.key == app.selected?.key, ping = null,
                                    modifier = Modifier.animateItem().padding(bottom = 6.dp),
                                ) {
                                    if (a.key != app.selected?.key) {
                                        Store.prefs { it.copy(selected = a.key) }
                                        SecurifyVpn.restartIfOn(ctx)
                                    }
                                }
                            }
                        }
                        itemsIndexed(servers, key = { _, s -> sub.id + "/" + s.key }) { i, s ->
                            ServerRow(
                                s, first = i == 0, last = i == servers.lastIndex,
                                selected = s.key == app.selected?.key, ping = pings[s.key],
                                modifier = Modifier.animateItem(),
                            ) {
                                if (s.key != app.selected?.key) {
                                    Store.prefs { it.copy(selected = s.key) }
                                    SecurifyVpn.restartIfOn(ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (manual) ManualDialog(onDismiss = { manual = false }) { manual = false; import(it) }

    renameSub?.let { sub ->
        var name by remember(sub.id) { mutableStateOf(sub.title) }
        AlertDialog(
            onDismissRequest = { renameSub = null },
            title = { Text(stringResource(R.string.rename_subscription)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(sub.name.ifEmpty { stringResource(R.string.manual_group) }) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.putSub(sub.renamed(name))
                    renameSub = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameSub = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    importText?.let { text ->
        val existing = Subs.subscriptionUrls(text).firstNotNullOfOrNull { u -> app.subs.find { it.url == u } }
        SubSettingsDialog(
            title = stringResource(R.string.add_subscription),
            initial = existing,
            prefs = app.prefs,
            isNew = true,
            onDismiss = { importText = null },
            onSave = { hwid, agent, customHwid ->
                importText = null
                runImport(text, hwid, agent, customHwid)
            },
        )
    }

    settingsSub?.let { sub ->
        SubSettingsDialog(
            title = stringResource(R.string.sub_settings),
            initial = sub,
            prefs = app.prefs,
            isNew = false,
            onDismiss = { settingsSub = null },
            onSave = { hwid, agent, customHwid ->
                val updated = sub.copy(hwid = hwid, agent = agent, customHwid = customHwid)
                Store.putSub(updated)
                settingsSub = null
                val changed = updated.hwid != sub.hwid || updated.agent != sub.agent || updated.customHwid != sub.customHwid
                if (changed && updated.url.isNotEmpty()) refresh(listOf(updated))
            },
        )
    }
}

@Composable
private fun <T> SettingPicker(label: String, options: List<Pair<T, String>>, value: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { open = true }) {
                Text(options.firstOrNull { it.first == value }?.second.orEmpty())
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                options.forEach { (option, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        leadingIcon = { if (option == value) Icon(Icons.Filled.Check, null) },
                        onClick = { open = false; onSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubSettingsDialog(
    title: String,
    initial: Sub?,
    prefs: Prefs,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean?, SubAgent?, String) -> Unit,
) {
    val hwidOptions = listOf<Pair<Boolean?, String>>(
        null to stringResource(R.string.follow_app),
        true to stringResource(R.string.hwid_on),
        false to stringResource(R.string.hwid_off),
    )
    val agentOptions = listOf<Pair<SubAgent?, String>>(
        null to stringResource(R.string.follow_app),
        SubAgent.SECURIFY to "Securify",
        SubAgent.V2RAYNG to "v2rayNG",
        SubAgent.HAPP to "Happ",
    )
    var hwid by remember(initial?.id) { mutableStateOf(initial?.hwid) }
    var agent by remember(initial?.id) { mutableStateOf(initial?.agent) }
    var custom by remember(initial?.id) { mutableStateOf(initial?.customHwid ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SettingPicker(stringResource(R.string.hwid), hwidOptions, hwid) { hwid = it }
                Spacer(Modifier.height(16.dp))
                SettingPicker(stringResource(R.string.agent), agentOptions, agent) { agent = it }
                if (hwid ?: prefs.hwid) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.custom_hwid)) },
                        placeholder = { Text(stringResource(R.string.custom_hwid_auto)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.custom_hwid_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hwid, agent, custom.trim()) }) {
                Text(stringResource(if (isNew) R.string.add else R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SubTabs(subs: List<Sub>, current: String?, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var order by remember(subs) { mutableStateOf(subs) }
    val rowState = rememberLazyListState()
    LaunchedEffect(current) {
        val i = order.indexOfFirst { it.id == current }
        val info = rowState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == i }
        if (i >= 0 && (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset)) rowState.animateScrollToItem(i)
    }
    val reorder = rememberReorderableLazyListState(rowState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(order, key = { it.id }) { s ->
            ReorderableItem(reorder, key = s.id) { dragging ->
                val scale by animateFloatAsState(if (dragging) 1.08f else 1f)
                ToggleButton(
                    checked = s.id == current,
                    onCheckedChange = { onSelect(s.id) },
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .longPressDraggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { Store.reorderSubs(order.map { it.id }) },
                        ),
                ) {
                    Text(subTitle(s), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                }
            }
        }
    }
}

private fun clipboardText(ctx: Context): String? =
    ctx.getSystemService(ClipboardManager::class.java).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()

@Composable
private fun ServerSearchField(query: String, onQueryChange: (String) -> Unit, focus: FocusRequester) {
    val style = MaterialTheme.typography.titleLarge
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        singleLine = true,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { defaultKeyboardAction(ImeAction.Search) }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(stringResource(R.string.search_servers), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                innerTextField()
            }
        },
    )
}

fun sortServers(list: List<Server>, sort: SortMode, pings: Map<String, Long>): List<Server> = when (sort) {
    SortMode.ORIGIN -> list
    SortMode.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.PING -> list.sortedBy { s -> pings[s.key]?.takeIf { it >= 0 } ?: Long.MAX_VALUE }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(MaterialShapes.Cookie7Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Dns, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp))
        Text(
            stringResource(R.string.empty_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubPanel(sub: Sub, refreshing: Boolean, onRefresh: () -> Unit, onDelete: () -> Unit, onRename: (Sub) -> Unit, onSettings: (Sub) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val info = sub.info
    var menu by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    if (qr) QrDialog(subTitle(sub), sub.url) { qr = false }
    Surface(
        shape = RoundedCornerShape(28.dp), color = c.secondaryContainer,
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 6.dp)) {
                    Text(subTitle(sub), style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val updated = if (sub.updatedAt > 0) " · " + DateUtils.getRelativeTimeSpanString(sub.updatedAt) else ""
                    Text(
                        pluralStringResource(R.plurals.servers, sub.servers.size, sub.servers.size) + updated,
                        style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
                if (sub.url.isNotEmpty()) {
                    IconButton(enabled = !refreshing, onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        info.supportUrl?.let { url ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.sub_support)) }, onClick = { menu = false; open(ctx, url) })
                        }
                        info.webPageUrl?.let { url ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.sub_web)) }, onClick = { menu = false; open(ctx, url) })
                        }
                        if (sub.url.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = { menu = false; copyText(ctx, sub.title, sub.url, sensitive = true) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = { menu = false; shareText(ctx, sub.url) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_qr)) },
                                leadingIcon = { Icon(Icons.Filled.QrCode, null) },
                                onClick = { menu = false; qr = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_hwid)) },
                                leadingIcon = { Icon(Icons.Filled.Fingerprint, null) },
                                onClick = { menu = false; copyText(ctx, "HWID", Subs.hwid(sub.url, sub.customHwid)) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { menu = false; onRename(sub) }
                        )
                        if (sub.url.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sub_settings)) },
                                onClick = { menu = false; onSettings(sub) }
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                info.announce?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                }
                val used = info.upload + info.download
                if (info.total > 0) {
                    LinearWavyProgressIndicator(
                        progress = { (used.toFloat() / info.total).coerceIn(0f, 1f) },
                        color = c.primary, trackColor = c.onSecondaryContainer.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(
                            stringResource(R.string.traffic_left, ctx.bytes((info.total - used).coerceAtLeast(0))),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.traffic_of, ctx.bytes(used), ctx.bytes(info.total)),
                            style = MaterialTheme.typography.labelMedium, color = c.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                } else if (sub.url.isNotEmpty() && used > 0) {
                    Text(
                        stringResource(R.string.traffic_unlimited, ctx.bytes(used)),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (info.expire > 0) {
                    val ms = info.expire * 1000
                    val days = ((ms - System.currentTimeMillis()) / DateUtils.DAY_IN_MILLIS).toInt()
                    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
                    Text(
                        if (ms < System.currentTimeMillis()) stringResource(R.string.expired, date)
                        else stringResource(R.string.expires, date) + " · " + pluralStringResource(R.plurals.days_left, days, days),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (days < 3) c.error else c.onSecondaryContainer,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun open(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun ServerRow(
    s: Server, first: Boolean, last: Boolean, selected: Boolean, ping: Long?,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val shape = segmentShape(first, last)
    val auto = s.group.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    if (qr) QrDialog(s.name, s.key) { qr = false }
    Box(modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Surface(
            shape = shape,
            color = if (selected) c.primaryContainer else c.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(shape)
                .combinedClickable(onLongClick = if (auto) null else ({ menu = true }), onClick = onClick),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(s.protocol, selected)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        if (auto) stringResource(R.string.auto) else s.name,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (auto) stringResource(R.string.auto_hint, s.group.size)
                        else listOf(protocolLabel(s.protocol), s.transport, s.security).filter { it.isNotEmpty() && it != "none" }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    )
                }
                PingLabel(ping)
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menu = false; copyText(ctx, s.name, s.key, sensitive = true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { menu = false; shareText(ctx, s.key) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_qr)) },
                leadingIcon = { Icon(Icons.Filled.QrCode, null) },
                onClick = { menu = false; qr = true },
            )
        }
    }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, null) },
        title = { Text(stringResource(R.string.import_manual)) },
        text = {
            Column {
                Text(stringResource(R.string.manual_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 8,
                    placeholder = { Text("https://… / vless://…") })
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
