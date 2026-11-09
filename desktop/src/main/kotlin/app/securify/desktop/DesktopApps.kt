package app.securify.desktop

import java.io.File
import java.util.Locale

class DesktopApp(val name: String, val process: String)

/**
 * Installed apps from .desktop files, with the process name Xray will see: the real binary behind the Exec command.
 * shortcut: Flatpak and Snap apps are skipped, their processes run under sandbox paths; the process field covers them by hand.
 */
fun desktopApps(): List<DesktopApp> {
    val home = System.getProperty("user.home")
    val ru = Locale.getDefault().language == "ru"
    val dirs = (System.getenv("XDG_DATA_DIRS")?.ifBlank { null } ?: "/usr/local/share:/usr/share").split(':') +
        (System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: "$home/.local/share")
    return dirs.distinct().flatMap { d -> File(d, "applications").walkTopDown().filter { it.extension == "desktop" }.toList() }
        .mapNotNull { f ->
            val entry = runCatching { f.readLines() }.getOrNull()?.dropWhile { it.trim() != "[Desktop Entry]" }?.drop(1)
                ?.takeWhile { !it.startsWith("[") }?.filter { '=' in it }?.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                ?: return@mapNotNull null
            if (entry["Type"] != "Application" || entry["NoDisplay"] == "true" || entry["Hidden"] == "true") return@mapNotNull null
            val name = (if (ru) entry["Name[ru]"] else null) ?: entry["Name"] ?: return@mapNotNull null
            val process = processOf(entry["Exec"] ?: return@mapNotNull null) ?: return@mapNotNull null
            DesktopApp(name, process)
        }
        .distinctBy { it.process }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun processOf(exec: String): String? {
    val words = Regex("""(?:"([^"]*)"|(\S+))""").findAll(exec).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        .filter { !it.startsWith("%") }.toList()
    val cmd = words.dropWhile { it == "env" || (Regex("^[A-Za-z_][A-Za-z0-9_]*=").containsMatchIn(it)) }.firstOrNull() ?: return null
    if (cmd.substringAfterLast('/') in setOf("flatpak", "snap", "sh", "bash")) return null
    val file = if ('/' in cmd) File(cmd) else Env.which(cmd) ?: return cmd
    return runCatching { file.toPath().toRealPath().fileName.toString() }.getOrDefault(file.name)
}
