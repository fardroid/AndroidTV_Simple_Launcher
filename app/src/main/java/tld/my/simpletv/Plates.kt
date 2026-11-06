package tld.my.simpletv

// Where the clock, weather and calendar plates stand on the 1920 x 1080 screen, and the colour of each plate. In "auto" a plate
// takes its colour from the brightness of the wallpaper behind it: light where the picture is dark, dark where it is light.
// A port of src/lib/plates.ts and src/lib/boardlayout.ts.

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle on the design screen: left, top, width, height. */
data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

/** A plate's colour (ARGB), and the layer laid over it while the remote is on it. */
data class PlateLook(val bg: Long, val focus: Long)

object Plates {
    const val MAP_W = 16
    const val MAP_H = 9
    const val SCREEN_W = 1920
    const val SCREEN_H = 1080
    const val BOARD_HEIGHT = 686
    const val TILE = 150
    const val TILE_LABEL = 25
    const val FOCUS_SCALE = 1.12f

    const val BOARD_GAP = 40
    private const val LEFT_WIDTH = 880
    private const val RIGHT_WIDTH = 600
    private const val THIRD_WIDTH = 560
    /** the quote of the day under the calendar or the news: the same height in two columns and in three, so its text (fitted to
     *  the card) comes out the same size whichever cards are on */
    const val QUOTE_HEIGHT = 176
    private const val CLOCK_PAD = 12
    private const val CLOCK_SIDE = 40

    enum class CardId { CLOCK, WEATHER, CALENDAR, NEWS, QUOTE }

    /** What the clock shows, which sets its height. */
    data class ClockShape(val size: Int, val showDate: Boolean, val dateSize: Int, val period: Boolean)

    data class Cards(val weather: Boolean, val calendar: Boolean, val news: Boolean, val quote: Boolean, val clock: ClockShape)

    class Column(val x: Int, val w: Int, val cards: List<Pair<CardId, Rect>>)

    /** Where the cards stand: in columns, 40 px apart, all ending on the same line (src/lib/boardlayout.ts of the webOS app).
     *  Two columns (880 + 600): the clock and the weather; the calendar or the news, with the quote under it. Three columns of 560
     *  when the calendar and the news are both on: the clock and the weather, the calendar, the news (with the quote under it). */
    class Board(val narrow: Boolean, val columns: List<Column>) {
        val cards: Map<CardId, Rect> = columns.flatMap { it.cards }.toMap()
        fun rect(id: CardId): Rect? = cards[id]
    }

    fun clockShape(c: Config) = ClockShape(c.clockSize, c.showDate, c.dateSize, !c.clock24)

    /** The size of the digits in a narrow column: as chosen, unless the time would not fit the width. */
    fun narrowClockSize(clock: ClockShape): Int = min(clock.size, floor((THIRD_WIDTH - 2 * CLOCK_SIDE) / (if (clock.period) 3.05 else 2.65)).toInt())

    /** The height of the date's line under the time in a narrow column. */
    fun narrowDateLine(clock: ClockShape): Int = (32.0 * clock.dateSize / 100 * 1.2).roundToInt()

    /** The clock card's height. Wide: the digits beside the date. Narrow: the date under the digits, on one line. */
    fun clockHeight(clock: ClockShape, narrow: Boolean): Int =
        if (!narrow) clock.size + 2 * CLOCK_PAD else narrowClockSize(clock) + 2 * CLOCK_PAD + if (clock.showDate) narrowDateLine(clock) + 8 else 0

    fun clockHeight(c: Config) = clockHeight(clockShape(c), false)

