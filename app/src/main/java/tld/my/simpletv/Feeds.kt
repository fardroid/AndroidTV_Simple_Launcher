package tld.my.simpletv

// The news card: headlines from Google News for the chosen country (in the launcher's language when the country has an edition
// in it, otherwise in the country's own) and from the user's own feeds. RSS 2.0, RSS 1.0 (RDF) and Atom are read; the feeds are
// joined into one list, newest first, without repeats. A port of src/lib/feeds.ts of the webOS version.

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FeedItem(
    val title: String,
    val link: String,
    /** when it was published, ms (0: not given) */
    val at: Long,
    /** the outlet or the feed's own name */
    val source: String,
    /** the description as plain text ("" when none) */
    val summary: String,
    /** which feed it came from (index in the list of sources) */
    val feed: Int,
)

data class NewsData(
    /** the sources it was read from, joined (a change of sources makes the saved copy useless) */
    val key: String,
    val at: Long,
    val items: List<FeedItem>,
)

/** One Google News edition: the language it is in, and the hl and ceid of its address. */
data class Edition(val lang: String, val hl: String, val ceid: String)

object Feeds {
    private fun e(lang: String, hl: String, ceid: String) = Edition(lang, hl, ceid)

    /** Google News editions by country (checked to return headlines), English name first. */
    val EDITIONS: Map<String, Pair<String, List<Edition>>> = mapOf(
        "AR" to ("Argentina" to listOf(e("es", "es-419", "AR:es-419"))),
        "AU" to ("Australia" to listOf(e("en", "en-AU", "AU:en"))),
        "BG" to ("Bulgaria" to listOf(e("bg", "bg", "BG:bg"))),
        "BR" to ("Brazil" to listOf(e("pt", "pt-BR", "BR:pt-419"))),
        "CA" to ("Canada" to listOf(e("en", "en-CA", "CA:en"), e("fr", "fr-CA", "CA:fr"))),
        "CL" to ("Chile" to listOf(e("es", "es-419", "CL:es-419"))),
        "CN" to ("China" to listOf(e("zh", "zh-CN", "CN:zh-Hans"))),
        "CO" to ("Colombia" to listOf(e("es", "es-419", "CO:es-419"))),
        "CZ" to ("Czechia" to listOf(e("cs", "cs", "CZ:cs"))),
        "DE" to ("Germany" to listOf(e("de", "de", "DE:de"))),
        "EG" to ("Egypt" to listOf(e("ar", "ar", "EG:ar"))),
        "ES" to ("Spain" to listOf(e("es", "es", "ES:es"))),
        "FR" to ("France" to listOf(e("fr", "fr", "FR:fr"))),
        "GB" to ("United Kingdom" to listOf(e("en", "en-GB", "GB:en"))),
        "GR" to ("Greece" to listOf(e("el", "el", "GR:el"))),
        "HK" to ("Hong Kong" to listOf(e("zh", "zh-HK", "HK:zh-Hant"))),
        "HU" to ("Hungary" to listOf(e("hu", "hu", "HU:hu"))),
        "ID" to ("Indonesia" to listOf(e("id", "id", "ID:id"))),
        "IE" to ("Ireland" to listOf(e("en", "en-IE", "IE:en"))),
        "IL" to ("Israel" to listOf(e("he", "he", "IL:he"), e("en", "en-IL", "IL:en"))),
        "IN" to ("India" to listOf(e("en", "en-IN", "IN:en"))),
        "IT" to ("Italy" to listOf(e("it", "it", "IT:it"))),
        "JP" to ("Japan" to listOf(e("ja", "ja", "JP:ja"))),
        "KR" to ("South Korea" to listOf(e("ko", "ko", "KR:ko"))),
        "LT" to ("Lithuania" to listOf(e("lt", "lt", "LT:lt"))),
        "LV" to ("Latvia" to listOf(e("lv", "lv", "LV:lv"))),
        "MX" to ("Mexico" to listOf(e("es", "es-419", "MX:es-419"))),
        "MY" to ("Malaysia" to listOf(e("en", "en-MY", "MY:en"))),
        "NG" to ("Nigeria" to listOf(e("en", "en-NG", "NG:en"))),
        "NL" to ("Netherlands" to listOf(e("nl", "nl", "NL:nl"))),
        "NO" to ("Norway" to listOf(e("no", "no", "NO:no"))),
        "NZ" to ("New Zealand" to listOf(e("en", "en-NZ", "NZ:en"))),
        "PE" to ("Peru" to listOf(e("es", "es-419", "PE:es-419"))),
        "PH" to ("Philippines" to listOf(e("en", "en-PH", "PH:en"))),
        "PL" to ("Poland" to listOf(e("pl", "pl", "PL:pl"))),
        "PT" to ("Portugal" to listOf(e("pt", "pt-PT", "PT:pt-150"))),
        "RO" to ("Romania" to listOf(e("ro", "ro", "RO:ro"))),
        "RS" to ("Serbia" to listOf(e("sr", "sr", "RS:sr"))),
        "RU" to ("Russia" to listOf(e("ru", "ru", "RU:ru"))),
        "SA" to ("Saudi Arabia" to listOf(e("ar", "ar", "SA:ar"))),
        "AE" to ("United Arab Emirates" to listOf(e("ar", "ar", "AE:ar"))),
        "SE" to ("Sweden" to listOf(e("sv", "sv", "SE:sv"))),
        "SG" to ("Singapore" to listOf(e("en", "en-SG", "SG:en"))),
        "SI" to ("Slovenia" to listOf(e("sl", "sl", "SI:sl"))),
        "SK" to ("Slovakia" to listOf(e("sk", "sk", "SK:sk"))),
        "TH" to ("Thailand" to listOf(e("th", "th", "TH:th"))),
        "TR" to ("Türkiye" to listOf(e("tr", "tr", "TR:tr"))),
        "TW" to ("Taiwan" to listOf(e("zh", "zh-TW", "TW:zh-Hant"))),
        "UA" to ("Ukraine" to listOf(e("uk", "uk", "UA:uk"), e("ru", "ru", "UA:ru"))),
        "US" to ("United States" to listOf(e("en", "en-US", "US:en"), e("es", "es-419", "US:es-419"))),
        "VN" to ("Vietnam" to listOf(e("vi", "vi", "VN:vi"))),
        "ZA" to ("South Africa" to listOf(e("en", "en-ZA", "ZA:en"))),
    )

