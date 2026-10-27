package tld.my.hearth.ui

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import tld.my.hearth.PlateLook
import tld.my.hearth.Rect
import tld.my.hearth.Slide
import tld.my.hearth.Wallpapers
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
}

private val SHADE = Brush.verticalGradient(*Wallpapers.SHADE_STOPS.zip(Wallpapers.SHADE_COLORS.map { Color(it) }).toTypedArray())

private fun DrawScope.drawFull(image: ImageBitmap, alpha: Float) {
    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = alpha, filterQuality = FilterQuality.Low)
}

/** The picture (or the plain dark gradient), and the shade at the top and bottom that keeps the clock and the row readable. */
@Composable
fun Background(wall: WallState, pictures: Boolean) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            val cur = if (pictures) wall.current else null
            val prev = if (pictures) wall.previous else null
            if (cur == null && prev == null) {
                drawRect(Brush.linearGradient(listOf(Color(0xFF0B1636), Color(0xFF26357A), Color(0xFF3A2445)), start = Offset.Zero, end = Offset(size.width, size.height)))
                drawRect(Color(0xB0000000))
            } else {
                prev?.let { drawFull(it.image, 1f) }
                cur?.let { drawFull(it.image, if (prev == null) 1f else wall.fade.value) }
            }
            drawRect(SHADE)
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
    glass: Boolean,
    shadow: Boolean,
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val radius = 36f
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(130, easing = Ease.out), label = "focus")
    Box(
        Modifier
            .offset(rect.x.dp, rect.y.dp)
            .size(rect.w.dp, rect.h.dp)
            .graphicsLayer { val s = 1f + 0.02f * focus; scaleX = s; scaleY = s }
            .softShadow(radius, 70f, 34f, Color(0x73000000), shadow)
            .drawBehind {
                val r = radius * density
                val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                val t = if (wall.previous == null) 1f else wall.fade.value
                clipPath(shape) {
                    if (glass) {
                        val dst = IntSize((1920 * density).roundToInt(), (1080 * density).roundToInt())
                        val at = IntOffset((-rect.x * density).roundToInt(), (-rect.y * density).roundToInt())
                        wall.previous?.let { drawImage(it.frost, dstOffset = at, dstSize = dst, filterQuality = FilterQuality.Low) }
                        wall.current?.let { drawImage(it.frost, dstOffset = at, dstSize = dst, alpha = t, filterQuality = FilterQuality.Low) }
                    }
                    val bg = if (previousLook == null) Color(look.bg) else lerp(Color(previousLook.bg), Color(look.bg), t)
                    drawRect(bg)
                    if (focus > 0f) drawRect(Color(look.focus), alpha = focus)
                }
                // the rim: a faint edge all round and a brighter one along the top
                val hair = density
                drawRoundRect(Color(0x12FFFFFF), topLeft = Offset(hair / 2, hair / 2), size = Size(size.width - hair, size.height - hair),
                    cornerRadius = CornerRadius(r - hair / 2), style = Stroke(hair))
                clipRect(bottom = r) {
                    drawRoundRect(Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x00FFFFFF)), startY = 0f, endY = r * 0.7f),
                        topLeft = Offset(hair / 2, hair / 2), size = Size(size.width - hair, size.height - hair),
                        cornerRadius = CornerRadius(r - hair / 2), style = Stroke(hair))
                }
            },
        content = content,
    )
}
