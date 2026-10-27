package tld.my.hearth

// The calendar card: the events of a day from iCal feeds (Google Calendar's secret addresses). A port of src/lib/calendar.ts:
// turning the feeds into days, the month grid, the words, the saved copy. Fetching is in Net.kt.

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class CalendarSource(val src: String, val name: String)

data class CalendarData(
    /** the addresses this is from, as the settings list them (a different list means different data) */
    val key: String,
    val at: Long,
    val sources: List<CalendarSource>,
    /** every event of all the calendars; `src` is the index in `sources` */
    val events: List<Occurrence>,
)

data class DayEvent(val time: String, val note: String, val title: String, val place: String, val src: Int, val state: State) {
    enum class State { PAST, NOW, NEXT, ALLDAY }
}

data class MonthCell(val start: Long, val day: Int, val inMonth: Boolean, val today: Boolean, val selected: Boolean, val dots: List<Int>)
data class MonthView(val name: String, val year: Int, val weeks: List<List<MonthCell>>)

object Cal {
    /** How far back and forward the card can be moved (days from today). */
    const val BACK_DAYS = 62
    const val AHEAD_DAYS = 186
    const val FRESH_MS = 15 * 60 * 1000L
    const val SHOWN_MS = 48 * 60 * 60 * 1000L

