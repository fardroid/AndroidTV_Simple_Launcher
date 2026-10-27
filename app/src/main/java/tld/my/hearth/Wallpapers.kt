package tld.my.hearth

// The wallpaper pictures: the ones that come with the app (assets/wallpapers) and any put in the app's own folder on the box
// (adb push picture.jpg /sdcard/Android/data/tld.my.hearth/files/wallpapers/). Each picture is loaded once into three forms:
// the picture itself at screen size, a small blurred copy for the frosted glass of the cards, and a coarse brightness grid
// that chooses the colour of each card's plate. A port of the measuring part of src/lib/wallpaper.ts.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect as ARect
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class Slide(
    val id: String,
    val image: ImageBitmap,
    /** 480 x 270, blurred, with the screen's shade baked in: stretched over the whole screen it lines up with the picture */
    val frost: ImageBitmap,
    /** 16 x 9 brightness values, 0 to 1 */
    val map: FloatArray,
)

object Wallpapers {
    private const val ASSET_DIR = "wallpapers"
    private val PICTURE = Regex(".*\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
    const val FROST_W = 480
    const val FROST_H = 270

    fun folder(context: Context): File? = context.getExternalFilesDir("wallpapers")

    /** Every picture: the bundled ones ("asset:" + name), then the box's own folder (absolute paths), each A to Z. */
    fun list(context: Context): List<String> {
        val bundled = (context.assets.list(ASSET_DIR) ?: emptyArray()).filter { PICTURE.matches(it) }.sorted().map { "asset:$it" }
        val own = folder(context)?.listFiles()?.filter { it.isFile && PICTURE.matches(it.name) && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }?.map { it.absolutePath } ?: emptyList()
        return bundled + own
    }

    private fun open(context: Context, id: String) =
        if (id.startsWith("asset:")) context.assets.open("$ASSET_DIR/" + id.removePrefix("asset:")) else File(id).inputStream()

    /** Loads a picture at `w` x `h` pixels (cropped to fill, like CSS `cover`). Null when it cannot be read. */
    fun load(context: Context, id: String, w: Int, h: Int): Slide? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, id).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val raw = open(context, id).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val screen = cover(raw, w, h)
        if (screen !== raw) raw.recycle()
        Slide(id, screen.asImageBitmap(), frost(screen).asImageBitmap(), brightnessMap(screen))
    }.getOrNull()

    /** The picture scaled and cropped to exactly `w` x `h`. */
    private fun cover(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val cw = (w / scale).roundToInt().coerceAtMost(src.width)
        val ch = (h / scale).roundToInt().coerceAtMost(src.height)
        val sx = (src.width - cw) / 2; val sy = (src.height - ch) / 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, ARect(sx, sy, sx + cw, sy + ch), ARect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The glass: a quarter-size copy blurred (three box passes, close to a 7 px Gaussian at this size, as the webOS app's
     *  `blur(7px)`), with the shade that lies over the screen (Shade in ui/Wallpaper.kt) drawn on top. */
    private fun frost(screen: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(screen, FROST_W, FROST_H, true)
        val px = IntArray(FROST_W * FROST_H)
        small.getPixels(px, 0, FROST_W, 0, 0, FROST_W, FROST_H)
        repeat(3) { boxBlur(px, FROST_W, FROST_H, 6) }
        val out = small.copy(Bitmap.Config.ARGB_8888, true)
        if (out !== small) small.recycle()
        out.setPixels(px, 0, FROST_W, 0, 0, FROST_W, FROST_H)
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, FROST_H.toFloat(), SHADE_COLORS, SHADE_STOPS, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawRect(0f, 0f, FROST_W.toFloat(), FROST_H.toFloat(), shade)
        return out
    }

    /** The soft shade at the top and bottom of the screen (as .wallpaper__scrim in global.css). */
    val SHADE_COLORS = intArrayOf(0x38000000, 0x14000000, 0x00000000, 0x80000000.toInt())
    val SHADE_STOPS = floatArrayOf(0f, 0.35f, 0.55f, 1f)

    /** One box-blur pass, horizontal then vertical; the edges repeat the outermost pixels. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        val div = 2 * r + 1
        for (y in 0 until h) {
            var ra = 0; var rr = 0; var rg = 0; var rb = 0
            for (i in -r..r) { val c = px[y * w + min(w - 1, max(0, i))]; ra += c ushr 24; rr += (c shr 16) and 255; rg += (c shr 8) and 255; rb += c and 255 }
            for (x in 0 until w) {
                tmp[y * w + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                val out = px[y * w + max(0, x - r)]; val inn = px[y * w + min(w - 1, x + r + 1)]
                ra += (inn ushr 24) - (out ushr 24); rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                rg += ((inn shr 8) and 255) - ((out shr 8) and 255); rb += (inn and 255) - (out and 255)
            }
        }
        for (x in 0 until w) {
            var ra = 0; var rr = 0; var rg = 0; var rb = 0
            for (i in -r..r) { val c = tmp[min(h - 1, max(0, i)) * w + x]; ra += c ushr 24; rr += (c shr 16) and 255; rg += (c shr 8) and 255; rb += c and 255 }
            for (y in 0 until h) {
                px[y * w + x] = ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                val out = tmp[max(0, y - r) * w + x]; val inn = tmp[min(h - 1, y + r + 1) * w + x]
                ra += (inn ushr 24) - (out ushr 24); rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                rg += ((inn shr 8) and 255) - ((out shr 8) and 255); rb += (inn and 255) - (out and 255)
            }
        }
    }

    /** Brightness grid: the picture drawn at 64 x 36, the mean of each 4 x 4 block. */
    private fun brightnessMap(screen: Bitmap): FloatArray {
        val small = Bitmap.createScaledBitmap(screen, 64, 36, true)
        val px = IntArray(64 * 36)
        small.getPixels(px, 0, 64, 0, 0, 64, 36)
        small.recycle()
        val out = FloatArray(Plates.MAP_W * Plates.MAP_H)
        for (my in 0 until Plates.MAP_H) for (mx in 0 until Plates.MAP_W) {
            var sum = 0f
            for (y in 0 until 4) for (x in 0 until 4) {
                val c = px[(my * 4 + y) * 64 + mx * 4 + x]
                sum += (0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)) / 255f
            }
            out[my * Plates.MAP_W + mx] = sum / 16
        }
        return out
    }
}
