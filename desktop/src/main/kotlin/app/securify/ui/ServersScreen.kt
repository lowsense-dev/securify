package app.securify.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.core.MANUAL_ID
import app.securify.core.PING_PENDING
import app.securify.core.Prefs
import app.securify.core.Server
import app.securify.core.SortMode
import app.securify.core.Store
import app.securify.core.Sub
import app.securify.core.SubAgent
import app.securify.core.Subs
import app.securify.core.fuzzyMatch
import app.securify.core.pingAll
import app.securify.core.protocolLabel
import app.securify.core.stopPing
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.awt.FileDialog
import java.awt.Frame
import java.text.DateFormat
import java.util.Date
import javax.imageio.ImageIO

@Composable
fun ServersScreen(modifier: Modifier) {
    val app by Store.state.collectAsState()
    val pings by Store.pings.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
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

    fun runImport(text: String, hwid: Boolean? = null, agent: SubAgent? = null, customHwid: String = "") {
        scope.launch {
            importing = true
            val msg = runCatching { withContext(Dispatchers.IO) { Subs.import(text, hwid, agent, customHwid) } }.fold(
                onSuccess = {
                    when (it) {
                        is Subs.Imported.Subscription -> { pageId = it.id; str(R.string.imported_sub, it.name, it.servers) }
                        is Subs.Imported.Servers -> { pageId = MANUAL_ID; str(R.string.imported_servers, it.count) }
                        Subs.Imported.Nothing -> str(R.string.import_nothing)
                    }
                },
                onFailure = { str(R.string.import_failed, it.message ?: it.toString()) },
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
            failed?.let { (s, e) -> snack.showSnackbar(str(R.string.refresh_failed, s.title, e.message ?: e.toString())) }
        }
    }

    fun importQrFile() {
        val dialog = FileDialog(null as Frame?, str(R.string.import_qr_image), FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg", "bmp", "gif", "webp") }
        dialog.isVisible = true
        val file = dialog.files.firstOrNull() ?: return
        val text = runCatching { ImageIO.read(file)?.let(::decodeQr) }.getOrNull()
        if (text != null) import(text) else scope.launch { snack.showSnackbar(str(R.string.qr_not_found)) }
    }

    fun pingAllServers(sub: Sub) {
        pingAll(sortServers(sub.servers, app.prefs.sort, pings), app.prefs.ping, app.prefs.pingUrl)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.tab_servers),
                subtitle = if (searching) null else pluralStringResource(R.plurals.servers, list.size, list.size),
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; searchQuery = "" }) {
                            Icon(MiuixIcons.Clear, stringResource(R.string.close), Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(enabled = list.isNotEmpty(), onClick = { searching = true }) {
                            Icon(MiuixIcons.Search, stringResource(R.string.search_servers), Modifier.size(20.dp))
                        }
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(MiuixIcons.Sort, stringResource(R.string.sort), Modifier.size(20.dp))
                        }
                        if (pings.containsValue(PING_PENDING)) {
                            IconButton(onClick = { stopPing() }) {
                                Icon(MiuixIcons.Pause, stringResource(R.string.ping_stop), Modifier.size(20.dp))
                            }
                        }
                        if (importing) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(18.dp), progress = null, strokeWidth = 2.dp)
                            }
                        } else {
                            IconButton(onClick = { addOpen = true }) {
                                Icon(MiuixIcons.Add, stringResource(R.string.add), Modifier.size(22.dp))
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (searching) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(searchFocus),
                    label = stringResource(R.string.search_servers),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    leadingIcon = { Icon(MiuixIcons.Search, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) },
                    trailingIcon = {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(MiuixIcons.Clear, null, Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    },
                )
            }
            if (subs.size > 1) SubTabs(subs, page?.id) { id ->
                scope.launch { pager.animateScrollToPage(subs.indexOfFirst { it.id == id }) }
            }
            if (subs.isEmpty()) {
                EmptyState(onAdd = { addOpen = true })
            } else {
                HorizontalPager(pager, Modifier.weight(1f), key = { subs.getOrNull(it)?.id ?: it }) { index ->
                    val sub = subs.getOrNull(index) ?: return@HorizontalPager
                    val servers = sortServers(sub.servers, app.prefs.sort, pings).filter { fuzzyMatch(it.name, searchQuery) }
                    LazyColumn(
                        Modifier.centeredContent(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    ) {
                        item(key = "sub:" + sub.id) {
                            SubPanel(
                                sub,
                                refreshing = sub.id in refreshing,
                                onRefresh = { refresh(listOf(sub)) },
                                onPing = { pingAllServers(sub) },
                                onDelete = {
                                    Store.removeSub(sub.id)
                                    scope.launch {
                                        val r = snack.showSnackbar(
                                            str(R.string.sub_deleted, sub.title.ifEmpty { str(R.string.manual_group) }),
                                            str(R.string.undo),
                                        )
                                        if (r == SnackbarResult.ActionPerformed) Store.putSub(sub)
                                    }
                                },
                                onSettings = { settingsSub = it },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        sub.auto?.let { a ->
                            item(key = a.key) {
                                ServerRow(
                                    a, first = true, last = true, selected = a.key == app.selected?.key, ping = null,
                                    modifier = Modifier.animateItem().padding(bottom = 8.dp),
                                ) {
                                    if (a.key != app.selected?.key) {
                                        Store.prefs { it.copy(selected = a.key) }
                                        SecurifyVpn.restartIfOn()
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
                                    SecurifyVpn.restartIfOn()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddDialog(
            onDismiss = { addOpen = false },
            onClipboard = { addOpen = false; import(clipboardText()?.takeIf { it.isNotBlank() } ?: clipboardImage()?.let(::decodeQr)) },
            onQrImage = { addOpen = false; importQrFile() },
            onManual = { addOpen = false; manual = true },
        )
    }

    if (sortOpen) {
        SortDialog(
            current = app.prefs.sort,
            onDismiss = { sortOpen = false },
            onSelect = { mode -> Store.prefs { it.copy(sort = mode) }; sortOpen = false },
        )
    }

    if (manual) ManualDialog(onDismiss = { manual = false }) { manual = false; import(it) }

    importText?.let { text ->
        val existing = Subs.subscriptionUrls(text).firstNotNullOfOrNull { u -> app.subs.find { it.url == u } }
        SubSettingsDialog(
            title = stringResource(R.string.add_subscription),
            initial = existing,
            prefs = app.prefs,
            isNew = true,
            onDismiss = { importText = null },
            onSave = { _, _, hwid, agent, customHwid ->
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
            onSave = { name, url, hwid, agent, customHwid ->
                val updated = sub.renamed(name).copy(url = url, hwid = hwid, agent = agent, customHwid = customHwid)
                Store.putSub(updated)
                settingsSub = null
                val changed = updated.url != sub.url || updated.hwid != sub.hwid || updated.agent != sub.agent || updated.customHwid != sub.customHwid
                if (changed && updated.url.isNotEmpty()) refresh(listOf(updated))
            },
        )
    }
}

@Composable
private fun SubSettingsDialog(
    title: String,
    initial: Sub?,
    prefs: Prefs,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean?, SubAgent?, String) -> Unit,
) {
    val hwidOptions = listOf(
        stringResource(R.string.follow_app),
        stringResource(R.string.hwid_on),
        stringResource(R.string.hwid_off),
    )
    val agentOptions = listOf(
        stringResource(R.string.follow_app),
        "Securify", "v2rayNG", "Happ",
    )
    var name by remember(initial?.id) { mutableStateOf(initial?.title ?: "") }
    var url by remember(initial?.id) { mutableStateOf(initial?.url ?: "") }
    var hwid by remember(initial?.id) { mutableStateOf(initial?.hwid) }
    var agent by remember(initial?.id) { mutableStateOf(initial?.agent) }
    var custom by remember(initial?.id) { mutableStateOf(initial?.customHwid ?: "") }
    val validUrl = initial?.url?.isNotEmpty() != true || url.trim().let { it.startsWith("http://") || it.startsWith("https://") }

    AppDialog(
        title = title,
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (!isNew && initial != null) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.sub_name),
                    singleLine = true,
                )
                if (initial.url.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.sub_url),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            PrefGroup {
                OverlayDropdownPreference(
                    items = hwidOptions,
                    selectedIndex = when (hwid) { null -> 0; true -> 1; false -> 2 },
                    title = stringResource(R.string.hwid),
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { i -> hwid = when (i) { 0 -> null; 1 -> true; else -> false } },
                )
                RowDivider()
                OverlayDropdownPreference(
                    items = agentOptions,
                    selectedIndex = agent?.let { it.ordinal + 1 } ?: 0,
                    title = stringResource(R.string.agent),
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { i -> agent = if (i == 0) null else SubAgent.entries[i - 1] },
                )
            }
            if (hwid ?: prefs.hwid) {
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = custom,
                    onValueChange = { custom = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.custom_hwid),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.custom_hwid_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
        }
        DialogButtons(
            confirm = stringResource(if (isNew) R.string.add else R.string.save),
            enabled = validUrl,
            onConfirm = { onSave(name, url.trim(), hwid, agent, custom.trim()) },
            onDismiss = onDismiss,
        )
    }
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(order, key = { it.id }) { s ->
            ReorderableItem(reorder, key = s.id) { dragging ->
                val scale by animateFloatAsState(if (dragging) 1.05f else 1f)
                val selected = s.id == current
                val colors = if (selected) ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary, contentColor = MiuixTheme.colorScheme.onPrimary)
                else ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.secondaryContainer, contentColor = MiuixTheme.colorScheme.onSurface)
                Button(
                    onClick = { onSelect(s.id) },
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .longPressDraggableHandle(
                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragStopped = { Store.reorderSubs(order.map { it.id }) },
                        ),
                    colors = colors,
                    cornerRadius = 50.dp,
                    minWidth = 0.dp,
                    minHeight = 34.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(subTitle(s), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp), style = MiuixTheme.textStyles.footnote1)
                }
            }
        }
    }
}

fun sortServers(list: List<Server>, sort: SortMode, pings: Map<String, Long>): List<Server> = when (sort) {
    SortMode.ORIGIN -> list
    SortMode.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    SortMode.PING -> list.sortedBy { s -> pings[s.key]?.takeIf { it >= 0 } ?: Long.MAX_VALUE }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MiuixTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(MiuixIcons.Layers, null, Modifier.size(36.dp), tint = MiuixTheme.colorScheme.primary) }
        Text(stringResource(R.string.empty_title), style = MiuixTheme.textStyles.title4, modifier = Modifier.padding(top = 20.dp))
        Text(
            stringResource(R.string.empty_hint),
            style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
        Button(
            onClick = onAdd,
            modifier = Modifier.padding(top = 20.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Icon(MiuixIcons.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.add_subscription), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun SubPanel(
    sub: Sub,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onSettings: (Sub) -> Unit,
    modifier: Modifier,
) {
    val c = MiuixTheme.colorScheme
    val info = sub.info
    var menu by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    if (qr) QrDialog(subTitle(sub), sub.url) { qr = false }
    if (menu) {
        SubMenuDialog(
            sub = sub,
            onDismiss = { menu = false },
            onQr = { qr = true },
            onPing = onPing,
            onSettings = { onSettings(sub) },
            onDelete = onDelete,
        )
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subTitle(sub), style = MiuixTheme.textStyles.title4, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val updated = if (sub.updatedAt > 0) " · " + relativeTime(sub.updatedAt) else ""
                Text(
                    pluralStringResource(R.plurals.servers, sub.servers.size, sub.servers.size) + updated,
                    style = MiuixTheme.textStyles.footnote1, color = c.onSurfaceVariantSummary,
                )
            }
            if (sub.url.isNotEmpty()) {
                if (refreshing) Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), progress = null, strokeWidth = 2.dp)
                } else IconButton(onClick = onRefresh) {
                    Icon(MiuixIcons.Refresh, stringResource(R.string.refresh), Modifier.size(20.dp))
                }
            }
            IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, null, Modifier.size(20.dp)) }
        }
        info.announce?.let {
            Text(
                it,
                style = MiuixTheme.textStyles.footnote1,
                color = c.onSurfaceVariantSummary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val used = info.upload + info.download
        if (info.total > 0) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                progress = (used.toFloat() / info.total).coerceIn(0f, 1f),
                height = 4.dp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.traffic_left, bytes((info.total - used).coerceAtLeast(0))),
                    style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.traffic_of, bytes(used), bytes(info.total)),
                    style = MiuixTheme.textStyles.footnote2, color = c.onSurfaceVariantSummary,
                )
            }
        } else if (sub.url.isNotEmpty() && used > 0) {
            Text(
                stringResource(R.string.traffic_unlimited, bytes(used)),
                style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (info.expire > 0) {
            val ms = info.expire * 1000
            val days = ((ms - System.currentTimeMillis()) / 86_400_000L).toInt()
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
            Text(
                if (ms < System.currentTimeMillis()) stringResource(R.string.expired, date)
                else stringResource(R.string.expires, date) + " · " + pluralStringResource(R.plurals.days_left, days, days),
                style = MiuixTheme.textStyles.footnote1,
                color = if (days < 3) c.error else c.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SubMenuDialog(
    sub: Sub,
    onDismiss: () -> Unit,
    onQr: () -> Unit,
    onPing: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = sub.info
    val c = MiuixTheme.colorScheme
    fun act(f: () -> Unit) {
        onDismiss()
        f()
    }
    AppDialog(title = subTitle(sub), onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            PrefGroup {
                info.supportUrl?.let { url ->
                    BasicComponent(title = stringResource(R.string.sub_support), startAction = { ActionIcon(MiuixIcons.Help) }, onClick = { act { openUrl(url) } })
                    RowDivider()
                }
                info.webPageUrl?.let { url ->
                    BasicComponent(title = stringResource(R.string.sub_web), startAction = { ActionIcon(MiuixIcons.Link) }, onClick = { act { openUrl(url) } })
                    RowDivider()
                }
                if (sub.url.isNotEmpty()) {
                    BasicComponent(
                        title = stringResource(R.string.copy_link),
                        startAction = { ActionIcon(MiuixIcons.Copy) },
                        onClick = { act { copyText(sub.title, sub.url, sensitive = true) } },
                    )
                    RowDivider()
                    BasicComponent(
                        title = stringResource(R.string.show_qr),
                        startAction = { ActionIcon(MiuixIcons.Scan) },
                        onClick = { act(onQr) },
                    )
                    RowDivider()
                    BasicComponent(
                        title = stringResource(R.string.copy_hwid),
                        startAction = { ActionIcon(MiuixIcons.Lock) },
                        onClick = { act { copyText("HWID", Subs.hwid(sub.url, sub.customHwid)) } },
                    )
                    RowDivider()
                }
                BasicComponent(
                    title = stringResource(R.string.ping_all),
                    startAction = { ActionIcon(MiuixIcons.Stopwatch) },
                    onClick = { act(onPing) },
                )
                RowDivider()
                BasicComponent(
                    title = stringResource(R.string.sub_settings),
                    startAction = { ActionIcon(MiuixIcons.Settings) },
                    onClick = { act(onSettings) },
                )
                RowDivider()
                BasicComponent(
                    title = stringResource(R.string.delete),
                    titleColor = BasicComponentDefaults.titleColor(color = c.error),
                    startAction = { ActionIcon(MiuixIcons.Delete, c.error) },
                    onClick = { act(onDelete) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(stringResource(R.string.close), onDismiss)
        }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface) {
    Icon(icon, null, Modifier.size(20.dp), tint = tint)
}

@Composable
private fun ServerRow(
    s: Server, first: Boolean, last: Boolean, selected: Boolean, ping: Long?,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = MiuixTheme.colorScheme
    val shape = segmentShape(first, last)
    val auto = s.group.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    if (qr) QrDialog(s.name, s.key) { qr = false }
    if (menu) {
        AppDialog(title = s.name, onDismiss = { menu = false }) {
            PrefGroup {
                BasicComponent(
                    title = stringResource(R.string.copy_link),
                    startAction = { ActionIcon(MiuixIcons.Copy) },
                    onClick = { menu = false; copyText(s.name, s.key, sensitive = true) },
                )
                RowDivider()
                BasicComponent(
                    title = stringResource(R.string.show_qr),
                    startAction = { ActionIcon(MiuixIcons.Scan) },
                    onClick = { menu = false; qr = true },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(stringResource(R.string.close), { menu = false })
            }
        }
    }
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.tertiaryContainer else c.surfaceContainer)
            .combinedClickable(onLongClick = if (auto) null else ({ menu = true }), onClick = onClick),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(s.protocol, selected)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        if (auto) stringResource(R.string.auto) else s.name,
                        style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (auto) stringResource(R.string.auto_hint, s.group.size)
                        else listOf(protocolLabel(s.protocol), s.transport, s.security).filter { it.isNotEmpty() && it != "none" }.joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1, color = c.onSurfaceVariantSummary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                PingLabel(ping)
            }
            if (!last) top.yukonga.miuix.kmp.basic.HorizontalDivider(
                Modifier.padding(start = 14.dp),
                color = c.dividerLine,
            )
        }
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onClipboard: () -> Unit, onQrImage: () -> Unit, onManual: () -> Unit) {
    AppDialog(title = stringResource(R.string.add_subscription), onDismiss = onDismiss) {
        PrefGroup {
            BasicComponent(
                title = stringResource(R.string.import_clipboard),
                startAction = { ActionIcon(MiuixIcons.Paste) },
                onClick = onClipboard,
            )
            RowDivider()
            BasicComponent(
                title = stringResource(R.string.import_qr_image),
                startAction = { ActionIcon(MiuixIcons.Scan) },
                onClick = onQrImage,
            )
            RowDivider()
            BasicComponent(
                title = stringResource(R.string.import_manual),
                startAction = { ActionIcon(MiuixIcons.Edit) },
                onClick = onManual,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(stringResource(R.string.cancel), onDismiss)
        }
    }
}

@Composable
private fun SortDialog(current: SortMode, onDismiss: () -> Unit, onSelect: (SortMode) -> Unit) {
    AppDialog(title = stringResource(R.string.sort), onDismiss = onDismiss) {
        PrefGroup {
            listOf(
                SortMode.ORIGIN to R.string.sort_origin,
                SortMode.NAME to R.string.sort_name,
                SortMode.PING to R.string.sort_ping,
            ).forEachIndexed { i, (mode, label) ->
                if (i > 0) RowDivider()
                RadioButtonPreference(
                    title = stringResource(label),
                    selected = mode == current,
                    onClick = { onSelect(mode) },
                    radioButtonLocation = RadioButtonLocation.End,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
            TextButton(stringResource(R.string.cancel), onDismiss)
        }
    }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AppDialog(
        title = stringResource(R.string.import_manual),
        summary = stringResource(R.string.manual_hint),
        onDismiss = onDismiss,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = "https://… / vless://…",
            minLines = 3,
            maxLines = 8,
        )
        DialogButtons(
            confirm = stringResource(R.string.add),
            enabled = text.isNotBlank(),
            onConfirm = { onAdd(text) },
            onDismiss = onDismiss,
        )
    }
}