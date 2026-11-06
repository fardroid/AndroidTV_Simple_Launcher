package tld.my.simpletv

// Fetching the weather, searching for places and reading the calendars. Blocking calls: run them on Dispatchers.IO.

import java.net.HttpURLConnection
import java.net.URL

object Net {
    /** Wikimedia asks scripts to say who they are. */
    const val AGENT = "SimpleTVLauncher/1.2 (Android TV; https://github.com/fardroid/AndroidTV_Simple_Launcher)"

    fun fetchText(url: String, timeoutMs: Int = 15_000, maxBytes: Int = 3 * 1048576): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", AGENT)
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val bytes = c.inputStream.use { readUpTo(it, maxBytes) }
            return decode(bytes, c.contentType)
        } finally {
            c.disconnect()
        }
    }

    /** At most `max` bytes of a stream (InputStream.readNBytes needs Android 13). */
    private fun readUpTo(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = input.read(buffer, 0, minOf(buffer.size, max - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** The text in the encoding the reply declares: in its Content-Type, else in an XML declaration (windows-1251 feeds), else UTF-8. */
    fun decode(bytes: ByteArray, contentType: String?): String {
        val fromHeader = contentType?.let { Regex("charset=\"?([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val fromXml = Regex("^\\s*<\\?xml[^>]*encoding=[\"']([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val charset = (fromXml ?: fromHeader)?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    /** The latest release, null when GitHub has none. Throws when it cannot be asked. */
    fun latestRelease(): UpdateInfo? = Updates.parse(fetchText(Updates.LATEST, 15_000, 200_000))

    /** Every news source, read one after another; one that cannot be read is left out (an error only when none can). */
    fun news(sources: List<String>, key: String): NewsData {
        val now = System.currentTimeMillis()
        val lists = ArrayList<List<FeedItem>>()
        sources.forEachIndexed { i, url ->
            runCatching { Feeds.parse(fetchText(url, 15_000), i) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { lists.add(it) }
        }
        if (lists.isEmpty()) throw java.io.IOException("nothing could be read")
        return NewsData(key, now, Feeds.join(lists, now))
    }

    private fun dailyQuote(lang: String): Pair<String, String>? {
        val d = Quotes.DAILY[lang] ?: return null
        if (d.feed) return Quotes.dailyFromFeed(fetchText(Quotes.feedUrl(lang), 20_000), lang)
        val html = Quotes.parseReply(fetchText(Quotes.parseUrl(lang, d.page!!), 20_000)) ?: return null
        return Quotes.dailyFromPage(html, lang)
    }

    /** A quote from the day's author; the next author when a page has nothing that fits (three tries). */
    private fun pickedQuote(lang: String, now: Long, store: Store): Pair<String, String> {
        var authors = Quotes.authorsFromJson(store.authors, lang, now)
        if (authors == null) {
            authors = Quotes.parseAuthors(fetchText(Quotes.authorsUrl(lang), 60_000)).takeIf { it.isNotEmpty() } ?: throw java.io.IOException("no authors")
            store.authors = Quotes.authorsToJson(lang, authors, now)
        }
        for (attempt in 0..2) {
            val title = Quotes.pickAuthor(authors, now, attempt) ?: break
            val quote = runCatching {
                val html = Quotes.parseReply(fetchText(Quotes.parseUrl(lang, title), 20_000))
                html?.let { Quotes.pickQuote(Quotes.quotesFromPage(it, lang, Quotes.authorName(title)), now) }
            }.getOrNull()
            if (quote != null) return quote to Quotes.authorName(title)
        }
        throw java.io.IOException("nothing to quote")
    }

    /** Today's quote in `lang`. */
    fun quote(lang: Language, store: Store, now: Long): Quote {
        val code = lang.code
        val day = Quotes.dayKey(now)
        val found = runCatching { dailyQuote(code) }.getOrNull()
        if (found != null) return Quote(found.first, found.second, code, day, "daily")
        val p = pickedQuote(code, now, store)
        return Quote(p.first, p.second, code, day, "picked")
    }

    fun weather(city: City): WeatherData =
        Weather.parse(fetchText(Weather.forecastUrl(city.lat, city.lon)), city.lat, city.lon, System.currentTimeMillis())
            ?: throw java.io.IOException("unreadable reply")

    fun searchCities(query: String): List<City> =
        if (query.trim().length < 2) emptyList() else Weather.parseCities(fetchText(Weather.geocodeUrl(query)))

    /** Every calendar of the list, read one after another; one that cannot be read is left out (an error only when none can). */
    fun calendars(urls: List<String>, key: String): CalendarData {
        val now = System.currentTimeMillis()
        val parts = urls.mapNotNull { raw ->
            val url = Cal.normalizeUrl(raw)
            runCatching { Cal.parse(fetchText(url, 30_000), url, 0, now) }.getOrNull()
        }
        if (parts.isEmpty()) throw java.io.IOException("nothing could be read")
        return Cal.join(key, parts, now)
    }
}