    val COUNTRIES: List<String> = EDITIONS.keys.sorted()

    /** The country whose Google News a launcher language stands for, when the box's own region has no edition in it. */
    private val HOME_COUNTRY = mapOf(
        "en" to "US", "ru" to "RU", "es" to "ES", "fr" to "FR", "de" to "DE", "it" to "IT", "pt" to "PT",
        "tr" to "TR", "uk" to "UA", "zh" to "CN", "ja" to "JP", "ko" to "KR", "pl" to "PL",
    )

    /** The region of a language tag ("pt-BR" → BR) when Google News has it, else "". */
    fun countryOfTag(tag: String): String {
        val m = Regex("[-_]([A-Za-z]{2})\\b").find(tag) ?: return ""
        val code = m.groupValues[1].uppercase()
        return if (EDITIONS.containsKey(code)) code else ""
    }

    /** The country in use: the chosen one ("" for none yet); otherwise the box's region when it has an edition in the launcher's
     *  language, else the launcher language's own country. */
    fun countryOf(chosen: String, tag: String, lang: Language): String {
        if (EDITIONS.containsKey(chosen)) return chosen
        val region = countryOfTag(tag)
        if (region.isNotEmpty() && EDITIONS.getValue(region).second.any { it.lang == lang.code }) return region
        return HOME_COUNTRY[lang.code] ?: region.ifEmpty { "US" }
    }

    /** The country's name in the screen's language (the English one where the platform cannot name it). */
    fun countryName(code: String, lang: Language): String {
        val name = runCatching { Locale("", code).getDisplayCountry(lang.locale) }.getOrDefault("")
        return if (name.isNotEmpty() && name != code) name else EDITIONS[code]?.first ?: code
    }

    /** The edition of a country for the launcher's language: in that language when there is one, else the country's own. */
    fun editionFor(country: String, lang: Language): Edition {
        val list = (EDITIONS[country] ?: EDITIONS.getValue("US")).second
        return list.firstOrNull { it.lang == lang.code } ?: list[0]
    }

