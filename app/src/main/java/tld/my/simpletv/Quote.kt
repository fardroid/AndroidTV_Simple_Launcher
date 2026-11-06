package tld.my.simpletv

// The quote of the day, in the launcher's language, from Wikiquote. Where the language's Wikiquote has an editors' quote of the
// day on its main page (or, in Italian, a feed of it), that one is shown. Elsewhere, and on a day the editors left empty, a quote
// is picked by the date: an author from a list of about 400 well-known writers, poets, thinkers and scientists born before 1920
// who have a page there (asked of Wikidata once a month), then one quote from that author's page. The whole day shows the same
// quote. A port of src/lib/quote.ts of the webOS version; Net.quote does the fetching.

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId

data class Quote(
    val text: String,
    val author: String,
    val lang: String,
    /** the local day it is for, YYYY-MM-DD */
    val day: String,
    /** "daily": the editors' quote of the day; "picked": picked by the date */
    val kind: String,
    /** the rules it was picked by (Quotes.RULES) */
    val rules: Int = Quotes.RULES,
)

object QuoteRules {
    /** Where each language's quote of the day is. Languages without one always pick by the date. */
    class Daily(val page: String? = null, val feed: Boolean = false, val marker: String? = null, val empty: Regex? = null)
}

object Quotes {
    /** Goes up when the way quotes are read from the pages changes: a quote saved by older rules is fetched again. */
    const val RULES = 2

    val DAILY: Map<String, QuoteRules.Daily> = mapOf(
        "en" to QuoteRules.Daily(page = "Main Page", marker = "Quote of the day"),
        "es" to QuoteRules.Daily(page = "Portada", marker = "Cita del día"),
        "pl" to QuoteRules.Daily(page = "Strona główna", marker = "Cytat dnia"),
        "zh" to QuoteRules.Daily(page = "Wikiquote:首页", marker = "每日名言"),
        "ko" to QuoteRules.Daily(page = "위키인용집:대문", marker = "오늘의 명언"),
        "tr" to QuoteRules.Daily(page = "Ana Sayfa", marker = "Günün Sözü"),
        "pt" to QuoteRules.Daily(page = "Página principal", marker = "Citação do Dia"),
        "uk" to QuoteRules.Daily(page = "Головна сторінка", marker = "Цитата дня", empty = Regex("ще не обрано")),
        "it" to QuoteRules.Daily(feed = true),
    )

    /** Languages written without spaces, where a quote is short in characters. */
    private val DENSE = listOf("zh", "ja")

    /** Headings of the parts of an author's page that hold quotes about the author, doubtful or wrongly attributed ones, links or
     *  sources, in any of the languages. Each heading an item is under is tested on its own. */
    private val SKIP_SECTION = Regex("^(" + listOf(
        "quotes? about", "about ", "misattributed", "disputed", "attributed", "see also", "external links", "sources", "references",
        "о нём", "о ней", "об ", "высказывания о", "цитаты о", "сомнительн", "ошибочно", "приписыва", "см\\. также", "ссылки", "источники", "литература",
        "общие источники", "статьи о",
        "zitate über", "über ", "zitate mit bezug", "fälschlich", "zugeschrieben", "unbelegt", "siehe auch", "weblinks", "quellen", "literatur",
        "citations le concernant", "citations sur", "à propos", "attribu", "apocryph", "voir aussi", "liens", "références", "bibliographie",
        ".*について", ".*に関する", ".*誤って", "関連", "外部リンク", "出典", "参考",
        "про ", "цитати про", "сумнівн", "приписуван", "див\\. також", "посилання", "джерела", "загальні джерела", "статті про",
        "sobre ", "citas sobre", "atribuid", "véase", "enlaces", "fuentes", "veja também", "ligações", "citações sobre",
        ".*hakkında", "ayrıca", "kaynak", "o nim", "o niej", "przypisywane", "zobacz też", "linki", "źródła",
        ".*에 대한", "관련", "외부 링크", ".*的名言", "关于", "出处", "相关", "外部链接",
        "su di", "citazioni su", "attribuit", "voci correlate", "collegamenti", "fonti",
    ).joinToString("|") + ")", RegexOption.IGNORE_CASE)

