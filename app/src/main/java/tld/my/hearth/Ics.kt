package tld.my.hearth

// Reading an iCalendar (.ics) feed, such as Google Calendar's "secret address in iCal format", and working out which events
// fall in a stretch of time (repeating events are expanded). Pure functions, no network. A port of src/lib/ics.ts.

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class Occurrence(
    /** ms since 1970 */
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val place: String,
    /** which calendar it came from (an index into the list of calendars) */
    val src: Int = 0,
)

object Ics {
    private const val DAY = 86_400_000L
    private val WEEKDAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
    /** Safety limit on how many days one repeating event is walked through. */
    private const val MAX_WALK_DAYS = 20_000

    private enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    private data class ByDay(val n: Int, val wd: Int)

    private data class Rule(
        val freq: Freq, val interval: Int, val count: Int?, val until: Long?,
        val byDay: List<ByDay>, val byMonthDay: List<Int>, val byMonth: List<Int>,
    )

    data class Wall(val y: Int, val mo: Int, val d: Int, val h: Int, val mi: Int, val s: Int)

    private data class RawEvent(
        val uid: String, val title: String, val place: String, val wall: Wall,
        /** null = the box's own zone, "UTC", or an IANA name */
        val tz: String?,
        val start: Long, val end: Long, val allDay: Boolean, val rule: Rule?,
        val exdates: List<Long>, val recurrenceId: Long?, val cancelled: Boolean,
    )

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private data class Stamp(val wall: Wall, val tz: String?, val ms: Long, val allDay: Boolean)

    // ---------- reading the text ----------

