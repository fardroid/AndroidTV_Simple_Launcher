package tld.my.simpletv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsQuoteTest {
    private val now = Feeds.parseDate("Fri, 06 Nov 2026 12:00:00 GMT")

    @Test fun dates() {
        assertEquals(1793966400000L, Feeds.parseDate("Fri, 06 Nov 2026 12:00:00 GMT"))
        assertEquals(Feeds.parseDate("2026-11-06T12:00:00Z"), Feeds.parseDate("Fri, 06 Nov 2026 15:00:00 +0300"))
        assertEquals(Feeds.parseDate("2026-11-06T12:00:00Z"), Feeds.parseDate("2026-11-06T13:00:00+01:00"))
        assertEquals(0L, Feeds.parseDate("yesterday"))
    }

    @Test fun rss() {
        val xml = """<?xml version="1.0"?><rss><channel><title>The Daily</title>
            <item><title><![CDATA[Rain &amp; wind - Daily Post]]></title><link>https://a.example/1</link>
            <pubDate>Fri, 06 Nov 2026 11:00:00 GMT</pubDate><source url="x">Daily Post</source>
            <description>&lt;p&gt;Some &lt;b&gt;rain&lt;/b&gt; today.&lt;/p&gt;</description></item>
            <item><title>Second</title><link>https://a.example/2</link><pubDate>Fri, 06 Nov 2026 10:00:00 GMT</pubDate></item>
            </channel></rss>"""
        val items = Feeds.parse(xml, 0)
        assertEquals(2, items.size)
        assertEquals("Rain & wind", items[0].title)
        assertEquals("Daily Post", items[0].source)
        assertEquals("Some rain today.", items[0].summary)
        assertEquals("The Daily", items[1].source)
        assertEquals("https://a.example/1", items[0].link)
    }

    @Test fun atom() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title>
            <entry><title>Post</title><link rel="self" href="https://x/self"/><link rel="alternate" href="https://x/post"/>
            <updated>2026-11-06T11:30:00Z</updated><summary>Short text</summary></entry></feed>"""
        val i = Feeds.parse(xml, 3).single()
        assertEquals("https://x/post", i.link); assertEquals("Blog", i.source); assertEquals(3, i.feed)
        assertEquals(Feeds.parseDate("2026-11-06T11:30:00Z"), i.at)
    }

    @Test fun joinNewestFirstWithoutRepeats() {
        fun item(t: String, l: String, at: Long) = FeedItem(t, l, at, "s", "", 0)
        val a = listOf(item("Old", "l1", now - 50 * 3_600_000L), item("A", "l2", now - 3_600_000L))
        val b = listOf(item("a!", "l3", now - 1000), item("B", "l4", now - 60_000L), item("Future", "l5", now + 5 * 3_600_000L))
        val out = Feeds.join(listOf(a, b), now)
        assertEquals(listOf("B", "A"), out.map { it.title })
    }

    @Test fun editions() {
        assertEquals("DE", Feeds.countryOf("", "de-DE", Language.DE))
        assertEquals("DE", Feeds.countryOf("", "en-US", Language.DE))      // German launcher on a US box: German news
        assertEquals("US", Feeds.countryOf("", "en-US", Language.EN))
        assertEquals("FR", Feeds.countryOf("FR", "en-US", Language.EN))
        assertEquals(52, Feeds.COUNTRIES.size)
        assertEquals("https://news.google.com/rss?hl=fr-CA&gl=CA&ceid=CA%3Afr", Feeds.googleNewsUrl("CA", Language.FR))
        assertEquals("https://news.google.com/rss?hl=en-CA&gl=CA&ceid=CA%3Aen", Feeds.googleNewsUrl("CA", Language.EN))
        assertEquals("https://example.com/x", Feeds.normalizeUrl(" feed://example.com/x "))
        assertTrue(Feeds.validUrl("https://example.com/feed")); assertFalse(Feeds.validUrl("ftp://x"))
    }

    @Test fun newsCopyRoundTrips() {
        val d = NewsData("k", 5, listOf(FeedItem("T", "l", 7, "s", "sum", 1)))
        assertEquals(d, Feeds.fromJson(Feeds.toJson(d)))
        assertNull(Feeds.usable(d, "other")); assertNotNull(Feeds.usable(d, "k"))
    }

    @Test fun ages() {
        assertEquals(12 to Feeds.Unit.MIN, Feeds.ageOf(now - 12 * 60_000L, now))
        assertEquals(3 to Feeds.Unit.H, Feeds.ageOf(now - 3 * 3_600_000L, now))
        assertNull(Feeds.ageOf(0, now))
    }

    @Test fun decodeAndTextOfHtml() {
        assertEquals("a & b — «c» ’", HtmlText.decodeEntities("a &amp; b &#8212; &laquo;c&raquo; &#x2019;"))
        assertEquals("One\nTwo", HtmlText.toText("<p>One</p><p>Two<sup>1</sup></p><style>x{}</style>"))
    }

    private val authorPage = """
        <h2>Quotes</h2>
        <ul><li>The only thing we have to fear is fear itself, and that is not a small thing.<ul><li>Source: a speech</li></ul></li>
        <li>Short.</li>
        <li>Another sentence that is long enough to be a quote. (Letter, 12 May 1900)</li></ul>
        <h2>Quotes about him</h2><ul><li>A man who was very well known to all people around him.</li></ul>
        <h2>External links</h2><ul><li>https://example.com is a link that is long enough to be here</li></ul>"""

    @Test fun quotesOfAnAuthorPage() {
        val q = Quotes.quotesFromPage(authorPage, "en", "Someone")
        assertEquals(listOf(
            "The only thing we have to fear is fear itself, and that is not a small thing.",
            "Another sentence that is long enough to be a quote.",
        ), q)
    }

    @Test fun sectionsToSkip() {
        assertTrue(Quotes.skippedSection("Quotes about him"))
        assertTrue(Quotes.skippedSection("Цитаты / Высказывания о нём"))
        assertTrue(Quotes.skippedSection("Works / О Достоевском", "Достоевский"))
        assertFalse(Quotes.skippedSection("Works / Letters"))
    }

    @Test fun cleanAndDrop() {
        assertEquals("Hello there", Quotes.cleanQuote("“Hello there” – Book 5", "en"))
        assertEquals("Real thought here.", Quotes.dropSource("Real thought here. — «Work», 358d"))
        assertEquals("Знание — сила.", Quotes.dropSource("Знание — сила. (Дневник: 12 июля 1900 г.)"))
        assertEquals("Said this.", Quotes.dropSource("— Said this."))
    }

    @Test fun dailyFromMainPage() {
        val html = """<div><h2>Quote of the day</h2><div><p>“Life is what happens.” </p><p>— John Lennon</p></div><h2>Other</h2></div>"""
        assertEquals("Life is what happens." to "John Lennon", Quotes.dailyFromPage(html, "en"))
        val inline = """<h2>Quote of the day</h2><p>Be kind to everyone you meet — Plato</p>"""
        assertEquals("Be kind to everyone you meet" to "Plato", Quotes.dailyFromPage(inline, "en"))
        // a dash inside the quote is not the author's (Wikiquote's page of 6 November 2026)
        val dashed = """<h2>Quote of the day</h2><table><tr><td>“</td><td>Others can stop you temporarily — you are the only one who can do it permanently. </td><td>”</td></tr><tr><td>Zig Ziglar</td></tr></table>"""
        assertEquals("Others can stop you temporarily — you are the only one who can do it permanently." to "Zig Ziglar", Quotes.dailyFromPage(dashed, "en"))
        assertNull(Quotes.dailyFromPage("<h2>Elsewhere</h2><p>x</p>", "en"))
        assertNull(Quotes.dailyFromPage("<h2>Цитата дня</h2><p>Цитату ще не обрано</p>", "uk"))
    }

    @Test fun dailyFromItalianFeed() {
        val xml = """<feed><entry><summary>old</summary></entry><entry><summary type="html">&lt;p&gt;La vita è bella.&lt;/p&gt;&lt;p&gt;— Autore&lt;/p&gt;</summary></entry></feed>"""
        assertEquals("La vita è bella." to "Autore", Quotes.dailyFromFeed(xml, "it"))
    }

    @Test fun pickingIsSteadyAllDay() {
        val authors = (1..400).map { "A$it" }
        val day = Quotes.dayNumber(now)
        assertEquals(Quotes.pickAuthor(authors, now), Quotes.pickAuthor(authors, now + 3_600_000L).takeIf { Quotes.dayNumber(now + 3_600_000L) == day } ?: Quotes.pickAuthor(authors, now))
        assertTrue(Quotes.pickAuthor(authors, now, 0) != Quotes.pickAuthor(authors, now, 1))
        assertNull(Quotes.pickAuthor(emptyList(), now))
        assertEquals("b", Quotes.pickQuote(listOf("b"), now))
    }

    @Test fun savedQuote() {
        val q = Quote("t", "a", "en", Quotes.dayKey(now), "daily")
        assertEquals(q, Quotes.fromJson(Quotes.toJson(q)))
        assertNotNull(Quotes.todays(q, "en", now)); assertNull(Quotes.todays(q, "de", now)); assertNotNull(Quotes.shown(q, "en"))
        assertNull(Quotes.todays(q, "en", now + 3 * 86_400_000L))
        assertNull(Quotes.fromJson(Quotes.toJson(q).replace("\"rules\":2", "\"rules\":1")))
    }

    @Test fun authorsList() {
        val reply = """{"results":{"bindings":[{"title":{"value":"Goethe"}},{"title":{"value":"Kant"}},{"title":{"value":"Goethe"}}]}}"""
        assertEquals(listOf("Goethe", "Kant"), Quotes.parseAuthors(reply))
        val saved = Quotes.authorsToJson("de", listOf("X"), now)
        assertEquals(listOf("X"), Quotes.authorsFromJson(saved, "de", now + 1000))
        assertNull(Quotes.authorsFromJson(saved, "en", now)); assertNull(Quotes.authorsFromJson(saved, "de", now + 31 * 86_400_000L))
    }

    @Test fun charsetOfAFeed() {
        val cp1251 = "<?xml version=\"1.0\" encoding=\"windows-1251\"?><t>Привет</t>".toByteArray(charset("windows-1251"))
        assertTrue(Net.decode(cp1251, "text/xml").contains("Привет"))
        assertTrue(Net.decode("Привет".toByteArray(charset("windows-1251")), "text/xml; charset=windows-1251").contains("Привет"))
        assertTrue(Net.decode("Привет".toByteArray(), null).contains("Привет"))
    }
}
