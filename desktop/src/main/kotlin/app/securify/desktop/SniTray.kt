@file:Suppress("FunctionName")

package app.securify.desktop

import app.securify.ui.R
import app.securify.ui.str
import app.securify.vpn.SecurifyVpn
import app.securify.vpn.VpnState
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewIcon(path: String) : DBusSignal(path)
    class NewToolTip(path: String) : DBusSignal(path)
}

@DBusInterfaceName("com.canonical.dbusmenu")
@JvmSuppressWildcards
interface DBusMenu : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): Reply2<UInt32, MenuLayout>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<ItemProps>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<MenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    fun AboutToShowGroup(ids: List<Int>): Reply2<List<Int>, List<Int>>

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) : DBusSignal(path, revision, parent)
}

class IconPixmap(@field:Position(0) @JvmField val width: Int, @field:Position(1) @JvmField val height: Int, @field:Position(2) @JvmField val data: ByteArray) : Struct()

class ToolTip(
    @field:Position(0) @JvmField val icon: String,
    @field:Position(1) @JvmField val pixmaps: List<IconPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val text: String,
) : Struct()

class MenuLayout(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val props: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/** dbus-java reads a multi-value reply's types from the type arguments, so the reply class must be generic. */
class Reply2<A, B>(@field:Position(0) @JvmField val first: A, @field:Position(1) @JvmField val second: B) : Tuple()

class ItemProps(@field:Position(0) @JvmField val id: Int, @field:Position(1) @JvmField val props: Map<String, Variant<*>>) : Struct()

class MenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()


/**
 * A tray icon over D-Bus (StatusNotifierItem with a dbusmenu): what Wayland bars, KDE, and GNOME with the AppIndicator
 * extension show. AWT's tray only reaches X11 trays.
 */
class SniTray(private val onOpen: () -> Unit, private val onQuit: () -> Unit) {
    private lateinit var conn: DBusConnection
    private val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
    @Volatile private var active = false
    @Volatile private var status = str(R.string.status_off)
    @Volatile private var revision = 1L
    private val iconOn = listOf(pixmap(32, 1f), pixmap(64, 1f))
    private val iconOff = listOf(pixmap(32, 0.45f), pixmap(64, 0.45f))

    /** False when no tray host runs on the session bus. */
    fun start(): Boolean = runCatching {
        conn = DBusConnectionBuilder.forSessionBus().build()
        conn.requestBusName(busName)
        conn.exportObject(ITEM, item)
        conn.exportObject(MENU, menu)
        // bars come and go (a reload restarts them): register again with each new watcher
        conn.addSigHandler(DBus.NameOwnerChanged::class.java) { s ->
            if (s.name == WATCHER && s.newOwner.isNotEmpty()) runCatching { register() }
        }
        register()
    }.onFailure {
        System.err.println("No D-Bus tray: $it")
        if (::conn.isInitialized) runCatching { conn.close() }
    }.isSuccess

    private fun register() =
        conn.getRemoteObject(WATCHER, "/StatusNotifierWatcher", StatusNotifierWatcher::class.java).RegisterStatusNotifierItem(busName)

    fun update(state: VpnState) {
        val on = state is VpnState.On || state is VpnState.Connecting
        status = when (state) {
            is VpnState.On -> str(R.string.status_on) + " · " + state.server
            VpnState.Connecting -> str(R.string.status_connecting)
            is VpnState.Failed -> str(R.string.status_failed)
            VpnState.Off -> str(R.string.status_off)
        }
        val iconChanged = on != active
        active = on
        revision++
        runCatching {
            if (iconChanged) conn.sendMessage(StatusNotifierItem.NewIcon(ITEM))
            conn.sendMessage(StatusNotifierItem.NewToolTip(ITEM))
            conn.sendMessage(DBusMenu.LayoutUpdated(MENU, UInt32(revision), 0))
        }
    }

    private val item = object : StatusNotifierItem, Properties {
        override fun getObjectPath() = ITEM
        override fun Activate(x: Int, y: Int) = onOpen()
        override fun SecondaryActivate(x: Int, y: Int) = onOpen()
        override fun ContextMenu(x: Int, y: Int) {}
        override fun Scroll(delta: Int, orientation: String) {}

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
            val icon = if (active) iconOn else iconOff
            return mapOf(
                "Category" to Variant("ApplicationStatus"),
                "Id" to Variant("securify"),
                "Title" to Variant("Securify"),
                "Status" to Variant("Active"),
                "WindowId" to Variant(0),
                "IconName" to Variant(""),
                "IconPixmap" to Variant(icon, "a(iiay)"),
                "OverlayIconName" to Variant(""),
                "OverlayIconPixmap" to Variant(emptyList<IconPixmap>(), "a(iiay)"),
                "AttentionIconName" to Variant(""),
                "AttentionIconPixmap" to Variant(emptyList<IconPixmap>(), "a(iiay)"),
                "AttentionMovieName" to Variant(""),
                "ToolTip" to Variant(ToolTip("", icon, "Securify", status), "(sa(iiay)ss)"),
                "ItemIsMenu" to Variant(false),
                "Menu" to Variant(DBusPath(MENU)),
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (GetAll(interfaceName)[propertyName] ?: throw DBusExecutionException("No such property: $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = throw DBusExecutionException("Read-only")
    }

    private fun items() = listOf(
        1 to mapOf<String, Variant<*>>("label" to Variant(str(R.string.tray_open))),
        2 to mapOf<String, Variant<*>>("label" to Variant(str(if (active) R.string.action_disconnect else R.string.action_connect))),
        3 to mapOf<String, Variant<*>>("type" to Variant("separator")),
        4 to mapOf<String, Variant<*>>("label" to Variant(str(R.string.tray_quit))),
    )

    private val menu = object : DBusMenu, Properties {
        override fun getObjectPath() = MENU

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): Reply2<UInt32, MenuLayout> {
            val items = items()
            val node = if (parentId == 0) MenuLayout(
                0, mapOf("children-display" to Variant("submenu")),
                if (recursionDepth == 0) emptyList() else items.map { (id, p) -> Variant(MenuLayout(id, p, emptyList()), "(ia{sv}av)") },
            ) else MenuLayout(parentId, items.toMap()[parentId] ?: emptyMap(), emptyList())
            return Reply2(UInt32(revision), node)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>) =
            items().filter { ids.isEmpty() || it.first in ids }.map { (id, p) -> ItemProps(id, p) }

        override fun GetProperty(id: Int, name: String): Variant<*> =
            items().toMap()[id]?.get(name) ?: throw DBusExecutionException("No such property: $name")

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            when (id) {
                1 -> onOpen()
                2 -> if (SecurifyVpn.isActive) SecurifyVpn.stop() else SecurifyVpn.start()
                4 -> onQuit()
            }
        }

        override fun EventGroup(events: List<MenuEvent>): List<Int> {
            events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
            return emptyList()
        }

        override fun AboutToShow(id: Int) = false
        override fun AboutToShowGroup(ids: List<Int>) = Reply2<List<Int>, List<Int>>(emptyList(), emptyList())

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (GetAll(interfaceName)[propertyName] ?: throw DBusExecutionException("No such property: $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = throw DBusExecutionException("Read-only")
    }

    /** The app icon as ARGB32 in network byte order, dimmed while disconnected. Plain Skia: Compose graphics aren't up yet. */
    private fun pixmap(size: Int, alpha: Float): IconPixmap {
        val svg = SVGDOM(Data.makeFromBytes(SniTray::class.java.getResourceAsStream("/securify.svg")!!.use { it.readBytes() }))
        svg.root?.apply { width = SVGLength(size.toFloat()); height = SVGLength(size.toFloat()) }
        val surface = Surface.makeRasterN32Premul(size, size)
        surface.canvas.saveLayer(null, Paint().setAlphaf(alpha))
        svg.render(surface.canvas)
        surface.canvas.restore()
        val info = ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bmp = Bitmap().apply { allocPixels(info) }
        surface.readPixels(bmp, 0, 0)
        val bgra = bmp.readPixels(info, info.minRowBytes, 0, 0)!!
        val argb = ByteArray(bgra.size)
        for (k in bgra.indices step 4) {
            argb[k] = bgra[k + 3]
            argb[k + 1] = bgra[k + 2]
            argb[k + 2] = bgra[k + 1]
            argb[k + 3] = bgra[k]
        }
        return IconPixmap(size, size, argb)
    }

    private companion object {
        const val WATCHER = "org.kde.StatusNotifierWatcher"
        const val ITEM = "/StatusNotifierItem"
        const val MENU = "/MenuBar"
    }
}
