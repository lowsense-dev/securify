package app.securify.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securify.core.Server
import app.securify.core.SortMode
import app.securify.core.Store
import app.securify.core.Sub
import app.securify.core.Subs
import app.securify.core.MANUAL_ID
import app.securify.core.PING_PENDING
import app.securify.core.pingAll
import app.securify.core.stopPing
import app.securify.vpn.SecurifyVpn
import kotlinx.coroutines.Dispatchers
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.awt.FileDialog
import java.awt.Frame
import javax.imageio.ImageIO
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
/** [split]: shown as a pane next to the home screen, so the list fills it with even margins. */
fun ServersScreen(modifier: Modifier, split: Boolean = false) {
    val app by Store.state.collectAsState()
    val pings by Store.pings.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    val subs = app.subs
    val wanted = subs.indexOfFirst { it.id == pageId }.takeIf { it >= 0 }
        ?: subs.indexOfFirst { s -> s.auto?.key == app.selected?.key || s.servers.any { it.key == app.selected?.key } }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = wanted) { subs.size }
    val latestSubs by rememberUpdatedState(subs)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { i -> latestSubs.getOrNull(i)?.let { pageId = it.id } } }
    LaunchedEffect(wanted) { if (wanted < pager.pageCount && pager.currentPage != wanted) pager.scrollToPage(wanted) }
    val page = subs.getOrNull(pager.currentPage)
    val list = page?.let { sortServers(it.servers, app.prefs.sort, pings) }.orEmpty()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val landscape = isLandscape()

    fun import(text: String?) {
        if (text.isNullOrBlank()) return
        scope.launch {
            importing = true
            val msg = runCatching { withContext(Dispatchers.IO) { Subs.import(text) } }.fold(
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

    fun refresh(subs: List<Sub>) {
        scope.launch {
            refreshing = refreshing + subs.map { it.id }
            val failed = subs.firstNotNullOfOrNull { s ->
                runCatching { withContext(Dispatchers.IO) { Subs.refresh(s) } }.exceptionOrNull()?.let { s to it }
            }
            refreshing = refreshing - subs.map { it.id }.toSet()
            failed?.let { (s, e) -> snack.showSnackbar(str(R.string.refresh_failed, s.name, e.message ?: e.toString())) }
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


    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.tab_servers),
                subtitle = pluralStringResource(R.plurals.servers, list.size, list.size),
                scroll = scroll,
                actions = {
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
                    onClick = { fabOpen = false; import(clipboardText()?.takeIf { it.isNotBlank() } ?: clipboardImage()?.let(::decodeQr)) },
                    icon = { Icon(Icons.Filled.ContentPaste, null) },
                    text = { Text(stringResource(R.string.import_clipboard)) },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabOpen = false
                        importQrFile()
                    },
                    icon = { Icon(Icons.Filled.QrCodeScanner, null) },
                    text = { Text(stringResource(R.string.import_qr_image)) },
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
                val servers = sortServers(sub.servers, app.prefs.sort, pings)
                val pull = rememberPullToRefreshState()
                val pulling = sub.id in refreshing
                PullToRefreshBox(
                    isRefreshing = pulling,
                    onRefresh = { if (sub.url.isNotEmpty()) refresh(listOf(sub)) },
                    state = pull,
                    modifier = Modifier.fillMaxSize(),
                    indicator = { PullToRefreshDefaults.LoadingIndicator(state = pull, isRefreshing = pulling, modifier = Modifier.align(Alignment.TopCenter)) },
                ) {
                    LazyColumn(
                        if (split) Modifier.fillMaxSize() else Modifier.centeredContent(),
                        contentPadding = PaddingValues(start = 16.dp, end = if (landscape && !split) 104.dp else 16.dp, bottom = 120.dp),
                    ) {
                        item(key = "sub:" + sub.id) {
                            SubPanel(
                                sub,
                                refreshing = sub.id in refreshing,
                                onRefresh = { refresh(listOf(sub)) },
                                onDelete = {
                                    Store.removeSub(sub.id)
                                    scope.launch {
                                        val r = snack.showSnackbar(str(R.string.sub_deleted, sub.name.ifEmpty { str(R.string.manual_group) }), str(R.string.undo))
                                        if (r == SnackbarResult.ActionPerformed) Store.putSub(sub)
                                    }
                                },
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

    if (manual) ManualDialog(onDismiss = { manual = false }) { manual = false; import(it) }
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
private fun SubPanel(sub: Sub, refreshing: Boolean, onRefresh: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
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
                    val updated = if (sub.updatedAt > 0) " · " + relativeTime(sub.updatedAt) else ""
                    Text(
                        pluralStringResource(R.plurals.servers, sub.servers.size, sub.servers.size) + updated,
                        style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer.copy(alpha = 0.75f),
                    )
                }
                if (sub.url.isNotEmpty()) {
                    if (refreshing) LoadingIndicator(Modifier.size(48.dp))
                    else IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        info.supportUrl?.let { url ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.sub_support)) }, onClick = { menu = false; openUrl(url) })
                        }
                        info.webPageUrl?.let { url ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.sub_web)) }, onClick = { menu = false; openUrl(url) })
                        }
                        if (sub.url.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = { menu = false; copyText(sub.name, sub.url, sensitive = true) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_qr)) },
                                leadingIcon = { Icon(Icons.Filled.QrCode, null) },
                                onClick = { menu = false; qr = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_hwid)) },
                                leadingIcon = { Icon(Icons.Filled.Fingerprint, null) },
                                onClick = { menu = false; copyText("HWID", Subs.hwid(sub.url)) },
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
                            stringResource(R.string.traffic_left, bytes((info.total - used).coerceAtLeast(0))),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.traffic_of, bytes(used), bytes(info.total)),
                            style = MaterialTheme.typography.labelMedium, color = c.onSecondaryContainer.copy(alpha = 0.75f),
                        )
                    }
                } else if (sub.url.isNotEmpty() && used > 0) {
                    Text(
                        stringResource(R.string.traffic_unlimited, bytes(used)),
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (info.expire > 0) {
                    val ms = info.expire * 1000
                    val days = ((ms - System.currentTimeMillis()) / 86_400_000L).toInt()
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


@Composable
private fun ServerRow(
    s: Server, first: Boolean, last: Boolean, selected: Boolean, ping: Long?,
    modifier: Modifier, onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
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
                onClick = { menu = false; copyText(s.name, s.key, sensitive = true) },
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