    /** The cards that share a column take its height: fixed ones first, the others share what is left. */
    private fun stack(ids: List<CardId>, x: Int, w: Int, top: Int, fixed: (CardId, Boolean) -> Int?): Column {
        val alone = ids.size == 1
        val heights = ids.map { fixed(it, alone) }
        val flexible = heights.count { it == null }
        val used = heights.sumOf { it ?: 0 } + BOARD_GAP * (ids.size - 1)
        val share = if (flexible > 0) max(0, (BOARD_HEIGHT - used) / flexible) else 0
        var y = top
        return Column(x, w, ids.mapIndexed { i, id ->
            val h = heights[i] ?: share
            val r = Rect(x, y, w, h)
            y += h + BOARD_GAP
            id to r
        })
    }

    /** Where every card stands on the 1920 x 1080 screen; `top` is the block's top (boardTop). */
    fun boardLayout(on: Cards, top: Int): Board {
        val three = on.news && on.calendar
        val lists = ArrayList<List<CardId>>()
        lists.add(if (on.weather) listOf(CardId.CLOCK, CardId.WEATHER) else listOf(CardId.CLOCK))
        if (three) {
            lists.add(listOf(CardId.CALENDAR))
            lists.add(if (on.quote) listOf(CardId.NEWS, CardId.QUOTE) else listOf(CardId.NEWS))
        } else {
            val right = listOfNotNull(CardId.CALENDAR.takeIf { on.calendar }, CardId.NEWS.takeIf { on.news }, CardId.QUOTE.takeIf { on.quote })
            if (right.isNotEmpty()) lists.add(right)
        }
        val widths = if (three) listOf(THIRD_WIDTH, THIRD_WIDTH, THIRD_WIDTH) else listOf(LEFT_WIDTH, RIGHT_WIDTH)
        val total = lists.indices.sumOf { widths[it] } + BOARD_GAP * (lists.size - 1)
        var x = ((SCREEN_W - total) / 2f).roundToInt()
        val fixed = { id: CardId, alone: Boolean ->
            when (id) {
                CardId.CLOCK -> clockHeight(on.clock, three)
                CardId.QUOTE -> if (alone) BOARD_HEIGHT else QUOTE_HEIGHT
                else -> null
            }
        }
        val columns = lists.mapIndexed { i, ids ->
            stack(ids, x, widths[i], top, fixed).also { x += widths[i] + BOARD_GAP }
        }
        return Board(three, columns)
    }

    /** The cards the remote can step onto (the clock and the quote are only read). */
    val FOCUSABLE = listOf(CardId.WEATHER, CardId.CALENDAR, CardId.NEWS)

    /** The card the remote steps onto from the app row in column `index`: the lowest one it can step onto. */
    fun entryCard(board: Board, index: Int): CardId? = board.columns.getOrNull(index)?.cards?.map { it.first }?.lastOrNull { it in FOCUSABLE }

    /** The column whose middle is closest to `x` among those with a card the remote can step onto (-1: none). */
    fun columnAt(board: Board, x: Float): Int {
        var best = -1; var distance = Float.MAX_VALUE
        board.columns.forEachIndexed { i, c ->
            if (c.cards.none { it.first in FOCUSABLE }) return@forEachIndexed
            val d = if (x < c.x) c.x - x else if (x > c.x + c.w) x - c.x - c.w else 0f
            if (d < distance) { distance = d; best = i }
        }
        return best
    }

    /** The card the remote steps onto from `id` going left (-1) or right (+1), in the nearest column that way (null: none). */
    fun besideCard(board: Board, id: CardId, step: Int): CardId? {
        val index = board.columns.indexOfFirst { c -> c.cards.any { it.first == id } }
        if (index == -1) return null
        var i = index + step
        while (i in board.columns.indices) {
            entryCard(board, i)?.let { return it }
            i += step
        }
        return null
    }

    /** The card above (-1) or below (+1) `id` in its column that the remote can step onto (null: none). */
    fun nextInColumn(board: Board, id: CardId, step: Int): CardId? {
        for (column in board.columns) {
            val i = column.cards.indexOfFirst { it.first == id }
            if (i == -1) continue
            var j = i + step
            while (j in column.cards.indices) {
                if (column.cards[j].first in FOCUSABLE) return column.cards[j].first
                j += step
            }
            return null
        }
        return null
    }

