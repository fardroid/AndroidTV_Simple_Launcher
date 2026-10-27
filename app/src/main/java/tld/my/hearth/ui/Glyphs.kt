package tld.my.hearth.ui

// Line icons, drawn from the same 24 x 24 SVG paths as the webOS app (src/components/Glyph.tsx).

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import tld.my.hearth.WeatherIcon

private val cache = HashMap<String, Path>()
private fun path(d: String): Path = cache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

/** Paths and circles (cx, cy, r) of a 24 x 24 icon. */
private class Icon(val paths: List<String>, val circles: List<Triple<Float, Float, Float>> = emptyList())

private const val CLOUD_LOW = "M7 14.5h9.5a3.4 3.4 0 0 0 .3-6.8 4.9 4.9 0 0 0-9.4 1.2A2.8 2.8 0 0 0 7 14.5z"

private fun weatherIcon(icon: WeatherIcon): Icon = when (icon) {
    WeatherIcon.SUN -> Icon(listOf("M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8"), listOf(Triple(12f, 12f, 4f)))
    WeatherIcon.MOON -> Icon(listOf("M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"))
    WeatherIcon.PARTLY -> Icon(listOf("M8 4.5v1.6M3.3 9H4.9M4.7 5.7l1.1 1.1M11.3 5.7l-1.1 1.1", "M9 19h8.3a3.4 3.4 0 0 0 .3-6.8 4.8 4.8 0 0 0-9.2 1.1A2.9 2.9 0 0 0 9 19z"), listOf(Triple(8f, 9f, 2.6f)))
    WeatherIcon.CMOON -> Icon(listOf("M11.5 3.8A4.6 4.6 0 1 0 15 11a4.6 4.6 0 0 1-3.5-7.2z", "M9 19.5h8.3a3.4 3.4 0 0 0 .3-6.8 4.8 4.8 0 0 0-9.2 1.1A2.9 2.9 0 0 0 9 19.5z"))
    WeatherIcon.CLOUD -> Icon(listOf("M7 18.5h9.5a3.7 3.7 0 0 0 .4-7.4 5.3 5.3 0 0 0-10.2 1.3A3.1 3.1 0 0 0 7 18.5z"))
    WeatherIcon.FOG -> Icon(listOf(CLOUD_LOW, "M5 18h14M8 21h8"))
    WeatherIcon.RAIN -> Icon(listOf(CLOUD_LOW, "M8.5 17.5l-1 2.5M12.5 17.5l-1 2.5M16.5 17.5l-1 2.5"))
    WeatherIcon.SNOW -> Icon(listOf(CLOUD_LOW, "M8.5 18v.2M12.5 19.5v.2M16.5 18v.2"))
    WeatherIcon.STORM -> Icon(listOf(CLOUD_LOW, "M12.5 14.5l-2 3.2h3l-2 3.3"))
}

/** The sun and the moon get a tint, rain a blue; the rest is drawn in the text colour. */
private fun tint(icon: WeatherIcon, base: Color): Color = when (icon) {
    WeatherIcon.SUN, WeatherIcon.PARTLY -> Color(0xFFFFC857)
    WeatherIcon.MOON, WeatherIcon.CMOON -> Color(0xFFDFE8FF)
    WeatherIcon.RAIN, WeatherIcon.STORM -> Color(0xFF8CC4FF)
    else -> base
}

private fun DrawScope.drawIcon(icon: Icon, color: Color, strokeWidth: Float) {
    val k = size.minDimension / 24f
    scale(k, k, pivot = Offset.Zero) {
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        icon.paths.forEach { drawPath(path(it), color, style = stroke) }
        icon.circles.forEach { (x, y, r) -> drawCircle(color, r, Offset(x, y), style = stroke) }
    }
}

@Composable
fun WeatherGlyph(icon: WeatherIcon, modifier: Modifier, color: Color = Hue.textPrimary) {
    val shape = weatherIcon(icon)
    val c = tint(icon, color)
    Canvas(modifier) { drawIcon(shape, c, 1.5f) }
}

enum class Glyph { GEAR, TV, CHEVRON_LEFT, CHEVRON_RIGHT }

private fun glyphIcon(g: Glyph): Icon = when (g) {
    Glyph.GEAR -> Icon(listOf("M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"), listOf(Triple(12f, 12f, 3.2f)))
    Glyph.TV -> Icon(listOf("M4.6 4.5h14.8a1.8 1.8 0 0 1 1.8 1.8v8.6a1.8 1.8 0 0 1-1.8 1.8H4.6a1.8 1.8 0 0 1-1.8-1.8V6.3a1.8 1.8 0 0 1 1.8-1.8z", "M8 20.2h8M12 16.7v3.5M7 8.6h4.6M14.6 8.6H17M7 12.6H9.2M12.2 12.6H17"), listOf(Triple(13.1f, 8.6f, 1.1f), Triple(10.7f, 12.6f, 1.1f)))
    Glyph.CHEVRON_LEFT -> Icon(listOf("M15 5l-7 7 7 7"))
    Glyph.CHEVRON_RIGHT -> Icon(listOf("M9 5l7 7-7 7"))
}

@Composable
fun LineGlyph(glyph: Glyph, modifier: Modifier, color: Color = Hue.textPrimary, strokeWidth: Float = 1.6f) {
    val shape = glyphIcon(glyph)
    Canvas(modifier) { drawIcon(shape, color, strokeWidth) }
}
