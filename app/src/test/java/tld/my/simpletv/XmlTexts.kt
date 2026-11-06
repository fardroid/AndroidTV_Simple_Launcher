package tld.my.simpletv

// The texts for the unit tests, which run without Android's resources: read straight from res/values*/strings.xml (the
// files the app is built from), in the language Lang is set to. A resource id is turned back into its name through R.

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object XmlTexts : TextSource {
    private class Values(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>, val fixed: Set<String>)

    private val files = HashMap<String, Values>()

    private fun names(cls: Class<*>): Map<Int, String> = cls.fields.associate { it.getInt(null) to it.name }
    private val stringNames by lazy { names(R.string::class.java) }
    private val pluralNames by lazy { names(R.plurals::class.java) }

    private fun dir(l: Language) = if (l == Language.EN) "values" else "values-${l.code}"

    private fun values(dir: String) = files.getOrPut(dir) {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        fun elements(tag: String, parent: Element = doc.documentElement) =
            parent.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        Values(
            elements("string").associate { it.getAttribute("name") to unescape(it.textContent) },
            elements("plurals").associate { p -> p.getAttribute("name") to elements("item", p).associate { it.getAttribute("quantity") to unescape(it.textContent) } },
            elements("string").filter { it.getAttribute("translatable") == "false" }.map { it.getAttribute("name") }.toSet(),
        )
    }

    /** Every string and plural to translate named in one values folder. */
    fun names(dir: String): Set<String> = values(dir).let { it.strings.keys - it.fixed + it.plurals.keys.map { k -> "plurals/$k" } }

    /** Android's escapes: \u0020, \' \" \\ \n */
    private fun unescape(s: String) = Regex("""\\(u[0-9a-fA-F]{4}|.)""").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("u") && e.length == 5 -> e.substring(1).toInt(16).toChar().toString()
            e == "n" -> "\n"
            else -> e
        }
    }

    override fun string(id: Int): String = values(dir(Lang.current)).strings.getValue(stringNames.getValue(id))

    override fun plural(id: Int, n: Int): String {
        val items = values(dir(Lang.current)).plurals.getValue(pluralNames.getValue(id))
        return String.format(items[quantity(Lang.current, n)] ?: items.getValue("other"), n)
    }

    /** CLDR's plural category of a whole number, for the launcher's languages. */
    private fun quantity(l: Language, n: Int): String {
        val a = n % 100; val b = n % 10
        return when (l) {
            Language.ZH, Language.JA, Language.KO -> "other"
            Language.RU, Language.UK -> if (b == 1 && a != 11) "one" else if (b in 2..4 && a !in 12..14) "few" else "many"
            Language.PL -> if (n == 1) "one" else if (b in 2..4 && a !in 12..14) "few" else "many"
            Language.FR -> if (n < 2) "one" else "other"
            else -> if (n == 1) "one" else "other"
        }
    }
}