    /** The Google News feed of a country's top stories. */
    fun googleNewsUrl(country: String, lang: Language): String {
        val code = if (EDITIONS.containsKey(country)) country else "US"
        val ed = editionFor(code, lang)
        return "https://news.google.com/rss?hl=${ed.hl}&gl=$code&ceid=${ed.ceid.replace(":", "%3A")}"
    }

    /** The address as it is fetched: "feed://" is https; spaces from typing are dropped. */
    fun normalizeUrl(raw: String): String =
        raw.replace(Regex("\\s+"), "").replace(Regex("^feed://", RegexOption.IGNORE_CASE), "https://").replace(Regex("^feed:", RegexOption.IGNORE_CASE), "")

    fun validUrl(url: String) = Regex("^https?://[^\\s'\"`<>\\\\]+$", RegexOption.IGNORE_CASE).matches(url) && url.length <= 600

    const val MAX_FEEDS = 8

    // ----- parsing -----

    private fun inner(block: String, names: List<String>): String? {
        for (name in names) {
            val re = Regex("<" + Regex.escape(name) + "(?:\\s[^>]*)?>([\\s\\S]*?)</" + Regex.escape(name) + ">", RegexOption.IGNORE_CASE)
            re.find(block)?.let { return it.groupValues[1] }
        }
        return null
    }

    private val CDATA = Regex("<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>")

