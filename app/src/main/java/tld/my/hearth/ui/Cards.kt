package tld.my.hearth.ui

// The clock, weather and calendar cards (their contents; the plate under each is Plate in Wallpaper.kt). Sizes, colours and
// motion follow src/theme/global.css of the webOS app.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import tld.my.hearth.Plates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tld.my.hearth.Cal
import tld.my.hearth.CalendarData
import tld.my.hearth.ClockColors
import tld.my.hearth.Config
import tld.my.hearth.DayEvent
import tld.my.hearth.Lang
import tld.my.hearth.Texts
import tld.my.hearth.WeatherView
import tld.my.hearth.tr
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------- clock ----------

/** The time (with a small AM/PM beside it in 12-hour mode) and, to its right, the day and the date. */
@Composable
fun ClockCard(now: LocalDateTime, c: Config) {
    val (time, period) = Texts.timeParts(now, c.clock24)
    val color = ClockColors.of(c.clockColor)
    val shadow = Shadow(if (color.dark) Color(0x59FFFFFF) else Color(0x66000000), Offset(0f, 3f), 18f)
    fun style(size: Float, weight: Int, alpha: Float = 1f, spacing: Float = 0f): TextStyle {
        val base = wtext(size, weight, lineHeight = 1f, spacing = spacing.sp)
        return if (color.colors.size > 1) base.copy(brush = Brush.horizontalGradient(color.colors.map { Color(it) }), alpha = alpha, shadow = null)
        else base.copy(color = Color(color.colors[0]).copy(alpha = alpha), shadow = shadow)
    }
    val size = c.clockSize.toFloat()
    val dateSize = (32f * c.dateSize / 100f).roundToInt().toFloat()
    // The digits sit in the middle of the card, by their own height (not the font's line box, which has more room above
    // than below): the baseline comes from the laid-out text, the height of the digits from the font (Fonts.cap).
    var baseline by remember(c.clockFont, c.clockSize, c.clockWeight) { mutableStateOf(-1f) }
    val density = LocalDensity.current
    val digits = Fonts.of(c.clockFont).cap * size
    val digitsTop = (if (baseline < 0f) size * 0.86f else with(density) { baseline.toDp().value }) - digits
    val cardH = Plates.clockHeight(c).toFloat()
    Box(Modifier.fillMaxWidth().height(cardH.dp)) {
    Row(Modifier.padding(horizontal = 40.dp).offset(y = ((cardH - digits) / 2f - digitsTop).dp), verticalAlignment = Alignment.Top) {
        BasicText(time, style = style(size, c.clockWeight, spacing = size * 0.03f), onTextLayout = { baseline = it.firstBaseline })
        if (period.isNotEmpty()) {
            BasicText(period, Modifier.padding(start = (size * 0.12f).dp, top = (size * 0.14f).dp), style = style((size * 0.27f).roundToInt().toFloat(), 300, 0.75f, size * 0.027f))
        }
        if (c.showDate) {
            val (day, date) = Texts.dateLines(now.toLocalDate())
            Column(Modifier.padding(start = (size * 0.2f).dp, top = maxOf(0f, digitsTop - dateSize * 0.24f).dp)) {
                BasicText(day, style = style(dateSize, 500, 0.75f).copy(lineHeight = (dateSize * 1.2f).sp))
                BasicText(date, style = style(dateSize, 400, 0.75f).copy(lineHeight = (dateSize * 1.2f).sp))
            }
        }
    }
    }
}

// ---------- weather ----------