    /** Size of the tiles on the home row, and of the names under them. */
    data class Dock(val tileW: Int, val tileH: Int, val label: Int, val labelBlock: Int)

    fun dock(c: Config): Dock {
        val scale = c.iconScale / 100f
        val h = (TILE * scale).roundToInt()
        val w = if (c.tileShape == TileShape.BANNER) (h * 16f / 9f).roundToInt() else h
        val label = min(30, max(16, (TILE_LABEL * scale).roundToInt()))
        return Dock(w, h, label, if (c.showLabels) (label * 2.4f + 14).roundToInt() else 0)
    }

    /** The board keeps its size; it stands so that the space above it and the space down to the selected (grown) tile are the same. */
    fun boardTop(c: Config): Int {
        val d = dock(c)
        val rowsTop = SCREEN_H - c.bottomMargin - (d.tileH + d.labelBlock)
        val biggestTop = rowsTop - d.tileH * (FOCUS_SCALE - 1)
        return max(16, ((biggestTop - BOARD_HEIGHT) / 2).roundToInt())
    }

    /** Pixel shift (OLED care): the loop of eight places the home screen goes round, one step a minute. A port of the webOS
     *  launcher's pixelshift.ts. */
    private val SHIFTS = listOf(0 to 0, 4 to 2, 8 to 0, 4 to -2, 0 to -4, -4 to -2, -8 to 0, -4 to 2)

    /** The offset in design pixels for the minute of `ms`. */
    fun shiftAt(ms: Long): Pair<Int, Int> = SHIFTS[Math.floorMod(Math.floorDiv(ms, 60_000L), SHIFTS.size.toLong()).toInt()]

    /** Average brightness (0 to 1) of the map under a rectangle of the screen. */
    fun regionBrightness(map: FloatArray, r: Rect): Float {
        val x0 = max(0, floor(r.x.toFloat() / SCREEN_W * MAP_W).toInt())
        val x1 = min(MAP_W, max(x0 + 1, ceil((r.x + r.w).toFloat() / SCREEN_W * MAP_W).toInt()))
        val y0 = max(0, floor(r.y.toFloat() / SCREEN_H * MAP_H).toInt())
        val y1 = min(MAP_H, max(y0 + 1, ceil((r.y + r.h).toFloat() / SCREEN_H * MAP_H).toInt()))
        var sum = 0f; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { sum += map[y * MAP_W + x]; n++ }
        return if (n > 0) sum / n else 0f
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int): Long = ((a * 255).roundToInt().toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

    private val LIGHT = PlateLook(argb(0.11f, 255, 255, 255), argb(0.06f, 255, 255, 255))
    private val DARK = PlateLook(argb(0.55f, 12, 18, 46), argb(0.18f, 14, 22, 54))

    /** Light under dark, dark under light, and in between a dark one that grows stronger the brighter it gets. */
    fun lookFor(brightness: Float): PlateLook {
        if (brightness <= 0.34f) return LIGHT
        val alpha = min(0.66f, 0.3f + (brightness - 0.34f) * 0.9f)
        return PlateLook(argb(alpha, 14, 18, 40), argb(min(0.3f, 0.05f + alpha * 0.25f), 14, 18, 40))
    }

    /** `map` is the wallpaper's brightness grid (null: the plain dark background). In AUTO all the plates get one look,
     *  chosen for the brightest place any of them covers (white text has to read on every one); a light plate next to a
     *  dark one looks like a mistake. */
    fun looks(mode: PlateMode, map: FloatArray?, board: Board): PlateLook = when (mode) {
        PlateMode.LIGHT -> LIGHT
        PlateMode.DARK -> DARK
        PlateMode.AUTO -> if (map == null) LIGHT else lookFor(board.cards.values.filter { it.h > 0 }.maxOf { regionBrightness(map, it) })
    }
}
