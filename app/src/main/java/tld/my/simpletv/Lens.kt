package tld.my.simpletv

// Liquid glass (Settings → Appearance → Glass: Liquid): the frosted wallpaper seen through a slab of glass whose edge is a convex
// bevel, worked out the way Simple LG launcher 2.2.6 (and the web's liquid glass demos: archisvaze/liquid-glass,
// gentpan/liquidglass, ybouane/liquidglass) does it. The flat middle shows what is under it as it is; over the bevel, a ray coming
// straight down through the tilted surface is bent (Snell's law) and goes down through the glass's height there, so the picture
// is taken from further inside and stretched out towards the edge, each colour a little differently (blue bends more). The bevel
// is never wider than the corner's radius, so it follows the corner exactly. No light is drawn on the glass: only the lens.
//
// The webOS launcher does this in a fragment shader. Android draws shaders of its own (AGSL) only from Android 13, and the boxes
// this runs on are often older (Android 11 on a Mi TV Stick), so the bevel is worked out here instead, once for each picture and
// each card, as a picture of its own: the edge of the card only (the middle is the plain frost), see Plate in ui/Wallpaper.kt.

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Lens {
    /** The shape: the bevel's width (never more than the corner's radius) and the glass's thickness, in design px; how much the
     *  colours part (0-1); the index of refraction (archisvaze/liquid-glass's default, 3). */
    // archisvaze/liquid-glass's default, picked on a page of sliders (as Simple LG launcher 2.2.7): the cards' corners 60, a bevel
    // as wide (kept a pixel under), 80 thick, index 3
    const val BEVEL = 60f
    const val THICKNESS = 80f
    const val DISPERSION = 1f
    const val IOR = 3f
    const val RADIUS = tld.my.simpletv.ui.PLATE_RADIUS
    private const val SAMPLES = 512

    /** The surface over the bevel: a squircle, 0 at the rim, 1 where the flat top starts. */
    private fun height(t: Double): Double { val s = 1 - t; return (1 - s * s * s * s).pow(0.25) }

    /** How far inwards (design px) the picture is taken from at depth t (0-1) into a bevel `bevel` px wide: the ray through the
     *  surface, tilted by its slope, is bent by the glass and goes down through the glass's height there; 0 at the very rim (no
     *  height) and on the flat top. */
    fun shift(t: Double, bevel: Double, thickness: Double = THICKNESS.toDouble(), ior: Double = IOR.toDouble()): Double {
        if (t >= 1) return 0.0
        val h = height(t)
        val dh = (height(min(t + 0.001, 1.0)) - h) / 0.001
        val tilt = atan(dh * thickness / bevel)
        val bent = asin((sin(tilt) / ior).coerceIn(-1.0, 1.0))
        return h * thickness * (tan(tilt) - tan(bent))
    }

    /** The signed distance to a rounded box of half size (bx, by), corner radius r, centred on 0 (negative inside). */
    fun box(px: Float, py: Float, bx: Float, by: Float, r: Float): Float {
        val qx = abs(px) - bx + r; val qy = abs(py) - by + r
        val ox = max(qx, 0f); val oy = max(qy, 0f)
        return sqrt(ox * ox + oy * oy) + min(max(qx, qy), 0f) - r
    }

    /** The bevel of a card `rect` (design px) over the frosted picture `frost` (which covers the whole 1920 x 1080 screen), as
     *  a picture `scale` times the card's size: the bent picture on the bevel, see-through inside it (and outside the rounded
     *  card). */
    fun bevel(frost: Bitmap, rect: Rect, scale: Float): Bitmap {
        val w = max(1, (rect.w * scale).roundToInt()); val h = max(1, (rect.h * scale).roundToInt())
        val fw = frost.width; val fh = frost.height
        val px = IntArray(fw * fh)
        frost.getPixels(px, 0, fw, 0, 0, fw, fh)
        val out = IntArray(w * h)
        val hx = rect.w / 2f; val hy = rect.h / 2f
        val radius = min(RADIUS, min(hx, hy))
        val bevel = max(1f, min(BEVEL, min(radius, min(hx, hy)) - 1f))
        // the shift by depth, for the three colours (red bends least, blue most)
        val k = DISPERSION * 0.06
        val tables = listOf(IOR * (1 - k), IOR.toDouble(), IOR * (1 + k)).map { ior ->
            FloatArray(SAMPLES + 1) { i -> shift(i.toDouble() / SAMPLES, bevel.toDouble(), ior = ior).toFloat() }
        }
        val sx = fw / Plates.SCREEN_W.toFloat(); val sy = fh / Plates.SCREEN_H.toFloat()
        val e = 0.5f
        for (y in 0 until h) {
            val py = (y + 0.5f) / scale - hy
            for (x in 0 until w) {
                val pxl = (x + 0.5f) / scale - hx
                val d = box(pxl, py, hx, hy, radius)
                if (d > 0.5f) continue
                val t = (-d / bevel).coerceIn(0f, 1f)
                if (t >= 1f) continue
                var nx = box(pxl + e, py, hx, hy, radius) - box(pxl - e, py, hx, hy, radius)
                var ny = box(pxl, py + e, hx, hy, radius) - box(pxl, py - e, hx, hy, radius)
                val len = sqrt(nx * nx + ny * ny)
                if (len < 1e-5f) continue
                nx /= len; ny /= len
                val ti = (t * SAMPLES).toInt().coerceIn(0, SAMPLES)
                // where this point of the card is on the screen, less the shift inwards (along the outward normal)
                val ax = rect.x + hx + pxl; val ay = rect.y + hy + py
                val r = sample(px, fw, fh, (ax - nx * tables[0][ti]) * sx, (ay - ny * tables[0][ti]) * sy, 16)
                val g = sample(px, fw, fh, (ax - nx * tables[1][ti]) * sx, (ay - ny * tables[1][ti]) * sy, 8)
                val b = sample(px, fw, fh, (ax - nx * tables[2][ti]) * sx, (ay - ny * tables[2][ti]) * sy, 0)
                out[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** One colour channel (`shiftBits` 16 red, 8 green, 0 blue) of the picture at (x, y) in its pixels, bilinear, clamped. */
    private fun sample(px: IntArray, w: Int, h: Int, x: Float, y: Float, shiftBits: Int): Int {
        val fx = (x - 0.5f).coerceIn(0f, (w - 1).toFloat()); val fy = (y - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(w - 1, x0 + 1); val y1 = min(h - 1, y0 + 1)
        val ax = fx - x0; val ay = fy - y0
        fun c(i: Int) = (px[i] shr shiftBits) and 0xFF
        val top = c(y0 * w + x0) * (1 - ax) + c(y0 * w + x1) * ax
        val bottom = c(y1 * w + x0) * (1 - ax) + c(y1 * w + x1) * ax
        return (top * (1 - ay) + bottom * ay).roundToInt().coerceIn(0, 255)
    }
}