    /** "О Льве Толстом", "Про Тараса Шевченка": about a person (two capitalised words). Case matters here. */
    private val ABOUT_PERSON = Regex("^(?:О|Об|Про) [А-ЯЁІЇЄҐ]\\S* [А-ЯЁІЇЄҐ]")

    private val ABOUT = Regex("^(?:о|об|обо|про|about|über|sur|sobre|su|o)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private fun aboutAuthor(part: String, author: String): Boolean {
        val m = ABOUT.find(part)
        if (m == null || author.isEmpty()) return false
        val splitter = Regex("[\\s,.-]+")
        val stems = author.lowercase().split(splitter).filter { it.length >= 5 }.map { it.take(5) }
        return m.groupValues[1].lowercase().split(splitter).any { it.take(5) in stems }
    }

    /** The item lies in a part of the page to leave out (`author`: the page's author, for the headings about them). */
    fun skippedSection(section: String, author: String = ""): Boolean =
        section.split(" / ").any { val p = it.trim(); SKIP_SECTION.containsMatchIn(p) || ABOUT_PERSON.containsMatchIn(p) || aboutAuthor(p, author) }

    fun api(lang: String) = "https://$lang.wikiquote.org/w/api.php"

    /** The address of a page's HTML. */
    fun parseUrl(lang: String, page: String) =
        "${api(lang)}?action=parse&format=json&formatversion=2&prop=text&redirects=1&page=${URLEncoder.encode(page, "UTF-8")}"

    /** The address of the Italian quote-of-the-day feed. */
    fun feedUrl(lang: String) = "${api(lang)}?action=featuredfeed&feed=qotd&feedformat=atom"

    /** The HTML out of an action=parse reply (null when it is not one). */
    fun parseReply(text: String): String? = runCatching { JSONObject(text).getJSONObject("parse").getString("text") }.getOrNull()

    /** The local day, YYYY-MM-DD. */
    fun dayKey(now: Long): String = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** A number that is the same all day and different every day. */
    fun dayNumber(now: Long): Long = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private val MARKS = Regex("^[\\s“”\"„«»‘’‚‹›'「」『』〝〞⹂]*$")
    private val DASH_TAIL = Regex("\\s*(?:——|—|–|~|\\s-)\\s*([^—–~\\n]{2,80})$")

    /** Quote marks around the whole text taken off; a source after the closing mark dropped. */
    fun cleanQuote(raw: String, lang: String): String {
        var text = raw.replace(Regex("\\s*//\\s*"), "\n").replace(Regex("\\[\\d+\\]"), "").replace(Regex("[ \\t]+"), " ").trim()
        if (lang == "ja") text = text.replace(Regex("\\s*[（(]\\s*[ぁ-ゖー\\s]+\\s*[）)]"), "")
        text = text.replace(Regex("\\s*(?:--|——)[^\\n]{0,40}$"), "")
        val quoted = Regex("^[„\"“«‚‹「『]([\\s\\S]+?)[“\"”»‘›„」』]\\s*(?:[–—-]\\s*[^\\n]{0,80})?$").find(text)
        if (quoted != null) text = quoted.groupValues[1].trim()
        text = text.replace(Regex("^[„\"“«「『]+|[“\"”»„」』]+$"), "").trim()
        // a mark taken off the end that closed a quote inside the text goes back
        fun count(c: Char) = text.count { it == c }
        if (count('«') > count('»')) text += "»"
        if (count('「') > count('」')) text += "」"
        return text
    }

    /** A line that only points elsewhere, not a quote. */
    private val CROSS_REF = Regex("^(?:см\\.|смотри|див\\.|дивись|see |cf\\.|vgl\\.|siehe |voir |véase |vedi |zob\\.|категория:|категорія:|category:|kategorie:|catégorie:)", RegexOption.IGNORE_CASE)

    private val DASH_SOURCE = Regex("([.!?…»])\\s+—\\s+[^\\n]+$")
    private val BRACKET_SOURCE = Regex("([.!?…»])\\s+(\\((?:[^()\\n]|\\([^()\\n]*\\))*\\))$")

