package app.securify.desktop

import app.securify.core.Updater
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun xrayVersion(): String = runCatching {
    val p = ProcessBuilder(Env.xray.path, "version").redirectErrorStream(true).start()
    p.inputStream.bufferedReader().readLine().orEmpty().also { p.waitFor() }.substringBefore(" (").trim()
}.getOrDefault("")

/** Only one copy runs: a second launch (e.g. a menu action) hands its command to the first one over a socket. */
object Instance {
    private val sock get() = File(Env.data, "gui.sock")

    fun forward(cmd: String): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
            ch.connect(UnixDomainSocketAddress.of(sock.toPath()))
            ch.write(ByteBuffer.wrap("$cmd\n".toByteArray()))
        }
    }.isSuccess

    fun listen(onCommand: (String) -> Unit) {
        sock.delete()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(sock.toPath()))
        thread(isDaemon = true, name = "instance") {
            while (true) {
                val ch = runCatching { server.accept() }.getOrNull() ?: break
                ch.use {
                    val buf = ByteBuffer.allocate(64)
                    it.read(buf)
                    onCommand(String(buf.array(), 0, buf.position()).trim())
                }
            }
        }
    }
}

/** What Android launcher shortcuts do: a menu entry with Connect and Disconnect actions for this AppImage. */
fun addToMenu(): Boolean {
    val image = Env.appImage ?: return false
    val share = File(System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: (System.getProperty("user.home") + "/.local/share"))
    val icon = File(share, "icons/hicolor/scalable/apps/securify.svg").apply { parentFile.mkdirs() }
    Env::class.java.getResourceAsStream("/securify.svg")?.use { i -> icon.outputStream().use { i.copyTo(it) } }
    val exec = "\"" + image.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    File(share, "applications").mkdirs()
    File(share, "applications/securify.desktop").writeText(
        """
        [Desktop Entry]
        Type=Application
        Name=Securify
        Comment=Xray proxy client with TUN mode
        Comment[ru]=Прокси-клиент на Xray с TUN-режимом
        Exec=$exec %U
        TryExec=$exec
        Icon=securify
        Categories=Network;
        Terminal=false
        StartupWMClass=securify
        Actions=Connect;Disconnect;

        [Desktop Action Connect]
        Name=Connect
        Name[ru]=Подключить
        Exec=$exec --connect

        [Desktop Action Disconnect]
        Name=Disconnect
        Name[ru]=Отключить
        Exec=$exec --disconnect
        """.trimIndent() + "\n",
    )
    return true
}

/** Swaps the verified download in place of the running AppImage and starts it; the tunnel stops with this process. */
fun installAppImageUpdate(file: File, reconnect: Boolean) {
    val target = File(Env.appImage ?: return)
    file.setExecutable(true, false)
    Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    File(Env.data, "gui.sock").delete()
    ProcessBuilder(listOfNotNull(target.path, if (reconnect) "--connect" else null)).start()
    exitProcess(0)
}

fun setupUpdater(isActive: () -> Boolean) {
    val image = Env.appImage ?: return
    Updater.pick = { names -> names.firstOrNull { it.endsWith("-x86_64.AppImage") } }
    // the move into place must stay on one filesystem
    Updater.downloadDir = File(image).parentFile
    Updater.apply = { installAppImageUpdate(it, isActive()) }
}