@Composable
private fun WeatherCurve(view: WeatherView, now: LocalDateTime, k: Float) {
    val measurer = rememberTextMeasurer(32)
    val hours = view.hours
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        if (hours.size < 2) return@Canvas
        val d = density
        val w = size.width / d
        val top = 46f; val bottom = 108f; val edge = 50f
        val lo = hours.minOf { it.tempC }; val hi = hours.maxOf { it.tempC }
        val span = maxOf(hi - lo, 3.0)
        val pts = hours.mapIndexed { i, h -> Offset(edge + (w - 2 * edge) * i / (hours.size - 1), (bottom - (h.tempC - lo) / span * (bottom - top)).toFloat()) }
        val line = Path().apply {
            moveTo(pts[0].x * d, pts[0].y * d)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]; val cx = (a.x + b.x) / 2
                cubicTo(cx * d, a.y * d, cx * d, b.y * d, b.x * d, b.y * d)
            }
        }
        val area = Path().apply { addPath(line); lineTo(pts.last().x * d, 118 * d); lineTo(pts[0].x * d, 118 * d); close() }
        drawPath(area, Brush.verticalGradient(listOf(Color(0x2BFFC36B), Color(0x00FFC36B)), startY = top * d, endY = 118 * d))
        drawPath(line, Hue.accent, style = Stroke(3.5f * d, cap = StrokeCap.Round))
        if (view.day == 0) {
            val first = hours.first().hour; val last = hours.last().hour
            val hourNow = now.hour + now.minute / 60f
            val x = edge + (w - 2 * edge) * ((hourNow - first) / (last - first)).coerceIn(0f, 1f)
            drawLine(Color(0x8CFFFFFF), Offset(x * d, 22 * d), Offset(x * d, 120 * d), 2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * d, 5 * d)))
        }
        val tempStyle = wtext(17f * k, 600, lineHeight = 1f)
        val hourStyle = wtext(14f * k, 400, Color(0x9EFFFFFF), lineHeight = 1f)
        pts.forEachIndexed { i, p ->
            drawCircle(Color(0xFF0D1534), 6 * d, Offset(p.x * d, p.y * d))
            drawCircle(Hue.accent, 6 * d, Offset(p.x * d, p.y * d), style = Stroke(3.5f * d))
            val t = measurer.measure(hours[i].temp, tempStyle)
            drawText(t, topLeft = Offset(p.x * d - t.size.width / 2f, (p.y - 14) * d - t.size.height * 0.85f))
            val h = measurer.measure(hours[i].label, hourStyle)
            drawText(h, topLeft = Offset(p.x * d - h.size.width / 2f, 144 * d - h.size.height * 0.85f))
        }
    }
}