    /** The colour of each calendar's bar, in the order the calendars are read. */
    val COLORS = listOf(0xFFFFC36B, 0xFF6FB4FF, 0xFFFF8FB1, 0xFF6FE3A0, 0xFFC39BFF, 0xFFFF9D6B)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun localTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    /** Local midnight `offset` days from the day `now` falls in. */
    fun startOfDay(now: Long, offset: Int): Long = localDate(now).plusDays(offset.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    /** "webcal://" is the same thing over https; spaces from typing are dropped. */
    fun normalizeUrl(raw: String): String = raw.replace(Regex("\\s+"), "").replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")

    fun validUrl(url: String) = Regex("^https?://[^\\s'\"`$\\\\]+$", RegexOption.IGNORE_CASE).matches(url) && url.length <= 600

    fun parse(text: String, src: String, index: Int, now: Long): Pair<CalendarSource, List<Occurrence>>? {
        if (!text.contains("BEGIN:VCALENDAR")) return null
        val events = Ics.expand(text, startOfDay(now, -BACK_DAYS), startOfDay(now, AHEAD_DAYS + 1)).map { it.copy(src = index) }
        return CalendarSource(src, Ics.calendarName(text)) to events
    }

    fun join(key: String, parts: List<Pair<CalendarSource, List<Occurrence>>>, now: Long): CalendarData {
        val events = parts.flatMapIndexed { i, p -> p.second.map { it.copy(src = i) } }.sortedWith(compareBy({ it.start }, { it.end }))
        return CalendarData(key, now, parts.map { it.first }, events)
    }

    private fun onDay(e: Occurrence, dayStart: Long, dayEnd: Long) =
        (e.end > dayStart && e.start < dayEnd) || (e.start == e.end && e.start >= dayStart && e.start < dayEnd)

    /** The events of the day that starts at `dayStart`, with what is over and what is going on at `now`. */
    fun eventsOfDay(data: CalendarData?, dayStart: Long, now: Long, h24: Boolean): List<DayEvent> {
        if (data == null) return emptyList()
        val dayEnd = startOfDay(dayStart, 1)
        val todayStart = startOfDay(now, 0)
        return data.events.filter { onDay(it, dayStart, dayEnd) }
            .sortedWith(compareBy({ if (it.allDay) 0 else 1 }, { it.start }))
            .map { e ->
                if (e.allDay) DayEvent(tr("All day", "Весь день"), "", e.title, e.place, e.src, DayEvent.State.ALLDAY)
                else {
                    val startsToday = e.start >= dayStart
                    val endsToday = e.end <= dayEnd
                    val state = when {
                        dayStart < todayStart -> DayEvent.State.PAST
                        dayStart == todayStart -> when {
                            (e.end <= now && e.end > e.start) || (e.end == e.start && e.start < now) -> DayEvent.State.PAST
                            e.start <= now -> DayEvent.State.NOW
                            else -> DayEvent.State.NEXT
                        }
                        else -> DayEvent.State.NEXT
                    }
                    var note = if (e.end > e.start && endsToday) tr("until ", "до ") + Texts.clock(localTime(e.end), h24) else if (e.end > e.start) tr("until tomorrow", "до следующего дня") else ""
                    if (state == DayEvent.State.NOW) note = tr("now", "сейчас")
                    DayEvent(if (startsToday) Texts.clock(localTime(e.start), h24) else tr("since yesterday", "с вчера"), note, e.title, e.place, e.src, state)
                }
            }
    }

    /** Which calendars have events on the day (for the dots under the days). */
    fun dotsOfDay(data: CalendarData?, dayStart: Long): List<Int> {
        if (data == null) return emptyList()
        val dayEnd = startOfDay(dayStart, 1)
        val seen = data.events.filter { onDay(it, dayStart, dayEnd) }.map { it.src }.toSet()
        return data.sources.indices.filter { it in seen }
    }

    /** "Today", "Tomorrow", "In 3 days", "2 days ago". */
    fun relativeDay(offset: Int): String = when (offset) {
        0 -> tr("Today", "Сегодня"); 1 -> tr("Tomorrow", "Завтра"); -1 -> tr("Yesterday", "Вчера"); 2 -> tr("In 2 days", "Послезавтра")
        else -> {
            val n = Math.abs(offset)
            val words = count(n, "day", "days", "день", "дня", "дней")
            if (offset > 0) tr("In ", "Через ") + words else words + tr(" ago", " назад")
        }
    }

    fun dayHeading(now: Long, offset: Int) = Texts.dayTitle(localDate(now).plusDays(offset.toLong()))

    /** Which calendars have events on each day of [from, to): day start → calendar indexes. */
    private fun sourcesByDay(data: CalendarData?, from: Long, to: Long): Map<Long, List<Int>> {
        val out = HashMap<Long, MutableList<Int>>()
        data?.events?.forEach { e ->
            val last = startOfDay(maxOf(e.start, e.end - 1), 0)
            var day = startOfDay(e.start, 0)
            while (day <= last) {
                if (day in from until to) out.getOrPut(day) { ArrayList() }.let { if (e.src !in it) it.add(e.src) }
                day = startOfDay(day, 1)
            }
        }
        return out
    }

    /** The month `monthOffset` months from the current one, as 6 weeks, Monday first. */
    fun monthView(data: CalendarData?, now: Long, monthOffset: Int, selectedDay: Long): MonthView {
        val today = startOfDay(now, 0)
        val first = localDate(now).withDayOfMonth(1).plusMonths(monthOffset.toLong())
        val lead = (first.dayOfWeek.value + 6) % 7
        val gridFirst = first.minusDays(lead.toLong())
        val gridStart = gridFirst.atStartOfDay(zone).toInstant().toEpochMilli()
        val gridEnd = gridFirst.plusDays(42).atStartOfDay(zone).toInstant().toEpochMilli()
        val by = sourcesByDay(data, gridStart, gridEnd)
        val weeks = (0 until 6).map { w ->
            (0 until 7).map { d ->
                val date = gridFirst.plusDays((w * 7 + d).toLong())
                val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                MonthCell(start, date.dayOfMonth, date.monthValue == first.monthValue, start == today, start == selectedDay, (by[start] ?: emptyList()).sorted())
            }
        }
        return MonthView(Texts.monthName(first.monthValue), first.year, weeks)
    }

    /** Whole months from the month `now` is in to the month `dayOffset` days from today is in. */
    fun monthOffsetOfDay(now: Long, dayOffset: Int): Int {
        val a = localDate(now); val b = a.plusDays(dayOffset.toLong())
        return (b.year - a.year) * 12 + b.monthValue - a.monthValue
    }

    /** The first day of the month `monthOffset` months away, as days from today. */
    fun dayOffsetOfMonth(now: Long, monthOffset: Int): Int {
        val today = localDate(now)
        return (today.withDayOfMonth(1).plusMonths(monthOffset.toLong()).toEpochDay() - today.toEpochDay()).toInt()
    }

    fun usable(data: CalendarData?, key: String, now: Long) = data?.takeIf { it.key == key && now - it.at in 0 until SHOWN_MS }
    fun isFresh(data: CalendarData?, key: String, now: Long) = data != null && data.key == key && now - data.at in 0 until FRESH_MS

    // ---------- the saved copy ----------

    fun toJson(d: CalendarData): String = JSONObject().apply {
        put("key", d.key); put("at", d.at)
        put("sources", JSONArray(d.sources.map { JSONObject().put("src", it.src).put("name", it.name) }))
        put("events", JSONArray(d.events.map { JSONObject().put("s", it.start).put("e", it.end).put("a", it.allDay).put("t", it.title).put("p", it.place).put("c", it.src) }))
    }.toString()

    fun fromJson(s: String?): CalendarData? = try {
        val o = JSONObject(s!!)
        val src = o.getJSONArray("sources"); val ev = o.getJSONArray("events")
        CalendarData(o.getString("key"), o.getLong("at"),
            (0 until src.length()).map { src.getJSONObject(it).let { x -> CalendarSource(x.getString("src"), x.optString("name")) } },
            (0 until ev.length()).map { ev.getJSONObject(it).let { x -> Occurrence(x.getLong("s"), x.getLong("e"), x.getBoolean("a"), x.getString("t"), x.optString("p"), x.optInt("c")) } })
    } catch (_: Exception) { null }
}
