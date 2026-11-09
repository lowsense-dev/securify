package app.securify.desktop

import app.securify.core.Geo
import app.securify.ui.BuildInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

const val TUN_NAME = "securify0"

/**
 * Runs as root through pkexec or sudo until stdin closes, so the password is asked once per app session.
 * "start" (re)starts Xray with config.json, "stop" stops it; "@started"/"@exited" lines report Xray's pid.
 * "@ready" tells the app that authentication is over.
 */
private val HELPER = """
#!/bin/sh
dir=${'$'}(dirname "${'$'}0")
pid=
stop() {
    if [ -n "${'$'}pid" ]; then kill "${'$'}pid" 2>/dev/null; wait "${'$'}pid" 2>/dev/null; pid=; fi
}
trap 'stop; exit 0' INT TERM HUP
echo @ready
while IFS= read -r cmd; do
    case "${'$'}cmd" in
        start)
            stop
            XRAY_LOCATION_ASSET="${'$'}dir" "${'$'}dir/xray" run -c "${'$'}dir/config.json" 2>&1 &
            pid=${'$'}!
            echo "@started ${'$'}pid"
            ( p=${'$'}pid; while kill -0 "${'$'}p" 2>/dev/null; do sleep 1; done; echo "@exited ${'$'}p" ) &
            ;;
        stop) stop ;;
    esac
done
stop
""".trimStart()

object Env {
    private val home = System.getProperty("user.home")
    val data = File(System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: "$home/.local/share", "securify")
    private val resources = File(System.getProperty("compose.application.resources.dir") ?: "resources")
    val appDir: String? = System.getenv("APPDIR")
    val appImage: String? = System.getenv("APPIMAGE")
    val xray get() = File(data, "xray")
    val helper get() = File(data, "securify-helper")

    /** Root can't read the AppImage's FUSE mount, so everything the helper runs is copied to the data dir first. */
    fun setup() {
        data.mkdirs()
        Files.setPosixFilePermissions(data.toPath(), PosixFilePermissions.fromString("rwx------"))
        val src = File(resources, "xray")
        if (!xray.exists() || xray.length() != src.length() || xray.lastModified() != src.lastModified()) {
            val tmp = File(data, "xray.tmp")
            src.copyTo(tmp, overwrite = true)
            tmp.setExecutable(true, true)
            tmp.setLastModified(src.lastModified())
            tmp.renameTo(xray)
        }
        if (!helper.exists() || helper.readText() != HELPER) {
            helper.writeText(HELPER)
            helper.setExecutable(true, true)
        }
        Geo.ensure(data, BuildInfo.VERSION_CODE.toString()) { File(resources, it).inputStream() }
    }

    fun bundled(name: String) = File(resources, name).inputStream()

    /** Stable per install like ANDROID_ID: machine-id if readable, otherwise an id kept in the data dir. */
    fun deviceId(): String = runCatching { File("/etc/machine-id").readText().trim() }.getOrNull()?.ifEmpty { null }
        ?: File(data, "device-id").let { f -> f.takeIf { it.exists() }?.readText() ?: UUID.randomUUID().toString().also(f::writeText) }

    fun osVersion(): String = runCatching {
        File("/etc/os-release").readLines().associate { it.substringBefore('=') to it.substringAfter('=').trim('"') }
            .let { it["PRETTY_NAME"] ?: it["NAME"] }
    }.getOrNull() ?: System.getProperty("os.version")

    /** Xray can point systemd-resolved at the tunnel; without resolved the system DNS stays as it is. */
    fun hasResolved() = File("/run/systemd/resolve/stub-resolv.conf").exists() && which("resolvectl") != null

    fun which(cmd: String) = System.getenv("PATH").orEmpty().split(':').map { File(it, cmd) }.firstOrNull { it.canExecute() }

    /** The interface of the lowest-metric IPv4 default route that isn't our tunnel. */
    fun physicalInterface(): String? = runCatching {
        File("/proc/net/route").readLines().drop(1).map { it.trim().split(Regex("\\s+")) }
            .filter { it.size > 7 && it[1] == "00000000" && it[7] == "00000000" && it[0] != TUN_NAME }
            .minByOrNull { it[6].toIntOrNull() ?: Int.MAX_VALUE }?.get(0)
    }.getOrNull()

    /** Bytes the kernel sent into the tunnel (upload) and got back from it (download). */
    fun tunBytes(): Pair<Long, Long>? = runCatching {
        val s = File("/sys/class/net/$TUN_NAME/statistics")
        File(s, "tx_bytes").readText().trim().toLong() to File(s, "rx_bytes").readText().trim().toLong()
    }.getOrNull()
}
