package tld.my.simpletv

// Text out of a page's HTML, without a DOM (so it is the same in the tests): the page as lines of text, the items of its
// top-level lists, or the text of the elements with a given class. Built for Wikiquote's pages (see Quote.kt); good enough for
// the bits of HTML in news feeds too. A port of src/lib/htmltext.ts of the webOS version.

data class ListItem(val text: String, /** the headings of the section the item is in, outermost first, joined with " / " */ val section: String)

object HtmlText {
    /** Elements whose content is never text to read. */
    private val DROP = listOf("style", "script", "sup", "table", "figure", "noscript", "math", "svg")
    private val DROP_KEEP_TABLES = DROP.filter { it != "table" }
    /** Classes whose element is dropped with all it holds (edit links, navigation boxes, notes; in Wikiquote's quote boxes the
     *  original-language text and the editors' remarks). */
    private val DROP_CLASS = Regex("\\b(mw-editsection|navbox|metadata|noprint|reference|mw-empty-elt|toc|thumb|mw-references-wrap|hatnote|ambox|q-original|q-comment)\\b")
    private val BLOCK = setOf("p", "div", "td", "th", "tr", "table", "h1", "h2", "h3", "h4", "h5", "h6", "br", "li", "ul", "ol", "dl", "dd", "dt", "center", "blockquote", "section", "tbody")
    private val VOID = setOf("br", "img", "hr", "input", "meta", "link", "wbr", "source", "area", "col", "embed")