/** Now, the chosen day by hours, and the next days; ← → choose the day. */
@Composable
fun WeatherCard(view: WeatherView, k: Float, now: LocalDateTime, focused: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 26.dp)) {
        Row(Modifier.height((68 * k).dp), verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(view.icon, Modifier.size((60 * k).dp))
            Spacer(Modifier.width(20.dp))
            BasicText(view.temp, style = wtext(64f * k, 300, lineHeight = 1f, spacing = (-2).sp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                BasicText(view.text, style = wtext(22f * k, 500, lineHeight = 1.25f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (view.detail.isNotEmpty()) BasicText(view.detail, style = wtext(17f * k, 400, Hue.textSecondary, 1.3f), maxLines = 1)
            }
            if (view.city.isNotEmpty()) BasicText(view.city, style = wtext(22f * k, 500, lineHeight = 1.3f), maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        WeatherCurve(view, now, k)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            view.days.forEach { day ->
                val bg = if (day.selected) Color.White.copy(alpha = if (focused) 0.21f else 0.14f) else Color.Transparent
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(bg, RoundedCornerShape(24.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(day.label, style = wtext(17f * k, 400, if (day.today) Hue.accent else Hue.textSecondary, 1f), maxLines = 1)
                    WeatherGlyph(day.icon, Modifier.padding(vertical = 4.dp).size((36 * k).dp))
                    BasicText(day.high, style = wtext(22f * k, 600, lineHeight = 1f))
                    Spacer(Modifier.height(5.dp))
                    BasicText(day.low, style = wtext(17f * k, 400, Hue.textSecondary, 1f))
                }
            }
        }
    }
}

// ---------- calendar ----------

/** Monday first: the month grid starts on Monday. */
private fun weekdays() = if (Lang.ru) listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс") else listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
private fun Dots(list: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        list.take(4).forEach { Box(Modifier.size(8.dp).background(Color(Cal.COLORS[it % Cal.COLORS.size]), CircleShape)) }
    }
}

/** Fades and slides a block in whenever `key` changes (`dx`: from which side, in px; `dy` from below). */
@Composable
private fun Entering(key: Any, dx: Float, dy: Float, ms: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (dx == 0f && dy == 0f) return@LaunchedEffect
        anim.snapTo(1f)
        anim.animateTo(0f, tween(ms, easing = Ease.out))
    }
    Box(modifier.graphicsLayer { val v = anim.value; alpha = 1f - v; translationX = dx * v * density; translationY = dy * v * density }) { content() }
}

/** The events of one day with the days around it; Up turns it into the month. The day and month layers slide past each other. */
@Composable
fun CalendarCard(
    data: CalendarData?, offset: Int, nowMs: Long, h24: Boolean, focused: Boolean,
    monthMode: Boolean, month: Int, dayDir: Int, k: Float, loading: Boolean,
) {
    val dayStart = Cal.startOfDay(nowMs, offset)
    val events = remember(data, dayStart, nowMs, h24, Lang.ru) { Cal.eventsOfDay(data, dayStart, nowMs, h24) }
    val rowHeight = 24 + 44.5f * k + 12
    val fit = maxOf(3, floor(420 / rowHeight).toInt())
    val shown = if (events.size > fit) events.take(fit - 1) else events
    val more = events.size - shown.size
    val many = (data?.sources?.size ?: 0) > 1
    val mode by animateFloatAsState(if (monthMode) 1f else 0f, tween(440, easing = Ease.out), label = "mode")
    val title = if (monthMode) Cal.monthView(data, nowMs, month, dayStart).year.toString() else Cal.relativeDay(offset)
    val heading = if (monthMode) Cal.monthView(data, nowMs, month, dayStart).name else Cal.dayHeading(nowMs, offset)

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavButton(Glyph.CHEVRON_LEFT)
            Entering(title + heading, 0f, 6f, 260, Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Column {
                    BasicText(title.uppercase(), style = wtext(14f * k, 600, Hue.accent, 1f, (14f * k * 0.12f).sp))
                    Spacer(Modifier.height(6.dp))
                    BasicText(heading, style = wtext(26f * k, 500, lineHeight = 1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            NavButton(Glyph.CHEVRON_RIGHT)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp).clipToBounds()) {
            val h = maxHeight.value
            // the day: the strip of days and that day's events
            Column(Modifier.fillMaxSize().graphicsLayer { translationY = mode * h * density }) {
                DayStrip(data, offset, nowMs, focused, k)
                Entering(offset, if (dayDir > 0) 36f else if (dayDir < 0) -36f else 0f, 0f, 300, Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp)) {
                    Column {
                        shown.forEach { e -> EventRow(e, k, many, data) }
                        if (more > 0) BasicText(tr("and $more more", "и ещё $more"), Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = wtext(17f * k, 400, Hue.textSecondary))
                        if (events.isEmpty()) {
                            BasicText(
                                if (loading) tr("Loading the calendar…", "Загружаю календарь…") else if (data != null) tr("Nothing on this day.", "В этот день событий нет.") else tr("The calendar has not loaded yet.", "Календарь пока не загрузился."),
                                Modifier.fillMaxWidth().padding(top = 70.dp), style = wtext(22f * k, 400, Hue.textSecondary).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            )
                        }
                    }
                }
            }
            // the month: comes down from above and pushes the days out
            Column(Modifier.fillMaxSize().graphicsLayer { translationY = (mode - 1f) * h * density }) {
                Row(Modifier.fillMaxWidth()) {
                    weekdays().forEach { BasicText(it, Modifier.weight(1f).padding(top = 6.dp, bottom = 10.dp), style = wtext(14f * k, 400, Hue.textSecondary, 1f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) }
                }
                MonthPanels(data, nowMs, month, dayStart, k, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun NavButton(glyph: Glyph) {
    Box(Modifier.size(52.dp).background(Color(0x1AFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
        LineGlyph(glyph, Modifier.size(24.dp), strokeWidth = 3f)
    }
}

/** Seven days with the chosen one in the middle under a highlight; the days slide under it. */
@Composable
private fun DayStrip(data: CalendarData?, offset: Int, nowMs: Long, focused: Boolean, k: Float) {
    val slide by animateFloatAsState(offset.toFloat(), tween(320, easing = Ease.out), label = "strip")
    BoxWithConstraints(Modifier.fillMaxWidth().height((14f * k + 22f * k * 1.2f + 42f).dp).clipToBounds()) {
        val cell = maxWidth.value / 7f
        Box(Modifier.offset((cell * 3).dp).width(cell.dp).fillMaxHeight().background(Color.White.copy(alpha = if (focused) 0.22f else 0.16f), RoundedCornerShape(22.dp)))
        val zone = ZoneId.systemDefault()
        for (o in offset - 9..offset + 9) {
            key(o) {
                val start = Cal.startOfDay(nowMs, o)
                val date = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val accent = o == 0 || o == offset
                Column(
                    Modifier.width(cell.dp).graphicsLayer { translationX = (o + 3 - slide) * cell * density }.padding(top = 12.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(Texts.dayShort(date), style = wtext(14f * k, 400, if (accent) Hue.accent else Hue.textSecondary, 1f))
                    Spacer(Modifier.height(4.dp))
                    BasicText(date.dayOfMonth.toString(), style = wtext(22f * k, 600, if (o == 0) Hue.accent else Hue.textPrimary, 1.2f))
                    Dots(remember(data, start) { Cal.dotsOfDay(data, start) }, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: DayEvent, k: Float, many: Boolean, data: CalendarData?) {
    val now = e.state == DayEvent.State.NOW
    var m = Modifier.padding(bottom = 12.dp).fillMaxWidth()
        .background(if (now) Color(0x2EFFC36B) else Color(0x12FFFFFF), RoundedCornerShape(20.dp))
    if (now) m = m.border(2.dp, Color(0x8CFFC36B), RoundedCornerShape(20.dp))
    if (e.state == DayEvent.State.PAST) m = m.graphicsLayer { alpha = 0.42f }
    Row(m.padding(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width((84 * k).dp)) {
            BasicText(e.time, style = wtext(22f * k, 600, lineHeight = 1.15f), maxLines = 2)
            if (e.note.isNotEmpty()) BasicText(e.note, Modifier.padding(top = 4.dp), style = wtext(14f * k, 400, if (now) Hue.accent else Hue.textSecondary), maxLines = 1)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(5.dp).height((44.5f * k).dp).background(Color(Cal.COLORS[e.src % Cal.COLORS.size]), RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(e.title, style = wtext(22f * k, 500), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = listOf(e.place, if (many) data?.sources?.getOrNull(e.src)?.name.orEmpty() else "").filter { it.isNotEmpty() }.joinToString(" · ")
            if (place.isNotEmpty()) BasicText(place, Modifier.padding(top = 4.dp), style = wtext(17f * k, 400, Hue.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The months side by side; the chosen one is in view and they slide past. */
@Composable
private fun MonthPanels(data: CalendarData?, nowMs: Long, month: Int, selectedDay: Long, k: Float, modifier: Modifier) {
    val slide by animateFloatAsState(month.toFloat(), tween(380, easing = Ease.out), label = "month")
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = maxWidth.value
        for (m in month - 1..month + 1) {
            key(m) {
                val view = remember(data, nowMs, m, selectedDay, Lang.ru) { Cal.monthView(data, nowMs, m, selectedDay) }
                Column(Modifier.fillMaxSize().graphicsLayer { translationX = (m - slide) * w * density }) {
                    view.weeks.forEach { week ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            week.forEach { c ->
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                                        .background(if (c.selected) Color(0x29FFFFFF) else Color.Transparent, RoundedCornerShape(20.dp))
                                        .graphicsLayer { alpha = if (c.inMonth) 1f else 0.3f },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    BasicText(c.day.toString(), style = wtext(22f * k, if (c.today) 600 else 500, if (c.today) Hue.accent else Hue.textPrimary, 1.1f))
                                    Dots(c.dots, Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
