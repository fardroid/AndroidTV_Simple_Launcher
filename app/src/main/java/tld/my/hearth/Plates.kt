package tld.my.hearth

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

data class PlateLooks(val clock: PlateLook, val weather: PlateLook, val calendar: PlateLook)

object Plates {
    const val MAP_W = 16
    const val MAP_H = 9
    const val SCREEN_W = 1920
    const val SCREEN_H = 1080
    const val BOARD_HEIGHT = 686
    const val TILE = 150
    const val TILE_LABEL = 25
    const val FOCUS_SCALE = 1.12f

    /** `calendarShown`: the calendar card is on the screen (its rect is there either way) */
    data class Board(val clock: Rect, val weather: Rect, val calendar: Rect, val calendarShown: Boolean = true)

    /** The clock's height follows its size (the digits and 12 px above and below); the weather card takes the rest of the column. */
    fun clockHeight(c: Config) = c.clockSize + 24

    fun rects(hasWeather: Boolean, hasCalendar: Boolean, top: Int, clockH: Int): Board {
        val leftWidth = 880; val calendarWidth = 600; val gap = 40
        val total = leftWidth + if (hasCalendar) gap + calendarWidth else 0
        val x = (SCREEN_W - total) / 2
        return Board(
            clock = Rect(x, top, leftWidth, clockH),
            weather = Rect(x, top + clockH + gap, leftWidth, if (hasWeather) BOARD_HEIGHT - clockH - gap else 0),
            calendar = Rect(x + leftWidth + gap, top, calendarWidth, BOARD_HEIGHT),
            calendarShown = hasCalendar,
        )
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
    fun looks(mode: PlateMode, map: FloatArray?, board: Board): PlateLooks = when (mode) {
        PlateMode.LIGHT -> PlateLooks(LIGHT, LIGHT, LIGHT)
        PlateMode.DARK -> PlateLooks(DARK, DARK, DARK)
        PlateMode.AUTO -> if (map == null) PlateLooks(LIGHT, LIGHT, LIGHT) else {
            val shown = listOfNotNull(board.clock, board.weather.takeIf { it.h > 0 }, board.calendar.takeIf { board.calendarShown })
            val look = lookFor(shown.maxOf { regionBrightness(map, it) })
            PlateLooks(look, look, look)
        }
    }
}