    private val ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0", "ndash" to "–", "mdash" to "—",
        "laquo" to "«", "raquo" to "»", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "bdquo" to "„", "middot" to "·", "shy" to "",
    )
    private val ENTITY = Regex("&(#x[0-9a-f]+|#\\d+|[a-z]+);", RegexOption.IGNORE_CASE)

    /** "&amp;" → "&", "&#8212;" → "—" and so on. */
    fun decodeEntities(text: String): String = ENTITY.replace(text) { m ->
        val name = m.groupValues[1]
        if (name[0] == '#') {
            val code = (if (name[1] == 'x' || name[1] == 'X') name.substring(2).toIntOrNull(16) else name.substring(1).toIntOrNull()) ?: 0
            if (code in 1 until 0x110000) String(Character.toChars(code)) else m.value
        } else ENTITIES[name.lowercase()] ?: m.value
    }

    private class Tag(val name: String, val close: Boolean, val attrs: String, val start: Int, val end: Int)

    private val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>|<!--[\\s\\S]*?-->")

    private fun tags(html: String): List<Tag> = TAG.findAll(html).mapNotNull { m ->
        if (m.groupValues[2].isEmpty()) null
        else Tag(m.groupValues[2].lowercase(), m.groupValues[1] == "/", m.groupValues[3], m.range.first, m.range.last + 1)
    }.toList()

    private val CLASS = Regex("\\bclass\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private fun classOf(attrs: String): String = CLASS.find(attrs)?.let { it.groups[2]?.value ?: it.groups[3]?.value ?: "" } ?: ""

    /** One pass over the HTML: `onText` gets every piece of text outside dropped elements, `onTag` every other tag. */
    private fun walk(html: String, onTag: (Tag, String) -> Unit, onText: (String) -> Unit, drop: List<String> = DROP) {
        var at = 0
        var dropping = 0
        val dropStack = ArrayList<String>()
        for (tag in tags(html)) {
            if (dropping == 0 && tag.start > at) onText(html.substring(at, tag.start))
            at = tag.end
            val selfClosing = tag.name in VOID || tag.attrs.trimEnd().endsWith("/")
            if (dropping > 0) {
                if (selfClosing) continue
                if (tag.close && tag.name == dropStack.last()) { dropStack.removeAt(dropStack.lastIndex); dropping-- }
                else if (!tag.close && tag.name == dropStack.last()) { dropStack.add(tag.name); dropping++ }
                continue
            }
            val cls = if (tag.close) "" else classOf(tag.attrs)
            if (!tag.close && !selfClosing && (tag.name in drop || DROP_CLASS.containsMatchIn(cls))) {
                dropping = 1
                dropStack.clear()
                dropStack.add(tag.name)
                continue
            }
            onTag(tag, cls)
        }
        if (dropping == 0 && at < html.length) onText(html.substring(at))
    }

    private val SPACES = Regex("[ \\t\\r\\u000B\\u000C\\u00A0]+")
    private val AROUND_NL = Regex(" *\\n *")
    private fun tidy(text: String): String = decodeEntities(text).replace(SPACES, " ").replace(AROUND_NL, "\n").trim()

    private val WS = Regex("\\s+")
    private val NBSP_RUN = Regex("[ \\u00A0]+")

    /** The page as lines of text (one per block element), empty lines left out. `tables`: keep what is in tables. */
    fun lines(html: String, tables: Boolean = false): List<String> {
        val out = StringBuilder()
        walk(html, { tag, _ -> if (tag.name in BLOCK) out.append('\n') }, { text -> out.append(text.replace(WS, " ")) }, if (tables) DROP_KEEP_TABLES else DROP)
        return decodeEntities(out.toString()).split('\n').map { it.replace(NBSP_RUN, " ").trim() }.filter { it.isNotEmpty() }
    }

    /** Plain text of a bit of HTML (a feed's description, say): tags gone, entities decoded, blocks on lines of their own. */
    fun toText(html: String): String = lines(html).joinToString("\n")

    /** The headings an item is under, outermost first ("Quotes about him / Letters"): a heading replaces those at its level and below. */
    private class SectionTracker {
        private val levels = ArrayList<String>()
        fun set(tag: String, text: String): String {
            val depth = maxOf(0, tag.substring(1).toInt() - 2)
            while (levels.size > depth) levels.removeAt(levels.lastIndex)
            while (levels.size < depth) levels.add("")
            levels.add(text)
            return levels.filter { it.isNotEmpty() }.joinToString(" / ")
        }
    }

    private val HEADING = Regex("^h[1-6]$")

    /** The items of the page's top-level lists (lists nested in an item left out), each with the heading of its section. */
    fun topLevelItems(html: String): List<ListItem> {
        val items = ArrayList<ListItem>()
        var depth = 0
        var current: StringBuilder? = null
        var heading: StringBuilder? = null
        var level = "h2"
        val sections = SectionTracker()
        var section = ""
        walk(html, { tag, _ ->
            val n = tag.name
            when {
                HEADING.matches(n) -> {
                    if (!tag.close) { heading = StringBuilder(); level = n }
                    else if (heading != null) { section = sections.set(level, tidy(heading.toString())); heading = null }
                }
                n == "ul" || n == "ol" || n == "dl" -> { depth += if (tag.close) -1 else 1; if (depth < 0) depth = 0 }
                n == "li" && depth == 1 -> {
                    if (!tag.close) current = StringBuilder()
                    else if (current != null) { items.add(ListItem(tidy(current.toString()), section)); current = null }
                }
                n == "br" && current != null && depth == 1 -> current!!.append('\n')
            }
        }, { text ->
            if (heading != null) heading!!.append(text)
            else if (current != null && depth == 1) current!!.append(text.replace(WS, " "))
        })
        return items.filter { it.text.isNotEmpty() }
    }

    /** The text of every element whose class list has `name` (nested ones are part of their parent's text). `tables`: look inside tables too. */
    fun textOfClass(html: String, name: String, tables: Boolean = false): List<ListItem> {
        val out = ArrayList<ListItem>()
        val re = Regex("(^|\\s)" + Regex.escape(name) + "(\\s|$)")
        val stack = ArrayList<String>()
        var current: StringBuilder? = null
        var heading: StringBuilder? = null
        var level = "h2"
        val sections = SectionTracker()
        var section = ""
        walk(html, { tag, cls ->
            if (HEADING.matches(tag.name) && current == null) {
                if (!tag.close) { heading = StringBuilder(); level = tag.name }
                else if (heading != null) { section = sections.set(level, tidy(heading.toString())); heading = null }
                return@walk
            }
            if (tag.name in VOID) { if (tag.name == "br" && current != null) current!!.append('\n'); return@walk }
            if (current == null) {
                if (!tag.close && re.containsMatchIn(cls)) { current = StringBuilder(); stack.clear(); stack.add(tag.name) }
                return@walk
            }
            if (!tag.close) { stack.add(tag.name); if (tag.name in BLOCK) current!!.append('\n'); return@walk }
            if (stack.isNotEmpty() && tag.name == stack.last()) stack.removeAt(stack.lastIndex)
            if (stack.isEmpty()) { out.add(ListItem(tidy(current.toString()), section)); current = null }
        }, { text ->
            if (heading != null) heading!!.append(text)
            else if (current != null) current!!.append(text.replace(WS, " "))
        }, if (tables) DROP_KEEP_TABLES else DROP)
        return out.filter { it.text.isNotEmpty() }
    }
}
