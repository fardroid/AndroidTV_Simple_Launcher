package tld.my.hearth

// Time-of-day and date words. A port of src/lib/clock.ts.

import java.time.LocalDate
import java.time.LocalDateTime

object Texts {
    private val DAYS_RU = listOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    private val MONTHS_RU = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val MONTH_NAMES_RU = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
    private val DAY_SHORT_RU = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
    private val DAYS_EN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val MONTH_NAMES_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val DAY_SHORT_EN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** A month's name as a heading, January = 1: "October". */
    fun monthName(month: Int) = (if (Lang.ru) MONTH_NAMES_RU else MONTH_NAMES_EN)[month - 1]

    /** A short weekday name: "Sun". */
    fun dayShort(date: LocalDate) = (if (Lang.ru) DAY_SHORT_RU else DAY_SHORT_EN)[date.dayOfWeek.value % 7]

    /** "Sunday" */
    fun dayName(date: LocalDate): String =
        if (Lang.ru) DAYS_RU[date.dayOfWeek.value % 7].replaceFirstChar { it.uppercase() } else DAYS_EN[date.dayOfWeek.value % 7]

    /** "23 September" */
    fun dateText(date: LocalDate) = "${date.dayOfMonth} ${if (Lang.ru) MONTHS_RU[date.monthValue - 1] else MONTH_NAMES_EN[date.monthValue - 1]}"

    /** The two lines beside the clock: "Sunday" and "11 October 2026". */
    fun dateLines(date: LocalDate): Pair<String, String> = dayName(date) to "${dateText(date)} ${date.year}"

    /** "Sunday, 11 October" */
    fun dayTitle(date: LocalDate) = "${dayName(date)}, ${dateText(date)}"

    /** The right form of a counted noun: plural(2, "день", "дня", "дней") → "дня". */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val a = Math.abs(n) % 100; val b = a % 10
        return if (a in 11..19) many else if (b in 2..4) few else if (b == 1) one else many
    }

    /** "15:42" / "3:42" with "PM" beside it in 12-hour format. */
    fun timeParts(t: LocalDateTime, h24: Boolean): Pair<String, String> {
        val h = t.hour
        val m = t.minute.toString().padStart(2, '0')
        return if (h24) "${h.toString().padStart(2, '0')}:$m" to ""
        else "${if (h % 12 == 0) 12 else h % 12}:$m" to if (h < 12) "AM" else "PM"
    }

    fun clock(t: LocalDateTime, h24: Boolean): String = timeParts(t, h24).let { if (it.second.isEmpty()) it.first else it.first + " " + it.second }
}
