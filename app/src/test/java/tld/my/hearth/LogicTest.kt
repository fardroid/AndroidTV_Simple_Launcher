package tld.my.hearth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class LogicTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) = java.time.LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    // the texts are checked in Russian, and once in English below
    @Before fun setZone() { TimeZone.setDefault(TimeZone.getTimeZone(zone)); Lang.apply(LangSetting.RU) }

    private val ics = """
        BEGIN:VCALENDAR
        X-WR-CALNAME:Дом
        BEGIN:VEVENT
        UID:weekly
        DTSTART;TZID=Europe/Rome:20261005T090000
        DTEND;TZID=Europe/Rome:20261005T093000
        RRULE:FREQ=WEEKLY;BYDAY=MO,WE
        EXDATE;TZID=Europe/Rome:20261028T090000
        SUMMARY:Зарядка
        BEGIN:VALARM
        SUMMARY:alarm
        END:VALARM
        END:VEVENT
        BEGIN:VEVENT
        UID:allday
        DTSTART;VALUE=DATE:20261026
        DTEND;VALUE=DATE:20261028
        SUMMARY:Отпуск
        LOCATION:Комо\, Италия
        END:VEVENT
        BEGIN:VEVENT
        UID:utc
        DTSTART:20261026T170000Z
        DURATION:PT1H
        SUMMARY:Звонок
        END:VEVENT
        BEGIN:VEVENT
        UID:gone
        DTSTART:20261026T120000Z
        STATUS:CANCELLED
        SUMMARY:Отменено
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test fun expandsRepeatsExceptionsAndZones() {
        val list = Ics.expand(ics, ms(2026, 10, 26), ms(2026, 11, 2))
        val workouts = list.filter { it.title == "Зарядка" }.map { Cal.localTime(it.start).toLocalDate() }
        assertEquals(listOf(LocalDate.of(2026, 10, 26)), workouts)       // Wed 28 is an exception, Mon 2 Nov is outside
        assertEquals(ms(2026, 10, 26, 9), list.first { it.title == "Зарядка" }.start)
        val call = list.first { it.title == "Звонок" }
        assertEquals(ms(2026, 10, 26, 18), call.start)                        // 17:00 UTC is 18:00 in Rome after the clocks went back
        assertEquals(3_600_000L, call.end - call.start)
        val trip = list.first { it.title == "Отпуск" }
        assertTrue(trip.allDay)
        assertEquals("Комо, Италия", trip.place)
        assertTrue(list.none { it.title == "Отменено" || it.title == "alarm" })
        assertEquals("Дом", Ics.calendarName(ics))
    }

    @Test fun dayListMarksPastNowAndNext() {
        val now = ms(2026, 10, 26, 18, 20)
        val data = Cal.parse(ics, "x", 0, now)!!.let { Cal.join("k", listOf(it), now) }
        val day = Cal.eventsOfDay(data, Cal.startOfDay(now, 0), now, true)
        assertEquals(listOf("Весь день", "09:00", "18:00"), day.map { it.time })
        assertEquals(DayEvent.State.PAST, day[1].state)
        assertEquals(DayEvent.State.NOW, day[2].state)
        assertEquals("сейчас", day[2].note)
        assertEquals("Через 3 дня", Cal.relativeDay(3))
        assertEquals("5 дней назад", Cal.relativeDay(-5))
    }

    @Test fun monthGridStartsOnMonday() {
        val now = ms(2026, 10, 26, 12)
        val view = Cal.monthView(null, now, 0, Cal.startOfDay(now, 0))
        assertEquals("Октябрь", view.name)
        assertEquals(28, view.weeks[0][0].day)              // Mon 28 September
        assertTrue(view.weeks.flatten().single { it.today }.day == 26)
        assertEquals(1, Cal.monthOffsetOfDay(now, 10))
        assertEquals(6, Cal.dayOffsetOfMonth(now, 1))
    }

    @Test fun weatherReplyAndView() {
        val json = """{"current":{"temperature_2m":18.4,"apparent_temperature":-0.3,"weather_code":2,"is_day":1,"wind_speed_10m":3.6},
            "hourly":{"time":["2026-10-26T00:00","2026-10-26T01:00","2026-10-26T03:00","2026-10-27T00:00"],"temperature_2m":[10,11,12,9],"weather_code":[0,0,3,61],"is_day":[0,0,0,0]},
            "daily":{"time":["2026-10-26","2026-10-27"],"weather_code":[2,61],"temperature_2m_max":[19.6,15],"temperature_2m_min":[9.2,null]}}"""
        val d = Weather.parse(json, 45.46, 9.19, 1000L)
        assertNotNull(d)
        assertEquals(2, d!!.hourly.count { it.day == 0 })
        val v = Weather.view(d, false, "Милан")
        assertEquals("18°", v.temp)
        assertEquals("Ощущается как 0° · ветер 4 м/с", v.detail)
        assertEquals("Переменная облачность", v.text)
        assertEquals("–", v.days[1].low)
        assertEquals("Вт 27", v.days[1].label)
        assertNull(Weather.parse("{}", 0.0, 0.0, 0))
        assertEquals("64°", Weather.formatTemp(18.0, true))
    }

    @Test fun platesFollowBrightness() {
        val c = Config(iconScale = 80, bottomMargin = 50, showLabels = true)
        assertEquals(74, Plates.boardTop(c))                      // the same as the webOS app (scripts/test-boardlayout.mjs)
        val dark = FloatArray(16 * 9) { 0.1f }
        val light = FloatArray(16 * 9) { 0.9f }
        val board = Plates.rects(true, true, 74, 184)
        assertEquals(0x1CFFFFFFL, Plates.looks(PlateMode.AUTO, dark, board).clock.bg)
        assertTrue((Plates.looks(PlateMode.AUTO, light, board).clock.bg ushr 24) > 0x9A)
        // one bright spot behind any plate makes all of them dark, the same colour
        val half = FloatArray(Plates.MAP_W * Plates.MAP_H) { i -> if (i % Plates.MAP_W < Plates.MAP_W / 2) 1f else 0f }
        val mixed = Plates.looks(PlateMode.AUTO, half, board)
        assertEquals(mixed.clock.bg, mixed.calendar.bg)
        assertEquals(mixed.clock.bg, mixed.weather.bg)
        assertTrue((mixed.calendar.bg ushr 24) > 0x9A)
    }

    @Test fun settingsSurviveTheirText() {
        val c = Config(hidden = setOf("a.b"), clockSize = 150, calendars = listOf("https://example.com/x.ics"), tileShape = TileShape.BANNER)
        assertEquals(c, Config.fromJson(c.toJson()))
        assertEquals(Config(), Config.fromJson("not json"))
        assertEquals("https://p.example/c.ics", Cal.normalizeUrl(" webcal://p.example/ c.ics"))
    }

    @Test fun englishWords() {
        Lang.apply(LangSetting.EN)
        val now = ms(2026, 10, 26, 18, 20)
        val data = Cal.parse(ics, "x", 0, now)!!.let { Cal.join("k", listOf(it), now) }
        assertEquals(listOf("All day", "09:00", "18:00"), Cal.eventsOfDay(data, Cal.startOfDay(now, 0), now, true).map { it.time })
        assertEquals("In 3 days", Cal.relativeDay(3))
        assertEquals("5 days ago", Cal.relativeDay(-5))
        assertEquals("October", Cal.monthView(null, now, 0, Cal.startOfDay(now, 0)).name)
        assertEquals("Monday" to "26 October 2026", Texts.dateLines(LocalDate.of(2026, 10, 26)))
        val json = """{"current":{"temperature_2m":18.4,"apparent_temperature":-0.3,"weather_code":2,"is_day":1,"wind_speed_10m":3.6},
            "daily":{"time":["2026-10-26","2026-10-27"],"weather_code":[2,61],"temperature_2m_max":[19.6,15],"temperature_2m_min":[9.2,8]}}"""
        val v = Weather.view(Weather.parse(json, 45.46, 9.19, 1000L)!!, false, "Milan")
        assertEquals("Feels like 0° · wind 13 km/h", v.detail)
        assertEquals(listOf("Today", "Tue 27"), v.days.map { it.label })
        assertEquals(LangSetting.EN, Config.fromJson(Config(language = LangSetting.EN).toJson()).language)
    }
}
