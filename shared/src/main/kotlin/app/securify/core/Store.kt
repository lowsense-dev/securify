package app.securify.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

data class SubInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val intervalHours: Int = 0,
    val announce: String? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
)

data class Sub(
    val id: String,
    val url: String,
    val name: String,
    val entries: List<String>,
    val info: SubInfo = SubInfo(),
    val updatedAt: Long = 0,
    val customHwid: String = "",
) {
    val servers: List<Server> by lazy { entries.mapNotNull(::parseEntry) }

    /** Balances between the subscription's link servers; JSON configs can't be merged into one config. */
    val auto: Server? by lazy {
        servers.filter { it.config == null && !it.needsPin }.takeIf { it.size > 1 }?.let { g ->
            Server(AUTO_PREFIX + id, listOf(autoLabel, name).filter { it.isNotEmpty() }.joinToString(" · "),
                "auto", "", 0, "", "", JSONObject(), group = g)
        }
    }
}

const val AUTO_PREFIX = "auto:"

/** Set from resources at startup, the core has no Context. */
var autoLabel = "Auto"

enum class PingMethod { TCP, ICMP, REAL }
enum class SortMode { ORIGIN, NAME, PING }
enum class AppMode { OFF, ONLY, BYPASS }
enum class SubAgent { SECURIFY, V2RAYNG, HAPP }

data class Prefs(
    val selected: String? = null,
    val ruDirect: Boolean = false,
    val appMode: AppMode = AppMode.OFF,
    val apps: Set<String> = emptySet(),
    val ping: PingMethod = PingMethod.TCP,
    val pingUrl: String = "https://www.gstatic.com/generate_204",
    val sort: SortMode = SortMode.ORIGIN,
    val hwid: Boolean = true,
    val autoUpdate: Boolean = true,
    val fragment: Boolean = false,
    val proxyRules: String = "",
    val directRules: String = "",
    val blockRules: String = "",
    val dns: String = "",
    val fakeDns: Boolean = false,
    val agent: SubAgent = SubAgent.SECURIFY,
    val geo: GeoSource = GeoSource.BUNDLED,
    val geoUpdated: Long = 0,
)

data class AppState(val subs: List<Sub> = emptyList(), val prefs: Prefs = Prefs()) {
    val servers get() = subs.flatMap { it.servers }

    val selected: Server? get() = prefs.selected.let { k ->
        if (k?.startsWith(AUTO_PREFIX) == true) subs.firstOrNull { AUTO_PREFIX + it.id == k }?.auto
        else servers.firstOrNull { it.key == k }
    } ?: servers.firstOrNull()
}

const val MANUAL_ID = "manual"

object Store {
    val state = MutableStateFlow(AppState())

    val pings = MutableStateFlow<Map<String, Long>>(emptyMap())

    private lateinit var file: File
    private val io = Executors.newSingleThreadExecutor()

    fun init(dir: File) {
        file = File(dir, "state.json")
        if (file.exists()) runCatching { state.value = decode(JSONObject(file.readText())) }
    }

    fun update(f: (AppState) -> AppState) {
        val s = state.updateAndGet(f)
        io.execute {
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { fos ->
                fos.write(encode(s).toString().toByteArray())
                fos.fd.sync()
            }
            tmp.renameTo(file)
        }
    }

    fun prefs(f: (Prefs) -> Prefs) = update { it.copy(prefs = f(it.prefs)) }

    fun putSub(sub: Sub) = update { s ->
        val i = s.subs.indexOfFirst { it.id == sub.id }
        s.copy(subs = if (i < 0) s.subs + sub else s.subs.toMutableList().also { it[i] = sub })
    }

    fun removeSub(id: String) = update { s -> s.copy(subs = s.subs.filter { it.id != id }) }

    fun reorderSubs(ids: List<String>) = update { s ->
        s.copy(subs = ids.mapNotNull { id -> s.subs.find { it.id == id } } + s.subs.filter { it.id !in ids })
    }
}

fun encode(s: AppState): JSONObject = JSONObject()
    .put("subs", JSONArray(s.subs.map { sub ->
        JSONObject().put("id", sub.id).put("url", sub.url).put("name", sub.name)
            .put("entries", JSONArray(sub.entries)).put("updatedAt", sub.updatedAt)
            .put("customHwid", sub.customHwid)
            .put("info", sub.info.let {
                JSONObject().put("upload", it.upload).put("download", it.download).put("total", it.total)
                    .put("expire", it.expire).put("interval", it.intervalHours).put("announce", it.announce)
                    .put("support", it.supportUrl).put("web", it.webPageUrl)
            })
    }))
    .put("prefs", s.prefs.let {
        JSONObject().put("selected", it.selected).put("ruDirect", it.ruDirect).put("appMode", it.appMode.name)
            .put("apps", JSONArray(it.apps.toList())).put("ping", it.ping.name).put("pingUrl", it.pingUrl)
            .put("sort", it.sort.name).put("hwid", it.hwid).put("autoUpdate", it.autoUpdate)
            .put("fragment", it.fragment).put("proxyRules", it.proxyRules).put("directRules", it.directRules)
            .put("blockRules", it.blockRules).put("dns", it.dns).put("fakeDns", it.fakeDns).put("agent", it.agent.name)
            .put("geo", it.geo.name).put("geoUpdated", it.geoUpdated)
    })

fun decode(j: JSONObject): AppState {
    fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k)
    fun JSONArray.strings() = (0 until length()).map { getString(it) }
    val subs = j.optJSONArray("subs")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val i = o.optJSONObject("info") ?: JSONObject()
            Sub(
                o.getString("id"), o.getString("url"), o.getString("name"), o.getJSONArray("entries").strings(),
                SubInfo(
                    i.optLong("upload"), i.optLong("download"), i.optLong("total"), i.optLong("expire"),
                    i.optInt("interval"), i.str("announce"), i.str("support"), i.str("web"),
                ),
                o.optLong("updatedAt"),
                o.optString("customHwid", ""),
            )
        }
    }.orEmpty()
    val p = j.optJSONObject("prefs") ?: JSONObject()
    val d = Prefs()
    val prefs = Prefs(
        selected = p.str("selected"),
        ruDirect = p.optBoolean("ruDirect", d.ruDirect),
        appMode = runCatching { AppMode.valueOf(p.getString("appMode")) }.getOrDefault(d.appMode),
        apps = p.optJSONArray("apps")?.strings()?.toSet() ?: d.apps,
        ping = runCatching { PingMethod.valueOf(p.getString("ping")) }.getOrDefault(d.ping),
        pingUrl = p.optString("pingUrl", d.pingUrl),
        sort = runCatching { SortMode.valueOf(p.getString("sort")) }.getOrDefault(d.sort),
        hwid = p.optBoolean("hwid", d.hwid),
        autoUpdate = p.optBoolean("autoUpdate", d.autoUpdate),
        fragment = p.optBoolean("fragment", d.fragment),
        proxyRules = p.optString("proxyRules", d.proxyRules),
        directRules = p.optString("directRules", d.directRules),
        blockRules = p.optString("blockRules", d.blockRules),
        dns = p.optString("dns", d.dns),
        fakeDns = p.optBoolean("fakeDns", d.fakeDns),
        agent = runCatching { SubAgent.valueOf(p.getString("agent")) }.getOrDefault(d.agent),
        geo = runCatching { GeoSource.valueOf(p.getString("geo")) }.getOrDefault(d.geo),
        geoUpdated = p.optLong("geoUpdated", d.geoUpdated),
    )
    return AppState(subs, prefs)
}