    /** The quote without the source after it, and without a dash before it (a line of speech). */
    fun dropSource(input: String): String {
        var text = input
        val bracket = BRACKET_SOURCE.find(text)
        if (bracket != null && bracket.groupValues[2].any { it.isDigit() }) text = text.substring(0, bracket.range.first + 1)
        return text.replace(DASH_SOURCE, "$1").replace(Regex("^—\\s+"), "")
    }

    private fun fits(text: String, lang: String): Boolean {
        val dense = lang in DENSE
        val n = text.length
        if (if (dense) n < 6 || n > 90 else n < 25 || n > 220) return false
        if (CROSS_REF.containsMatchIn(text) || Regex("[,:;]$").containsMatchIn(text)) return false
        return !Regex("https?:|ISBN|\\bp\\.\\s?\\d|\\bS\\.\\s?\\d|\\bed\\.|éd\\.|trad\\.|\\(\\d{4}\\)|^\\d{4}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    /** The author's name from a page title: "Confucius (philosopher)" → "Confucius". */
    fun authorName(title: String) = title.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()

    /** The editors' quote out of a main page's box: the first line of text after the heading, and the author after it. */
    fun dailyFromLines(lines: List<String>, daily: QuoteRules.Daily, lang: String): Pair<String, String>? {
        var i = 0
        daily.marker?.let { marker ->
            val m = marker.lowercase()
            i = lines.indexOfFirst { val l = it.lowercase(); l == m || (l.startsWith(m) && it.length <= m.length + 3) }
            if (i == -1) return null
            i++
        }
        fun next(): String {
            while (i < lines.size && (MARKS.matches(lines[i]) || lines[i].startsWith(".mw-parser-output"))) i++
            return if (i < lines.size) lines[i++] else ""
        }
        var text = next()
        if (text.isEmpty() || (daily.empty != null && daily.empty.containsMatchIn(text))) return null
        var author: String
        val tail = DASH_TAIL.find(text)
        // a dash inside the quote ("… temporarily — you are the only one …") is not the author's: a long sentence after it is not a name
        val sentence = tail != null && Regex("[.!?…]$").containsMatchIn(tail.groupValues[1].trim()) && tail.groupValues[1].trim().split(Regex("\\s+")).size > 6
        if (tail != null && !sentence && text.length - tail.value.length > 3) {
            author = tail.groupValues[1].trim()
            text = text.substring(0, text.length - tail.value.length)
        } else {
            author = next().replace(Regex("^[\\s—–~-]+"), "").trim()
        }
        text = cleanQuote(text, lang)
        if (text.length < 3 || text.length > 600 || author.length > 120 || author.isEmpty()) return null
        return text to author
    }

    /** The editors' quote of the day from a main page's HTML. */
    fun dailyFromPage(html: String, lang: String): Pair<String, String>? =
        DAILY[lang]?.let { dailyFromLines(HtmlText.lines(html, true), it, lang) }

    /** The newest entry of the Italian quote-of-the-day feed. */
    fun dailyFromFeed(xml: String, lang: String): Pair<String, String>? {
        val entries = xml.split(Regex("<entry>", RegexOption.IGNORE_CASE))
        val last = entries.last()
        val summary = Regex("<summary[^>]*>([\\s\\S]*?)</summary>", RegexOption.IGNORE_CASE).find(last)
            ?: Regex("<content[^>]*>([\\s\\S]*?)</content>", RegexOption.IGNORE_CASE).find(last)
        if (entries.size < 2 || summary == null) return null
        return dailyFromLines(HtmlText.lines(HtmlText.decodeEntities(summary.groupValues[1]), true), QuoteRules.Daily(), lang)
    }

    /** The quotes on an author's page that suit the card: the items of its lists and its quote boxes, outside sections about the
     *  author or of doubtful ones, cleaned and of a fitting length. */
    fun quotesFromPage(html: String, lang: String, author: String = ""): List<String> {
        val boxes = lang == "ru" || lang == "uk"
        val noOriginals = if (boxes) html.replace(Regex("<td[^>]*font-style:\\s*italic[^>]*>[\\s\\S]*?</td>", RegexOption.IGNORE_CASE), "") else ""
        val items: List<ListItem> = when {
            lang == "fr" -> HtmlText.textOfClass(html, "citation")
            boxes -> HtmlText.topLevelItems(html) + HtmlText.textOfClass(html, "q-text", true) + HtmlText.textOfClass(noOriginals, "poem", true)
            else -> HtmlText.topLevelItems(html)
        }
        val out = ArrayList<String>()
        for (item in items) {
            if (item.section.isNotEmpty() && skippedSection(item.section, author)) continue
            val text = dropSource(cleanQuote(item.text, lang))
            if (fits(text, lang) && text !in out) out.add(text)
        }
        return out
    }

    /** The Wikidata query for the authors of a language's Wikiquote: writers, poets, philosophers, scientists and the like, born
     *  before 1920, the best known first (by how many Wikipedias have an article on them). */
    fun authorsQuery(lang: String): String {
        val jobs = "wd:Q36180 wd:Q49757 wd:Q4964182 wd:Q6625963 wd:Q214917 wd:Q901 wd:Q169470 wd:Q1028181 wd:Q36834"
        return "SELECT DISTINCT ?title ?n WHERE { ?a schema:isPartOf <https://$lang.wikiquote.org/>; schema:about ?p; schema:name ?title. " +
            "?p wdt:P31 wd:Q5; wikibase:sitelinks ?n; wdt:P569 ?born; wdt:P106 ?job. VALUES ?job { $jobs } FILTER(YEAR(?born) < 1920) } ORDER BY DESC(?n) LIMIT 400"
    }

    fun authorsUrl(lang: String) = "https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(authorsQuery(lang), "UTF-8").replace("+", "%20")

    /** The page titles out of the Wikidata reply. */
    fun parseAuthors(text: String): List<String> = runCatching {
        val rows = JSONObject(text).getJSONObject("results").getJSONArray("bindings")
        val out = ArrayList<String>()
        for (i in 0 until rows.length()) {
            val t = rows.getJSONObject(i).optJSONObject("title")?.optString("value").orEmpty()
            if (t.isNotEmpty() && t !in out) out.add(t)
        }
        out
    }.getOrDefault(emptyList())

    /** The author for a day: the list is walked in steps of a large prime, so neighbouring days get unrelated authors. */
    fun pickAuthor(authors: List<String>, now: Long, attempt: Int = 0): String? =
        if (authors.isEmpty()) null else authors[((dayNumber(now) * 7919 + attempt.toLong() * 104729) % authors.size).toInt()]

    /** The quote of the day among an author's quotes. */
    fun pickQuote(quotes: List<String>, now: Long): String? =
        if (quotes.isEmpty()) null else quotes[((dayNumber(now) * 2671) % quotes.size).toInt()]

    // ----- the saved copies -----

    const val AUTHORS_FRESH_MS = 30 * 86_400_000L

    fun toJson(q: Quote): String = JSONObject().put("text", q.text).put("author", q.author).put("lang", q.lang).put("day", q.day).put("kind", q.kind).put("rules", q.rules).toString()

    fun fromJson(s: String?): Quote? = if (s == null) null else runCatching {
        val o = JSONObject(s)
        if (o.optInt("rules") != RULES) null else Quote(o.getString("text"), o.getString("author"), o.getString("lang"), o.getString("day"), o.optString("kind", "picked"), RULES)
    }.getOrNull()

    /** The saved quote, when it is today's and in this language. */
    fun todays(q: Quote?, lang: String, now: Long): Quote? = if (q != null && q.lang == lang && q.day == dayKey(now)) q else null

    /** The saved quote even when it is from another day (shown until today's is in), as long as the language is right. */
    fun shown(q: Quote?, lang: String): Quote? = if (q != null && q.lang == lang) q else null

    fun authorsToJson(lang: String, titles: List<String>, now: Long): String = JSONObject().put("lang", lang).put("at", now).put("titles", JSONArray(titles)).toString()

    fun authorsFromJson(s: String?, lang: String, now: Long): List<String>? = if (s == null) null else runCatching {
        val o = JSONObject(s)
        val a = o.getJSONArray("titles")
        if (o.getString("lang") == lang && now - o.getLong("at") < AUTHORS_FRESH_MS && a.length() > 0) (0 until a.length()).map { a.getString(it) } else null
    }.getOrNull()
}
