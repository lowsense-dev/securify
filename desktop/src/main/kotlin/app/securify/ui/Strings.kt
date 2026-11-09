package app.securify.ui

import androidx.compose.runtime.Composable
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Reads the Android app's strings.xml (plus the desktop-only ones), picking Russian by the system language like Android does. */
object Strings {
    private val ru = Locale.getDefault().language == "ru"
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()

    init {
        load("values")
        if (ru) load("values-ru")
    }

    private fun load(dir: String) {
        for (file in listOf("android.xml", "desktop.xml")) {
            val stream = Strings::class.java.getResourceAsStream("/strings/$dir/$file") ?: continue
            val doc = stream.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            val list = doc.documentElement.childNodes
            for (i in 0 until list.length) {
                val e = list.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> strings[name] = unescape(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).map { items.item(it) as Element }
                            .associate { it.getAttribute("quantity") to unescape(it.textContent) }
                    }
                }
            }
        }
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        val t = s.trim().removeSurrounding("\"")
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length) {
                append(when (val n = t[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> n })
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private fun quantity(n: Int): String = if (!ru) (if (n == 1) "one" else "other") else when {
        n % 10 == 1 && n % 100 != 11 -> "one"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "few"
        else -> "many"
    }

    fun get(name: String, vararg args: Any): String {
        val s = strings[name] ?: name
        return if (args.isEmpty()) s else s.format(*args)
    }

    fun plural(name: String, count: Int, vararg args: Any): String {
        val forms = plurals[name] ?: return name
        val s = forms[quantity(count)] ?: forms["other"] ?: forms.values.first()
        return s.format(*args)
    }
}

fun str(id: String, vararg args: Any) = Strings.get(id, *args)

@Composable
fun stringResource(id: String, vararg formatArgs: Any): String = Strings.get(id, *formatArgs)

@Composable
fun pluralStringResource(id: String, count: Int, vararg formatArgs: Any): String = Strings.plural(id, count, *formatArgs)
