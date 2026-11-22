package tld.my.simpletv.ui

// The banners at the top of the screen, over whatever is open (Settings too), as Simple LG launcher 2.2.5 has them: the cards'
// glass with a round icon, a title, a dimmer line under it and a ring that runs down until the banner goes. Several at once stand
// one under another, the newest at the top: a new one pushes the others down, and when one goes the ones under it move up. At
// most three show at once; the rest wait their turn. The same title and text again only makes it stay longer.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tld.my.simpletv.PlateLook
import tld.my.simpletv.Rect

enum class NoticeKind {
    /** blue "i": something is going on, or a hint */
    INFO,
    /** green tick: it is done */
    DONE,
    /** red "!": it did not work */
    ERROR,
    /** blue arrow: something comes down in the background */
    DOWNLOAD,
    /** green arrow: a new version of the launcher */
    UPDATE;

    val color get() = when (this) {
        DONE, UPDATE -> Color(0xD930D158)
        ERROR -> Color(0xE6FF453A)
        else -> Color(0xE60A84FF)
    }
    val glyph get() = when (this) { DONE -> Glyph.CHECK; ERROR -> Glyph.ALERT; DOWNLOAD, UPDATE -> Glyph.DOWNLOAD; INFO -> Glyph.INFO }
    /** how long it stays, between coming in and going */
    val holdMs get() = when (this) { UPDATE -> 8000; ERROR -> 7000; else -> 5000 }
}

class Notice(val id: Long, val kind: NoticeKind, val title: String, val text: String) {
    /** grows when the same banner is asked for again: its time starts again */
    var renewed by androidx.compose.runtime.mutableIntStateOf(0)
}

class NoticeState {
    /** the ones on the screen, the newest first */
    val shown = mutableStateListOf<Notice>()
    private val waiting = ArrayDeque<Notice>()
    private var next = 0L

    fun show(kind: NoticeKind, title: String, text: String = "") {
        shown.firstOrNull { it.title == title && it.text == text }?.let { it.renewed++; return }
        if (waiting.any { it.title == title && it.text == text }) return
        waiting.addLast(Notice(next++, kind, title, text))
        settle()
    }

    fun gone(n: Notice) {
        shown.remove(n)
        settle()
    }

    private fun settle() {
        while (shown.size < MAX_SHOWN && waiting.isNotEmpty()) shown.add(0, waiting.removeFirst())
    }

    companion object {
        const val MAX_SHOWN = 3
    }
}

private const val IN_MS = 350
private const val OUT_MS = 350
private const val TOP = 26f
private const val GAP = 14f

/** The banners, at the top of the screen. `look`, `before` and `glass`: as the cards are drawn now. */
@Composable
fun Notices(state: NoticeState, wall: WallState, look: PlateLook, before: PlateLook?, glass: GlassStyle?, shadow: Boolean) {
    val heights = remember { mutableStateMapOf<Long, Float>() }
    val widths = remember { mutableStateMapOf<Long, Float>() }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxWidth()) {
        var y = TOP
        state.shown.forEach { n ->
            val top = y
            y += (heights[n.id] ?: 100f) + GAP
            key(n.id) {
                Banner(n, top, wall, look, before, glass, shadow, widths[n.id], heights[n.id], density,
                    measured = { w, h -> widths[n.id] = w; heights[n.id] = h }, done = { heights.remove(n.id); widths.remove(n.id); state.gone(n) })
            }
        }
    }
}

@Composable
private fun Banner(
    n: Notice, top: Float, wall: WallState, look: PlateLook, before: PlateLook?, glass: GlassStyle?, shadow: Boolean, width: Float?, height: Float?, density: Float,
    measured: (Float, Float) -> Unit, done: () -> Unit,
) {
    val show = remember { Animatable(0f) }
    val left = remember { Animatable(1f) }
    // comes in, stays while its ring runs down (again when it is asked for again), goes
    LaunchedEffect(n.renewed) {
        show.animateTo(1f, tween(IN_MS, easing = Ease.out))
        left.snapTo(1f)
        left.animateTo(0f, tween(n.kind.holdMs, easing = LinearEasing))
        show.animateTo(0f, tween(OUT_MS, easing = Ease.inOut))
        done()
    }
    val y by animateFloatAsState(top, tween(350, easing = Ease.inOut), label = "place")
    Box(Modifier.fillMaxWidth().offset(y = y.dp).graphicsLayer { alpha = show.value }, contentAlignment = Alignment.TopCenter) {
        val w = width ?: 0f
        val h = height ?: 100f
        val x = ((1920f - w) / 2f).toInt()
        val rest = Rect(x, top.toInt(), w.toInt(), h.toInt())
        Box(
            Modifier
                .onSizeChanged { measured(it.width / density, it.height / density) }
                .softShadow(PLATE_RADIUS, 80f, 30f, Color(0x80000000), shadow)
                // the glass of a card, lined up with the picture where the banner is now (liquid glass's bevel once it rests)
                .plate({ Rect(x, y.toInt(), w.toInt(), h.toInt()) }, rest, wall, look, before, glass, shadow = shadow, shadowBlur = 80f, shadowDy = 30f, shadowColor = Color(0x80000000)),
        ) {
            Row(Modifier.heightIn(min = 100.dp).widthIn(max = 1600.dp).padding(horizontal = 34.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).background(n.kind.color, CircleShape), contentAlignment = Alignment.Center) {
                    LineGlyph(n.kind.glyph, Modifier.size(34.dp), Color.White, 2.4f)
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.widthIn(max = 1100.dp)) {
                    BasicText(n.title, style = text(28f, 500, Color.White.copy(alpha = 0.96f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        BasicText(n.text, style = text(20f, 400, Color.White.copy(alpha = 0.66f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(48.dp))
                // the ring: the time left
                Canvas(Modifier.size(46.dp)) {
                    val sw = 3f * density
                    val r = size.minDimension / 2 - sw
                    drawCircle(Color.White.copy(alpha = 0.18f), r, style = Stroke(sw))
                    drawArc(Color.White.copy(alpha = 0.5f), -90f, 360f * left.value, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r), style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
    }
}