    private fun unfold(text: String): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("\n[ \t]"), "").split('\n')

    fun unescapeText(value: String): String =
        Regex("""\\([nN,;\\])""").replace(value) { m -> val c = m.groupValues[1]; if (c == "n" || c == "N") "\n" else c }

    private fun parseProp(line: String): Prop? {
        val colon = line.indexOf(':')
        if (colon < 1) return null
        val head = line.substring(0, colon).split(';')
        val params = HashMap<String, String>()
        for (i in 1 until head.size) {
            val eq = head[i].indexOf('=')
            if (eq > 0) params[head[i].substring(0, eq).uppercase()] = head[i].substring(eq + 1).replace("\"", "")
        }
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun zoneOf(tz: String?): ZoneId = when (tz) {
        null -> ZoneId.systemDefault()
        "UTC" -> ZoneOffset.UTC
        else -> try { ZoneId.of(tz) } catch (_: Exception) { ZoneId.systemDefault() }
    }

    /** The moment a zone's wall-clock time happens. */
    fun wallToMs(w: Wall, tz: String?): Long =
        LocalDateTime.of(w.y, w.mo, w.d, w.h, w.mi, w.s).atZone(zoneOf(tz)).toInstant().toEpochMilli()

    private val STAMP = Regex("""^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?)?(Z)?$""")

    private fun parseStamp(p: Prop): Stamp? {
        val m = STAMP.find(p.value.trim()) ?: return null
        val g = m.groupValues
        val allDay = g[4].isEmpty() || p.params["VALUE"] == "DATE"
        val wall = Wall(g[1].toInt(), g[2].toInt(), g[3].toInt(),
            if (allDay) 0 else g[4].toInt(), if (allDay) 0 else g[5].toInt(), if (allDay || g[6].isEmpty()) 0 else g[6].toInt())
        val tz = if (allDay) null else if (g[7].isNotEmpty()) "UTC" else p.params["TZID"]
        return try { Stamp(wall, tz, wallToMs(wall, tz), allDay) } catch (_: Exception) { null }
    }

    private val DURATION = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

    private fun parseDuration(v: String): Long? {
        val m = DURATION.find(v.trim()) ?: return null
        val g = m.groupValues
        fun n(i: Int) = if (g[i].isEmpty()) 0L else g[i].toLong()
        val ms = (n(2) * 7 + n(3)) * DAY + n(4) * 3_600_000 + n(5) * 60_000 + n(6) * 1000
        return if (g[1] == "-") -ms else ms
    }

    private val BYDAY = Regex("""^([+-]?\d+)?(SU|MO|TU|WE|TH|FR|SA)$""")

    private fun parseRule(value: String): Rule? {
        val part = HashMap<String, String>()
        value.split(';').forEach { kv -> val i = kv.indexOf('='); if (i > 0) part[kv.substring(0, i).uppercase()] = kv.substring(i + 1) }
        val freq = when (part["FREQ"]) { "DAILY" -> Freq.DAILY; "WEEKLY" -> Freq.WEEKLY; "MONTHLY" -> Freq.MONTHLY; "YEARLY" -> Freq.YEARLY; else -> return null }
        fun nums(s: String?) = s?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val byDay = (part["BYDAY"]?.split(',') ?: emptyList()).mapNotNull { s ->
            BYDAY.find(s.trim())?.let { m -> ByDay(m.groupValues[1].toIntOrNull() ?: 0, WEEKDAYS.indexOf(m.groupValues[2])) }
        }
        val until = part["UNTIL"]?.let { u -> parseStamp(Prop("UNTIL", emptyMap(), u))?.let { if (it.allDay) it.ms + DAY - 1 else it.ms } }
        return Rule(freq, maxOf(1, part["INTERVAL"]?.toIntOrNull() ?: 1), part["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }, until,
            byDay, nums(part["BYMONTHDAY"]), nums(part["BYMONTH"]))
    }

    private fun readEvents(text: String): List<RawEvent> {
        val events = ArrayList<RawEvent>()
        var props: ArrayList<Prop>? = null
        var depth = 0
        for (line in unfold(text)) {
            if (line == "BEGIN:VEVENT") { props = ArrayList(); depth = 0; continue }
            if (line == "END:VEVENT") { props?.let { buildEvent(it)?.let(events::add) }; props = null; continue }
            if (props == null) continue
            // nested components (alarms) have their own properties, which must not be mistaken for the event's
            if (line.startsWith("BEGIN:")) { depth++; continue }
            if (line.startsWith("END:")) { depth--; continue }
            if (depth > 0) continue
            parseProp(line)?.let(props::add)
        }
        return events
    }

    private fun buildEvent(props: List<Prop>): RawEvent? {
        fun get(name: String) = props.firstOrNull { it.name == name }
        val stamp = get("DTSTART")?.let(::parseStamp) ?: return null
        val endStamp = get("DTEND")?.let(::parseStamp)
        val dur = get("DURATION")?.let { parseDuration(it.value) }
        var end = endStamp?.ms ?: if (dur != null) stamp.ms + dur else if (stamp.allDay) stamp.ms + DAY else stamp.ms
        if (end < stamp.ms) end = stamp.ms
        val exdates = ArrayList<Long>()
        props.filter { it.name == "EXDATE" }.forEach { p ->
            p.value.split(',').forEach { v -> parseStamp(Prop("EXDATE", p.params, v))?.let { exdates.add(it.ms) } }
        }
        return RawEvent(
            uid = get("UID")?.value ?: "",
            title = get("SUMMARY")?.let { unescapeText(it.value).trim() } ?: "",
            place = get("LOCATION")?.let { unescapeText(it.value).replace(Regex("\\s+"), " ").trim() } ?: "",
            wall = stamp.wall, tz = stamp.tz, start = stamp.ms, end = end, allDay = stamp.allDay,
            rule = get("RRULE")?.let { parseRule(it.value) },
            exdates = exdates,
            recurrenceId = get("RECURRENCE-ID")?.let(::parseStamp)?.ms,
            cancelled = get("STATUS")?.value?.trim()?.uppercase() == "CANCELLED",
        )
    }

    // ---------- repeating events ----------

    private fun dayNumber(y: Int, mo: Int, d: Int): Long = LocalDate.of(y, mo, d).toEpochDay()
    private fun weekdayOf(dn: Long): Int = LocalDate.ofEpochDay(dn).dayOfWeek.value % 7     // 0 = Sunday
    private fun daysIn(y: Int, mo: Int): Int = LocalDate.of(y, mo, 1).lengthOfMonth()

    private fun dayMatches(rule: Rule, ev: RawEvent, dn: Long, startDn: Long): Boolean {
        val date = LocalDate.ofEpochDay(dn)
        val y = date.year; val mo = date.monthValue; val d = date.dayOfMonth; val wd = weekdayOf(dn)
        fun inByDay() = rule.byDay.any { b ->
            if (b.wd != wd) false
            else if (b.n == 0) true
            else if (b.n > 0) (d - 1) / 7 + 1 == b.n
            else (daysIn(y, mo) - d) / 7 + 1 == -b.n
        }
        fun inMonthDay() = if (rule.byMonthDay.isNotEmpty())
            rule.byMonthDay.any { x -> if (x > 0) x == d else daysIn(y, mo) + x + 1 == d }
        else d == ev.wall.d
        return when (rule.freq) {
            Freq.DAILY -> (dn - startDn) % rule.interval == 0L && (rule.byDay.isEmpty() || inByDay())
            Freq.WEEKLY -> {
                val weekStart = startDn - ((weekdayOf(startDn) + 6) % 7)
                val week = Math.floorDiv(dn - weekStart, 7L)
                if (week % rule.interval != 0L) false
                else if (rule.byDay.isNotEmpty()) inByDay() else wd == weekdayOf(startDn)
            }
            Freq.MONTHLY -> {
                val months = (y - ev.wall.y) * 12 + (mo - ev.wall.mo)
                if (months % rule.interval != 0) false
                else if (rule.byDay.isNotEmpty()) inByDay() else inMonthDay()
            }
            Freq.YEARLY -> {
                if ((y - ev.wall.y) % rule.interval != 0) false
                else if (!(if (rule.byMonth.isNotEmpty()) mo in rule.byMonth else mo == ev.wall.mo)) false
                else if (rule.byDay.isNotEmpty()) inByDay() else inMonthDay()
            }
        }
    }

    /** Start times of a repeating event that could matter between `from` and `to`. */
    private fun repeatStarts(ev: RawEvent, rule: Rule, from: Long, to: Long, spanMs: Long): List<Long> {
        val out = ArrayList<Long>()
        val startDn = dayNumber(ev.wall.y, ev.wall.mo, ev.wall.d)
        val lastDn = Math.floorDiv(to, DAY) + 2
        var count = 0
        var dn = startDn
        var steps = 0
        while (dn <= lastDn && steps < MAX_WALK_DAYS) {
            if (dayMatches(rule, ev, dn, startDn)) {
                val date = LocalDate.ofEpochDay(dn)
                val start = wallToMs(Wall(date.year, date.monthValue, date.dayOfMonth, ev.wall.h, ev.wall.mi, ev.wall.s), ev.tz)
                if (rule.until != null && start > rule.until) break
                count++
                if (rule.count != null && count > rule.count) break
                if (start + spanMs > from && start < to) out.add(start)
            }
            dn++; steps++
        }
        return out
    }

    /** Every event (repeats included) that overlaps [from, to), in time order. */
    fun expand(text: String, from: Long, to: Long): List<Occurrence> {
        val events = readEvents(text)
        val replaced = HashMap<String, MutableList<Long>>()
        events.forEach { e -> e.recurrenceId?.let { replaced.getOrPut(e.uid) { ArrayList() }.add(it) } }
        val out = ArrayList<Occurrence>()
        val zone = ZoneId.systemDefault()
        for (e in events) {
            if (e.cancelled) continue
            val span = e.end - e.start
            fun add(start: Long) {
                var end = start + span
                if (e.allDay) {
                    // an all-day event spans whole local days even when a daylight-saving change falls inside
                    val days = Math.round(span.toDouble() / DAY)
                    end = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
                }
                if ((end > from && start < to) || (span == 0L && start >= from && start < to))
                    out.add(Occurrence(start, end, e.allDay, e.title.ifEmpty { tr("(no title)", "Без названия") }, e.place))
            }
            val rule = e.rule
            if (rule == null) { add(e.start); continue }
            val skip = e.exdates + (replaced[e.uid] ?: emptyList())
            repeatStarts(e, rule, from, to, span).forEach { s -> if (s !in skip) add(s) }
        }
        return out.sortedWith(compareBy({ it.start }, { it.end }))
    }

    /** The calendar's own name (X-WR-CALNAME), or an empty string. */
    fun calendarName(text: String): String =
        Regex("""^X-WR-CALNAME[^:\r\n]*:(.*)$""", RegexOption.MULTILINE).find(text)?.let { unescapeText(it.groupValues[1]).trim() } ?: ""
}
