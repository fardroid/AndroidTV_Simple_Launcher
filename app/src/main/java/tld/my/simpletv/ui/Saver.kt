package tld.my.simpletv.ui

// The screen saver (Settings → Wallpaper → Screen saver), as Simple LG launcher 2.2.6 has it: after a while without a key, on the
// home screen the app row sinks 60 px and fades, the cards fade a beat later (Settings fades whole), and the wallpaper stays alone,
// slowly zooming and drifting (Ken Burns), so that no point of it stands still; a slideshow changes its picture every 30 s
// meanwhile. Any key brings back what was there, the same way, faster, and does nothing else. It is the launcher's own: the
// box's own screen saver (Ambient mode) is left alone, and when it comes over the launcher this one ends at once, under it.

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.random.Random

class SaverState {
    /** the time of the frame being drawn while it shows (ms): reading it makes a drawing follow the saver */
    var frameMs by mutableLongStateOf(0L)
    /** it is on or coming on (a key now wakes it) */
    var on by mutableStateOf(false)
        private set

    private class View(val s: Float, val x: Float, val y: Float)
    private class Seg(val from: Float, val to: Float, val t0: Long, val dur: Long, val coming: Boolean)

    private var seg: Seg? = null
    private var from = View(1f, 960f, 540f)
    private var to = View(1f, 960f, 540f)
    private var driftAt = 0L
    private var zoomIn = true
    private val rng = Random(System.nanoTime())

    fun start(now: Long) {
        if (on) return
        val v = progress(now)
        if (v <= 0f) { from = View(1f, 960f, 540f); to = pick(); driftAt = now }
        seg = Seg(v, 1f, now, maxOf(1L, (ENTER_MS * (1 - v)).toLong()), true)
        on = true
        frameMs = now
    }

    /** A key: goes away from wherever it is. */
    fun wake(now: Long) {
        if (!on) return
        val v = progress(now)
        seg = Seg(v, 0f, now, maxOf(1L, (EXIT_MS * v).toLong()), false)
        on = false
        frameMs = now
    }

    /** Gone at once (the launcher went to the back). */
    fun stop() { on = false; seg = null; frameMs = 0L }

    /** Any of it on the screen (on, coming on or going away). */
    fun showing(now: Long) = on || progress(now) > 0f

    fun moving(now: Long) = seg?.let { now - it.t0 < it.dur } ?: false

    /** 0: what was on the screen, 1: the saver alone. */
    fun progress(now: Long): Float {
        val s = seg ?: return 0f
        val t = ((now - s.t0).toDouble() / s.dur).coerceIn(0.0, 1.0)
        val e = if (s.coming) (if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2) else 1 - (1 - t).pow(3)
        return (s.from + (s.to - s.from) * e).toFloat().coerceIn(0f, 1f)
    }

    /** How far the row has gone (down ROW_SINK px and faded), and the cards (a beat later). */
    fun row(now: Long) = progress(now)
    fun cards(now: Long) = ((progress(now) - CARDS_DELAY) / (1 - CARDS_DELAY)).coerceIn(0f, 1f)

    // a view to drift to: zoomed in and out by turns (4-6 % and 9-12 %), round a point in the middle half of the screen; growing by
    // s round a point inside the screen keeps the screen covered, and moving that point pans the picture by (s - 1) times its way
    private fun pick(): View {
        val s = if (zoomIn) 1.09f + 0.03f * rng.nextFloat() else 1.04f + 0.02f * rng.nextFloat()
        zoomIn = !zoomIn
        return View(s, 1920f * (0.25f + 0.5f * rng.nextFloat()), 1080f * (0.25f + 0.5f * rng.nextFloat()))
    }

    /** The wallpaper's slow zoom at `now`: it grows by the third value round the point of the first two (design px). */
    fun view(now: Long): Triple<Float, Float, Float> {
        while (on && now - driftAt >= STEP_MS) { from = to; to = pick(); driftAt += STEP_MS }
        val t = (-(cos(PI * ((now - driftAt).toDouble() / STEP_MS).coerceIn(0.0, 1.0)) - 1) / 2).toFloat()
        val s = from.s + (to.s - from.s) * t
        val v = progress(now)
        return Triple(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, 1 + (s - 1) * v)
    }

    companion object {
        const val ENTER_MS = 1000L
        const val EXIT_MS = 500L
        const val STEP_MS = 30_000L
        const val ROW_SINK = 60f
        private const val CARDS_DELAY = 0.2f
    }
}
