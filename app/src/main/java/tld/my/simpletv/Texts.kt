package tld.my.simpletv

// Time-of-day and date words. A port of src/lib/clock.ts. English and Russian are written here; the other languages take
// the platform's own wording (java.time with the language's locale, as the webOS app takes Intl's).

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

object Texts {
    private val DAYS_RU = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    private val MONTHS_RU = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val MONTH_NAMES_RU = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    private val DAY_SHORT_RU = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    private val DAYS_EN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val MONTH_NAMES_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DAY_SHORT_EN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** Neither English nor Russian: the words come from the platform. */
    private fun own() = Lang.current != Language.EN && Lang.current != Language.RU

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase(Lang.current.locale) }

    /** The platform's wording of a date in the current language, e.g. "d MMMM" → "11. Oktober". */
    private fun pattern(skeleton: String, date: LocalDate): String {
        val locale = Lang.current.locale
        // null off the box (the unit tests): the plain day-month order then
        val best: String? = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
        return cap(DateTimeFormatter.ofPattern(best ?: skeleton.replace("dMMMM", "d MMMM").replace("yMMMMd", "d MMMM y"), locale).format(date))
    }

    /** A month's name as a heading, January = 1: "October". */
    fun monthName(month: Int) =
        if (own()) cap(java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Lang.current.locale).let { if (it.all(Char::isDigit)) java.time.Month.of(month).getDisplayName(TextStyle.FULL, Lang.current.locale) else it })
        else (if (Lang.ru) MONTH_NAMES_RU else MONTH_NAMES_EN)[month - 1]

    /** A short weekday name: "Sun". */
    fun dayShort(date: LocalDate) =
        if (own()) cap(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Lang.current.locale))
        else (if (Lang.ru) DAY_SHORT_RU else DAY_SHORT_EN)[date.dayOfWeek.value % 7]

    /** "Sunday" */
    fun dayName(date: LocalDate): String = when {
        own() -> cap(date.dayOfWeek.getDisplayName(TextStyle.FULL, Lang.current.locale))
        Lang.ru -> DAYS_RU[date.dayOfWeek.value % 7].replaceFirstChar { it.uppercase() }
        else -> DAYS_EN[date.dayOfWeek.value % 7]
    }

    /** "23 September" */
    fun dateText(date: LocalDate) =
        if (own()) pattern("dMMMM", date)
        else "${date.dayOfMonth} ${if (Lang.ru) MONTHS_RU[date.monthValue - 1] else MONTH_NAMES_EN[date.monthValue - 1]}"

    /** "11 October 2026" */
    private fun dateYear(date: LocalDate) = if (own()) pattern("yMMMMd", date) else "${dateText(date)} ${date.year}"

    /** The two lines beside the clock: "Sunday" and "11 October 2026". */
    fun dateLines(date: LocalDate): Pair<String, String> = dayName(date) to dateYear(date)

    /** "Sunday, 11 October" */
    fun dayTitle(date: LocalDate) = "${dayName(date)}, ${dateText(date)}"

    /** "15:42" / "3:42" with "PM" beside it in 12-hour format. */
    fun timeParts(t: LocalDateTime, h24: Boolean): Pair<String, String> {
        val h = t.hour
        val m = t.minute.toString().padStart(2, '0')
        return if (h24) "${h.toString().padStart(2, '0')}:$m" to ""
        else "${if (h % 12 == 0) 12 else h % 12}:$m" to if (h < 12) "AM" else "PM"
    }

    fun clock(t: LocalDateTime, h24: Boolean): String = timeParts(t, h24).let { if (it.second.isEmpty()) it.first else it.first + " " + it.second }
}
