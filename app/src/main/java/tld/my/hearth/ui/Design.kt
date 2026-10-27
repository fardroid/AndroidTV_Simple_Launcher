package tld.my.hearth.ui

// The look: colours, motion curves and the design screen. Everything is laid out on a 1920 x 1080 design screen, as the webOS
// app is: one dp here is one pixel there (DesignScreen sets the density so), whatever the box's real resolution.

import android.graphics.Paint
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object Hue {
    val textPrimary = Color(0xFFF2F2F2)
    val textSecondary = Color(0xFFA8A8A8)
    val textMuted = Color(0xFF6A6A6A)
    val accent = Color(0xFFFFC36B)
    val tileBg = Color(0xFF101010)
    val tileBgFocus = Color(0xFF1D1D1D)
    val overlay = Color(0xE0000000)
    val danger = Color(0xFFEF7A73)
}

object Ease {
    /** the cards' and the strip's slides */
    val out = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)
    /** the selected app growing and shrinking */
    val dock = CubicBezierEasing(0.25f, 0.8f, 0.3f, 1f)
    /** CSS ease-in-out: the wallpaper and the glass cross-fading */
    val inOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    const val FADE_MS = 1400
}

/** Lays the content out on the 1920 x 1080 design screen. */
@Composable
fun DesignScreen(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val px = with(LocalDensity.current) { maxWidth.toPx() }
        CompositionLocalProvider(LocalDensity provides Density(px / 1920f, 1f)) { content() }
    }
}

/** The app's text: the box's own UI font, tabular figures. */
fun text(size: Float, weight: Int = 400, color: Color = Hue.textPrimary, lineHeight: Float = 1.2f, spacing: TextUnit = TextUnit.Unspecified) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    color = color,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = spacing,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** A soft shadow like CSS box-shadow: drawn only outside the rounded shape (so a see-through card stays clear),
 *  `blur` as CSS gives it (the blur radius), `dy` down. */
fun Modifier.softShadow(radius: Float, blur: Float, dy: Float, color: Color, enabled: Boolean = true): Modifier =
    if (!enabled) this else drawBehind {
        val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius.dp2px(this@drawBehind)))) }
        clipPath(path, ClipOp.Difference) {
            drawIntoCanvas { c ->
                val r = radius.dp2px(this)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.copy(alpha = 1f).toArgb()
                    setShadowLayer((blur * 0.5f / 0.57735f).dp2px(this@drawBehind).coerceAtLeast(1f), 0f, dy.dp2px(this@drawBehind), color.toArgb())
                }
                c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
        }
    }

/** Design pixels to real pixels inside a draw scope. */
fun Float.dp2px(d: Density) = this * d.density
