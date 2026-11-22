package tld.my.simpletv.ui

// The wallpaper and the frosted-glass plates of the cards. Both read one cross-fade (WallState.fade), so the glass changes in
// step with the picture behind it. The fade is read only while drawing: a change of picture redraws, it does not recompose.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tld.my.simpletv.PlateLook
import tld.my.simpletv.Rect
import tld.my.simpletv.Slide
import tld.my.simpletv.Wallpapers
import kotlin.math.roundToInt

class WallState {
    var current by mutableStateOf<Slide?>(null)
        private set
    var previous by mutableStateOf<Slide?>(null)
        private set
    /** 0 → 1 while `current` fades in over `previous` */
    val fade = Animatable(1f)

    suspend fun show(slide: Slide?) {
        if (slide?.id == current?.id) return
        previous = current
        current = slide
        fade.snapTo(0f)
        fade.animateTo(1f, tween(Ease.FADE_MS, easing = Ease.inOut))
        previous = null
    }

    /** The same picture made again (the glass with another frost): swapped in place, without a fade. */
    fun replace(slide: Slide) {
        if (slide.id == current?.id) current = slide
    }
}

/** The bevels of liquid glass (Lens.bevel), made in the background once for each picture and card and kept while that picture
 *  is on the screen or fading out. Reading one that is not made yet starts making it; the card redraws when it is there. */
class LensCache(private val scope: kotlinx.coroutines.CoroutineScope) {
    private val made = androidx.compose.runtime.mutableStateMapOf<String, ImageBitmap>()
    private val making = mutableSetOf<String>()

    private fun key(slide: Slide, rect: Rect) = "${slide.id}#${System.identityHashCode(slide.frost)}#${rect.x},${rect.y},${rect.w},${rect.h}"

    fun bevel(slide: Slide, rect: Rect): ImageBitmap? {
        val k = key(slide, rect)
        made[k]?.let { return it }
        if (making.add(k)) scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val bmp = runCatching { tld.my.simpletv.Lens.bevel(slide.frost.asAndroidBitmap(), rect, 1f).asImageBitmap() }.getOrNull()
            kotlinx.coroutines.withContext(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
                making.remove(k)
                if (bmp != null) made[k] = bmp
            }
        }
        return null
    }

    /** Only the bevels of these pictures are kept (each card's is about a megabyte). */
    fun keep(slides: List<Slide?>) {
        val keys = slides.filterNotNull().map { "${it.id}#${System.identityHashCode(it.frost)}#" }
        made.keys.filter { k -> keys.none { k.startsWith(it) } }.forEach { made.remove(it) }
    }
}

/** How the cards' glass is drawn: its kind, how much darker the picture is (Dim the picture) and where liquid glass's bevels come
 *  from. */
class GlassStyle(val kind: tld.my.simpletv.GlassKind, val dim: Float, val lenses: LensCache, val focusable: Boolean = false) {
    fun forFocusable() = GlassStyle(kind, dim, lenses, true)
}

/** A card grown 2 % round its middle, as in focus. */
fun grownRect(r: Rect): Rect {
    val dw = (r.w * 0.02f).roundToInt(); val dh = (r.h * 0.02f).roundToInt()
    return Rect(r.x - dw / 2, r.y - dh / 2, r.w + dw, r.h + dh)
}

private val SHADE = Brush.verticalGradient(*Wallpapers.SHADE_STOPS.zip(Wallpapers.SHADE_COLORS.map { Color(it) }).toTypedArray())

private fun DrawScope.drawFull(image: ImageBitmap, alpha: Float) {
    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = alpha, filterQuality = FilterQuality.Low)
}

/** The picture (or the plain dark gradient), and the shade at the top and bottom that keeps the clock and the row readable. */
@Composable
fun Background(wall: WallState, pictures: Boolean, dim: Float = 0f, shade: () -> Float = { 1f }) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val cur = if (pictures) wall.current else null
            val prev = if (pictures) wall.previous else null
            if (cur == null && prev == null) {
                val th = Themes.current
                drawRect(Color(th.bg))
                drawRect(Brush.linearGradient(0f to Color(th.start), 0.55f to Color(th.mid), 1f to Color(th.end), start = Offset(size.width * 0.2f, 0f), end = Offset(size.width * 0.8f, size.height)))
            } else {
                prev?.let { drawFull(it.image, 1f) }
                cur?.let { drawFull(it.image, if (prev == null) 1f else wall.fade.value) }
                // Settings → Wallpaper → Dim the picture (the plain background is never dimmed)
                if (dim > 0f) drawRect(Color.Black, alpha = dim)
            }
            shade().let { if (it > 0f) drawRect(SHADE, alpha = it) }
        }
    )
}

/** A card on the design screen at `rect`: its soft shadow, the frosted glass (the blurred wallpaper lined up with the picture
 *  behind), its colour, its rim, and while the remote is on it a brighter layer and a small growth. */