    /** The text inside an element: CDATA unwrapped, escaped HTML (a description) turned to plain text. */
    private fun textOf(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var s = raw.replace(CDATA, "$1")
        // a feed escapes the HTML of its descriptions: decode once, then read the tags
        if (Regex("&lt;|&#60;").containsMatchIn(s) && !Regex("<[a-z]", RegexOption.IGNORE_CASE).containsMatchIn(s)) s = HtmlText.decodeEntities(s)
        var text = HtmlText.toText(s)
        // a title of type "html" is escaped twice (&amp;amp;)
        if (Regex("&(amp|lt|gt|quot|#\\d+);").containsMatchIn(text)) text = HtmlText.decodeEntities(text)
        return text.replace(Regex("\\s*\\n\\s*"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun linkOf(block: String): String {
        val rss = inner(block, listOf("link"))
        if (rss != null && rss.isNotBlank() && !rss.trimStart().startsWith("<")) return HtmlText.decodeEntities(rss.replace(CDATA, "$1").trim())
        // Atom: <link rel="alternate" href="…"/> (or the first link without a rel)
        var best = ""
        for (l in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(block).map { it.value }) {
            val href = Regex("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(l) ?: continue
            val rel = Regex("\\brel\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(l)
            if (rel == null || rel.groupValues[1] == "alternate") return HtmlText.decodeEntities(href.groupValues[1])
            if (best.isEmpty()) best = HtmlText.decodeEntities(href.groupValues[1])
        }
        val guid = inner(block, listOf("guid"))
        return best.ifEmpty { if (guid != null && Regex("^https?:").containsMatchIn(guid.trim())) guid.trim() else "" }
    }

    /** A feed's date (RFC 822 as RSS has it, or ISO 8601 as Atom and Dublin Core have it) in ms; 0 when unreadable. */
    fun parseDate(raw: String): Long {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        val tries = listOf<(String) -> Long>(
            { Instant.parse(it).toEpochMilli() },
            { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
            { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() },
            { ZonedDateTime.parse(it.replace(Regex("^\\w{3},? "), ""), DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] [zzz][XX]", Locale.ENGLISH)).toInstant().toEpochMilli() },
            { java.time.LocalDateTime.parse(it).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() },
            { java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        for (t in tries) { val v = runCatching { t(s) }.getOrNull(); if (v != null) return v }
        return 0
    }

    private fun dateOf(block: String): Long {
        val raw = inner(block, listOf("pubDate", "dc:date", "published", "updated", "a10:updated")) ?: return 0
        return parseDate(raw)
    }

    /** The headlines of one feed (RSS 2.0, RSS 1.0 or Atom). */
    fun parse(xml: String, feed: Int): List<FeedItem> {
        val start = Regex("<(item|entry)[\\s>]", RegexOption.IGNORE_CASE).find(xml)?.range?.first ?: -1
        val head = if (start == -1) xml else xml.substring(0, start)
        val channel = textOf(inner(head, listOf("title")))
        val blocks = Regex("<(item|entry)(?:\\s[^>]*)?>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE).findAll(xml).map { it.value }
        val out = ArrayList<FeedItem>()
        for (block in blocks) {
            var title = textOf(inner(block, listOf("title")))
            if (title.isEmpty()) continue
            var source = textOf(inner(block, listOf("source")))
            // Google News writes the outlet after the headline too: "Headline - Outlet"
            if (source.isNotEmpty() && title.length > source.length + 3 && title.endsWith(" - $source")) title = title.dropLast(source.length + 3)
            if (source.isEmpty()) source = channel
            val summary = textOf(inner(block, listOf("description", "summary", "content:encoded", "content")))
            out.add(FeedItem(title, linkOf(block), dateOf(block), source, if (summary == title) "" else summary.take(600), feed))
        }
        return out
    }

    /** Headlines older than this are left out. */
    const val MAX_AGE_MS = 48 * 3_600_000L
    const val MAX_ITEMS = 50
    /** a copy younger than this is not fetched again */
    const val FRESH_MS = 25 * 60_000L

    private fun sameStory(title: String) = title.lowercase().replace(Regex("[\\s.,:;!?'\"“”‘’«»()\\[\\]\\-–—…|]+"), " ").trim()

    /** All the feeds' headlines in one list: recent ones only (those without a date stay), newest first, no repeats. */
    fun join(lists: List<List<FeedItem>>, now: Long): List<FeedItem> {
        val seen = HashSet<String>()
        val all = ArrayList<FeedItem>()
        for (list in lists) for (item in list) {
            if (item.at != 0L && (now - item.at > MAX_AGE_MS || item.at - now > 3_600_000L)) continue
            val keys = listOf(item.link, sameStory(item.title)).filter { it.isNotEmpty() }
            if (keys.any { it in seen }) continue
            seen.addAll(keys)
            all.add(item)
        }
        all.sortByDescending { it.at }
        return all.take(MAX_ITEMS)
    }

    // ----- the card -----

    /** How many headlines fit on a page of a card `height` px tall at text size `k` (two lines each). */
    fun headlinesPerPage(height: Float, k: Float): Int {
        val head = 44 * k
        val item = 16 + 15 * k * 1.35f + 6 + 2 * 22 * k * 1.3f + 14
        return maxOf(1, ((height - 44 - head - 10) / item).toInt())
    }

    enum class Unit { MIN, H, D }

    /** The age of a headline: minutes under an hour, then hours, then days (the words come from the caller, translated). */
    fun ageOf(at: Long, now: Long): Pair<Int, Unit>? {
        if (at == 0L) return null
        val minutes = maxOf(1L, Math.round((now - at) / 60000.0))
        return when {
            minutes < 60 -> minutes.toInt() to Unit.MIN
            minutes < 48 * 60 -> Math.round(minutes / 60.0).toInt() to Unit.H
            else -> Math.round(minutes / 1440.0).toInt() to Unit.D
        }
    }

    // ----- the saved copy -----

    fun toJson(d: NewsData): String = JSONObject().put("key", d.key).put("at", d.at).put("items", JSONArray(d.items.map {
        JSONObject().put("title", it.title).put("link", it.link).put("at", it.at).put("source", it.source).put("summary", it.summary).put("feed", it.feed)
    })).toString()

    fun fromJson(s: String?): NewsData? {
        if (s == null) return null
        return runCatching {
            val o = JSONObject(s)
            val a = o.getJSONArray("items")
            NewsData(o.getString("key"), o.getLong("at"), (0 until a.length()).map {
                val i = a.getJSONObject(it)
                FeedItem(i.getString("title"), i.optString("link"), i.optLong("at"), i.optString("source"), i.optString("summary"), i.optInt("feed"))
            })
        }.getOrNull()
    }

    /** The saved copy when it is of these sources. */
    fun usable(d: NewsData?, key: String): NewsData? = if (d != null && d.key == key) d else null

    fun isFresh(d: NewsData?, key: String, now: Long) = d != null && d.key == key && now - d.at < FRESH_MS
}
