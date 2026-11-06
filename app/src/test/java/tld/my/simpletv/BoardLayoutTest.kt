package tld.my.simpletv

import org.junit.Assert.assertEquals
import org.junit.Test
import tld.my.simpletv.Plates.CardId

// The same cases as scripts/test-boardlayout.mjs of the webOS version (v1.10.1), so both place the cards alike.
class BoardLayoutTest {
    private val clock = Plates.ClockShape(160, true, 100, false)
    private fun on(weather: Boolean = false, calendar: Boolean = false, news: Boolean = false, quote: Boolean = false, c: Plates.ClockShape = clock) =
        Plates.Cards(weather, calendar, news, quote, c)
    private fun Plates.Board.r(id: CardId) = cards[id]?.let { listOf(it.x, it.y, it.w, it.h) }
    private fun Plates.Board.ids() = columns.map { c -> c.cards.joinToString("+") { it.first.name.lowercase() } }

    @Test fun twoColumnsAsBefore() {
        var l = Plates.boardLayout(on(true, true), 40)
        assertEquals(listOf(listOf(200, 40, 880, 184), listOf(200, 264, 880, 462), listOf(1120, 40, 600, 686)), listOf(l.r(CardId.CLOCK), l.r(CardId.WEATHER), l.r(CardId.CALENDAR)))
        l = Plates.boardLayout(on(true), 40)
        assertEquals(listOf(520, 40, 880, 184), l.r(CardId.CLOCK)); assertEquals(1, l.columns.size)
        assertEquals(listOf(520, 40, 880, 184), Plates.boardLayout(on(), 40).r(CardId.CLOCK))
        assertEquals(74, Plates.boardLayout(on(true, true), 74).r(CardId.CALENDAR)!![1])
    }

    @Test fun quoteAndNews() {
        var l = Plates.boardLayout(on(true, true, quote = true), 40)
        assertEquals(listOf(listOf(1120, 40, 600, 470), listOf(1120, 550, 600, 176)), listOf(l.r(CardId.CALENDAR), l.r(CardId.QUOTE)))
        assertEquals(686, Plates.boardLayout(on(true, true), 40).r(CardId.CALENDAR)!![3])
        l = Plates.boardLayout(on(true, news = true), 40)
        assertEquals(listOf("clock+weather", "news"), l.ids()); assertEquals(listOf(1120, 40, 600, 686), l.r(CardId.NEWS))
        l = Plates.boardLayout(on(true, news = true, quote = true), 40)
        assertEquals(listOf(listOf(1120, 40, 600, 470), listOf(1120, 550, 600, 176)), listOf(l.r(CardId.NEWS), l.r(CardId.QUOTE)))
        assertEquals(listOf(1120, 40, 600, 686), Plates.boardLayout(on(quote = true), 40).r(CardId.QUOTE))
    }

    @Test fun threeColumns() {
        var l = Plates.boardLayout(on(true, true, true), 40)
        assertEquals(true, l.narrow); assertEquals(listOf("clock+weather", "calendar", "news"), l.ids())
        assertEquals(listOf(80 to 560, 680 to 560, 1280 to 560), l.columns.map { it.x to it.w })
        assertEquals(listOf(listOf(1280, 40, 560, 686), listOf(680, 40, 560, 686)), listOf(l.r(CardId.NEWS), l.r(CardId.CALENDAR)))
        l = Plates.boardLayout(on(true, true, true, true), 40)
        assertEquals(listOf(listOf(1280, 40, 560, 470), listOf(1280, 550, 560, 176)), listOf(l.r(CardId.NEWS), l.r(CardId.QUOTE)))
        val clockH = l.r(CardId.CLOCK)!![3]
        val w = l.r(CardId.WEATHER)!!
        assertEquals(Plates.narrowClockSize(clock) + 24 + 38 + 8, clockH)
        assertEquals(listOf(40 + clockH + 40, 726), listOf(w[1], w[1] + w[3]))
        assertEquals(listOf(181, 157, 120), listOf(Plates.narrowClockSize(clock.copy(size = 220)), Plates.narrowClockSize(clock.copy(size = 220, period = true)), Plates.narrowClockSize(clock.copy(size = 120))))
        assertEquals(184, Plates.clockHeight(clock.copy(showDate = false), true))
        assertEquals(listOf("clock", "calendar", "news"), Plates.boardLayout(on(calendar = true, news = true), 40).ids())
    }

    @Test fun theRemote() {
        val l = Plates.boardLayout(on(true, true, true, true), 40)
        assertEquals(listOf(0, 1, 2), listOf(Plates.columnAt(l, 100f), Plates.columnAt(l, 960f), Plates.columnAt(l, 1900f)))
        assertEquals(listOf(CardId.WEATHER, CardId.CALENDAR, CardId.NEWS), (0..2).map { Plates.entryCard(l, it) })
        assertEquals(CardId.CALENDAR, Plates.besideCard(l, CardId.WEATHER, 1)); assertEquals(CardId.NEWS, Plates.besideCard(l, CardId.CALENDAR, 1))
        assertEquals(null, Plates.besideCard(l, CardId.NEWS, 1)); assertEquals(CardId.WEATHER, Plates.besideCard(l, CardId.CALENDAR, -1))
        assertEquals(null, Plates.nextInColumn(l, CardId.WEATHER, -1))   // the clock above it is only read
        assertEquals(null, Plates.nextInColumn(l, CardId.NEWS, 1))     // the quote under the news is only read
    }
}