@Composable
fun Plate(
    rect: Rect,
    wall: WallState,
    look: PlateLook,
    previousLook: PlateLook?,
    glass: GlassStyle?,
    shadow: Boolean,
    focused: Boolean = false,
    /** the pixel shift the cards are drawn with now (design px): the glass takes the picture from where the card really is */
    shift: Pair<Int, Int> = 0 to 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(130, easing = Ease.out), label = "focus")
    val onScreen = Rect(rect.x + shift.first, rect.y + shift.second, rect.w, rect.h)
    Box(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.w.dp, rect.h.dp)
            .graphicsLayer { val s = 1f + 0.02f * focus; scaleX = s; scaleY = s }
            .softShadow(PLATE_RADIUS, 70f, 34f, Color(0x73000000), shadow)
            .plate({ onScreen }, onScreen, wall, look, previousLook, glass, { focus }, shadow),
        content = content,
    )
}

/** The cards' corners: 60 px (36 before 1.3.1) with every kind of glass, so that liquid glass's bevel, never wider than the
 *  corner's radius, can be wide, and the cards keep their shape when the glass is changed (as Simple LG launcher 2.2.7). */
const val PLATE_RADIUS = 60f

/** The plate of a card (or a banner) drawn behind what it holds: the glass, the dimming, its colour, the focus layer and the rim.
 *  `at`: where it is on the screen now (design px), which lines the glass up with the picture behind; `rest`: where it rests, for
 *  which liquid glass's bevel is made (drawn only while it is there); `focus`: 0-1, how far it has grown in focus (the picture
 *  behind is drawn shrunk back by it, so it stays still). */
fun Modifier.plate(at: () -> Rect, rest: Rect, wall: WallState, look: PlateLook, previousLook: PlateLook?, glass: GlassStyle?, focus: () -> Float = { 0f },
    shadow: Boolean = false, shadowBlur: Float = 70f, shadowDy: Float = 34f, shadowColor: Color = Color(0x73000000)): Modifier =
    drawBehind {
        val r = PLATE_RADIUS * density
        val f = focus()
        val now = at()
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
        val t = if (wall.previous == null) 1f else wall.fade.value
        clipPath(shape) {
            if (glass != null) {
                val grow = 1f + 0.02f * f
                val dst = IntSize((1920 * density).roundToInt(), (1080 * density).roundToInt())
                val origin = IntOffset((-now.x * density).roundToInt(), (-now.y * density).roundToInt())
                scale(1f / grow, pivot = center) {
                    wall.previous?.let { drawImage(it.frost, dstOffset = origin, dstSize = dst, filterQuality = FilterQuality.Low) }
                    wall.current?.let { drawImage(it.frost, dstOffset = origin, dstSize = dst, alpha = t, filterQuality = FilterQuality.Low) }
                }
                // liquid glass: the bent picture on the bevel, over it. A card in focus is 2 % larger: its bevel is worked out for that
                // size and place too (as the webOS launcher's shader does every frame), and the two cross-fade as it grows, instead
                // of the resting one being stretched
                if (glass.kind == tld.my.simpletv.GlassKind.LIQUID && now == rest) {
                    val full = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    val big = grownRect(rest)
                    fun bevelOf(s: Slide, alpha: Float) {
                        val small = glass.lenses.bevel(s, rest)
                        val large = if (f > 0f || glass.focusable) glass.lenses.bevel(s, big) else null
                        if (f > 0f && large != null) {
                            small?.let { drawImage(it, dstSize = full, alpha = alpha * (1f - f), filterQuality = FilterQuality.Low) }
                            drawImage(large, dstSize = full, alpha = alpha * (if (small == null) 1f else f), filterQuality = FilterQuality.Low)
                        } else small?.let { drawImage(it, dstSize = full, alpha = alpha, filterQuality = FilterQuality.Low) }
                    }
                    wall.previous?.let { bevelOf(it, 1f) }
                    wall.current?.let { bevelOf(it, t) }
                }
                if (glass.dim > 0f) drawRect(Color.Black, alpha = glass.dim)
            }
            // the card's shadow falls on the picture behind it, which shows through the glass: under the card too, not only
            // round it (a light card in a dark frame looked lit from inside). Even all over, as dark as at the card's sides: a
            // shadow thinning out towards the edges, as a real one does, made the edges look lit, like sanded glass
            if (shadow) drawRect(Color.Black, alpha = shadowColor.alpha * 0.5f)
            val bg = if (previousLook == null) Color(look.bg) else lerp(Color(previousLook.bg), Color(look.bg), t)
            drawRect(bg)
            if (f > 0f) drawRect(Color(look.focus), alpha = f)
        }
        // the rim: a faint edge all round and a brighter one along the top (liquid glass has none: only the lens)
        if (glass?.kind == tld.my.simpletv.GlassKind.LIQUID) return@drawBehind
        val hair = density
        drawRoundRect(Color(0x12FFFFFF), topLeft = Offset(hair / 2, hair / 2), size = Size(size.width - hair, size.height - hair),
            cornerRadius = CornerRadius(r - hair / 2), style = Stroke(hair))
        clipRect(bottom = r) {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x00FFFFFF)), startY = 0f, endY = r * 0.7f),
                topLeft = Offset(hair / 2, hair / 2), size = Size(size.width - hair, size.height - hair),
                cornerRadius = CornerRadius(r - hair / 2), style = Stroke(hair))
        }
    }
