package tld.my.hearth

// The weather card: now, the hours of a day and the next days, from Open-Meteo (free, no key). A port of src/lib/weather.ts:
// reading the reply, describing it, formatting. Fetching is in Net.kt.

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WeatherIcon { SUN, MOON, PARTLY, CMOON, CLOUD, FOG, RAIN, SNOW, STORM }

data class City(val name: String, val lat: Double, val lon: Double, val detail: String = "")

/** One reading in a day's curve (every 3 hours). */
data class HourPoint(val day: Int, val hour: Int, val tempC: Double, val code: Int, val isDay: Boolean)

data class DayPoint(val date: String, val code: Int, val highC: Double?, val lowC: Double?)

data class WeatherData(
    val lat: Double, val lon: Double,
    val tempC: Double, val code: Int, val isDay: Boolean,
    val feelsC: Double?, val windMs: Double?,
    val hourly: List<HourPoint>, val daily: List<DayPoint>,
    /** when it was read (ms since 1970) */
    val at: Long,
)

data class HourView(val label: String, val temp: String, val tempC: Double, val hour: Int, val icon: WeatherIcon)
data class DayView(val label: String, val icon: WeatherIcon, val high: String, val low: String, val today: Boolean, val selected: Boolean)
data class WeatherView(
    val icon: WeatherIcon, val temp: String, val text: String, val detail: String, val city: String,
    val day: Int, val dayName: String, val hours: List<HourView>, val days: List<DayView>,
)

object Weather {
    /** A reading younger than this is not fetched again. */
    const val FRESH_MS = 30 * 60 * 1000L
    /** An older reading than this is no longer shown. */
    const val SHOWN_MS = 6 * 60 * 60 * 1000L

    private fun coord(n: Double) = String.format(Locale.US, "%.3f", n)

    fun forecastUrl(lat: Double, lon: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&wind_speed_unit=ms&timezone=auto"

    fun geocodeUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=" + java.net.URLEncoder.encode(query.trim().take(60), "UTF-8") + "&count=8&language=" + (if (Lang.ru) "ru" else "en") + "&format=json"

    private fun JSONArray?.num(i: Int): Double? = if (this == null || i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

    /** Reads Open-Meteo's forecast reply; null when it isn't one. */
    fun parse(json: String, lat: Double, lon: Double, now: Long): WeatherData? = try {
        val data = JSONObject(json)
        val current = data.getJSONObject("current")
        val temp = current.getDouble("temperature_2m")
        val code = current.getInt("weather_code")
        val daily = data.optJSONObject("daily") ?: JSONObject()
        val dates = daily.optJSONArray("time") ?: JSONArray()
        val days = ArrayList<DayPoint>()
        for (i in 0 until minOf(7, dates.length())) {
            val dayCode = daily.optJSONArray("weather_code").num(i) ?: continue
            days.add(DayPoint(dates.getString(i), dayCode.toInt(), daily.optJSONArray("temperature_2m_max").num(i), daily.optJSONArray("temperature_2m_min").num(i)))
        }
        val hours = data.optJSONObject("hourly") ?: JSONObject()
        val times = hours.optJSONArray("time") ?: JSONArray()
        val hourly = ArrayList<HourPoint>()
        for (i in 0 until times.length()) {
            val t = times.optString(i)
            val day = days.indexOfFirst { t.startsWith(it.date) }
            if (day == -1 || t.length < 13) continue
            val hour = t.substring(11, 13).toIntOrNull() ?: continue
            val hourTemp = hours.optJSONArray("temperature_2m").num(i)
            val hourCode = hours.optJSONArray("weather_code").num(i)
            if (hour % 3 == 0 && hourTemp != null && hourCode != null)
                hourly.add(HourPoint(day, hour, hourTemp, hourCode.toInt(), hours.optJSONArray("is_day").num(i) != 0.0))
        }
        WeatherData(lat, lon, temp, code, current.optInt("is_day", 1) != 0,
            current.optDouble("apparent_temperature").takeIf { !it.isNaN() }, current.optDouble("wind_speed_10m").takeIf { !it.isNaN() },
            hourly, days, now)
    } catch (_: Exception) { null }

    fun parseCities(json: String): List<City> = try {
        val list = JSONObject(json).optJSONArray("results") ?: JSONArray()
        (0 until list.length()).mapNotNull { i ->
            val r = list.getJSONObject(i)
            val lat = r.optDouble("latitude"); val lon = r.optDouble("longitude")
            if (!r.has("name") || lat.isNaN() || lon.isNaN()) null
            else City(r.getString("name"), lat, lon, listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotEmpty() }.joinToString(", "))
        }
    } catch (_: Exception) { emptyList() }

    /** Words and a picture for a WMO weather code. */
    fun describe(code: Int, isDay: Boolean): Pair<String, WeatherIcon> = when (code) {
        0 -> tr("Clear", "Ясно") to if (isDay) WeatherIcon.SUN else WeatherIcon.MOON
        1 -> tr("Mainly clear", "Преимущественно ясно") to if (isDay) WeatherIcon.SUN else WeatherIcon.MOON
        2 -> tr("Partly cloudy", "Переменная облачность") to if (isDay) WeatherIcon.PARTLY else WeatherIcon.CMOON
        3 -> tr("Overcast", "Пасмурно") to WeatherIcon.CLOUD
        45, 48 -> tr("Fog", "Туман") to WeatherIcon.FOG
        51, 53, 55 -> tr("Drizzle", "Морось") to WeatherIcon.RAIN
        56, 57 -> tr("Freezing drizzle", "Ледяная морось") to WeatherIcon.RAIN
        61 -> tr("Light rain", "Небольшой дождь") to WeatherIcon.RAIN
        63 -> tr("Rain", "Дождь") to WeatherIcon.RAIN
        65 -> tr("Heavy rain", "Сильный дождь") to WeatherIcon.RAIN
        66, 67 -> tr("Freezing rain", "Ледяной дождь") to WeatherIcon.RAIN
        71 -> tr("Light snow", "Небольшой снег") to WeatherIcon.SNOW
        73 -> tr("Snow", "Снег") to WeatherIcon.SNOW
        75 -> tr("Heavy snow", "Сильный снег") to WeatherIcon.SNOW
        77 -> tr("Snow grains", "Снежная крупа") to WeatherIcon.SNOW
        80 -> tr("Light showers", "Небольшие ливни") to WeatherIcon.RAIN
        81 -> tr("Showers", "Ливни") to WeatherIcon.RAIN
        82 -> tr("Heavy showers", "Сильные ливни") to WeatherIcon.RAIN
        85, 86 -> tr("Snow showers", "Снегопад") to WeatherIcon.SNOW
        95 -> tr("Thunderstorm", "Гроза") to WeatherIcon.STORM
        96, 99 -> tr("Thunderstorm with hail", "Гроза с градом") to WeatherIcon.STORM
        else -> tr("Cloudy", "Облачно") to WeatherIcon.CLOUD
    }

    /** "29°" (always whole degrees, in the chosen unit; never "-0°"). */
    fun formatTemp(celsius: Double, fahrenheit: Boolean): String {
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        val rounded = value.roundToInt()
        return "${if (rounded == 0) 0 else rounded}°"
    }

    private fun dayLabel(date: String): String {
        val d = LocalDate.parse(date)
        return Texts.dayShort(d) + " " + d.dayOfMonth
    }

    /** "wind 4 m/s" in Russian; in English km/h, or mph with Fahrenheit. */
    private fun windText(ms: Double, fahrenheit: Boolean) = when {
        Lang.ru -> "ветер ${ms.roundToInt()} м/с"
        fahrenheit -> "wind ${(ms * 2.237).roundToInt()} mph"
        else -> "wind ${(ms * 3.6).roundToInt()} km/h"
    }

    fun view(data: WeatherData, fahrenheit: Boolean, cityName: String, day: Int = 0): WeatherView {
        val picked = day.coerceIn(0, maxOf(0, data.daily.size - 1))
        val (text, icon) = describe(data.code, data.isDay)
        val detail = listOfNotNull(
            data.feelsC?.let { tr("Feels like ", "Ощущается как ") + formatTemp(it, fahrenheit) },
            data.windMs?.let { windText(it, fahrenheit) },
        ).joinToString(" · ")
        return WeatherView(
            icon = icon, temp = formatTemp(data.tempC, fahrenheit), text = text, detail = detail, city = cityName,
            day = picked,
            dayName = if (picked == 0 || picked >= data.daily.size) tr("Today", "Сегодня") else Texts.dayTitle(LocalDate.parse(data.daily[picked].date)),
            hours = data.hourly.filter { it.day == picked }.map {
                HourView((if (it.hour < 10) "0" else "") + it.hour + ":00", formatTemp(it.tempC, fahrenheit), it.tempC, it.hour, describe(it.code, it.isDay).second)
            },
            days = data.daily.mapIndexed { i, d ->
                DayView(if (i == 0) tr("Today", "Сегодня") else dayLabel(d.date), describe(d.code, true).second,
                    d.highC?.let { formatTemp(it, fahrenheit) } ?: "–", d.lowC?.let { formatTemp(it, fahrenheit) } ?: "–", i == 0, i == picked)
            },
        )
    }

    fun isFresh(data: WeatherData?, city: City, now: Long) =
        data != null && data.lat == city.lat && data.lon == city.lon && now - data.at in 0 until FRESH_MS

    fun usable(data: WeatherData?, city: City, now: Long): WeatherData? =
        data?.takeIf { it.lat == city.lat && it.lon == city.lon && now - it.at in 0 until SHOWN_MS }

    // ---------- the saved copy ----------

    fun toJson(d: WeatherData): String = JSONObject().apply {
        put("lat", d.lat); put("lon", d.lon); put("tempC", d.tempC); put("code", d.code); put("isDay", d.isDay)
        d.feelsC?.let { put("feelsC", it) }; d.windMs?.let { put("windMs", it) }; put("at", d.at)
        put("hourly", JSONArray(d.hourly.map { JSONObject().put("day", it.day).put("hour", it.hour).put("tempC", it.tempC).put("code", it.code).put("isDay", it.isDay) }))
        put("daily", JSONArray(d.daily.map { o -> JSONObject().put("date", o.date).put("code", o.code).apply { o.highC?.let { put("highC", it) }; o.lowC?.let { put("lowC", it) } } }))
    }.toString()

    fun fromJson(s: String?): WeatherData? = try {
        val o = JSONObject(s!!)
        val h = o.getJSONArray("hourly"); val d = o.getJSONArray("daily")
        WeatherData(o.getDouble("lat"), o.getDouble("lon"), o.getDouble("tempC"), o.getInt("code"), o.optBoolean("isDay", true),
            o.optDouble("feelsC").takeIf { !it.isNaN() }, o.optDouble("windMs").takeIf { !it.isNaN() },
            (0 until h.length()).map { h.getJSONObject(it).let { x -> HourPoint(x.getInt("day"), x.getInt("hour"), x.getDouble("tempC"), x.getInt("code"), x.getBoolean("isDay")) } },
            (0 until d.length()).map { d.getJSONObject(it).let { x -> DayPoint(x.getString("date"), x.getInt("code"), x.optDouble("highC").takeIf { v -> !v.isNaN() }, x.optDouble("lowC").takeIf { v -> !v.isNaN() }) } },
            o.getLong("at"))
    } catch (_: Exception) { null }

    /** A few cities to pick without typing (the picker can also search any place by name). */
    val PRESET_CITIES get() = listOf(
        City(tr("Milan", "Милан"), 45.46427, 9.18951, tr("Lombardy, Italy", "Ломбардия, Италия")),
        City(tr("Baku", "Баку"), 40.4093, 49.8671, tr("Azerbaijan", "Азербайджан")),
        City(tr("Moscow", "Москва"), 55.7558, 37.6173, tr("Russia", "Россия")),
        City(tr("Saint Petersburg", "Санкт-Петербург"), 59.9343, 30.3351, tr("Russia", "Россия")),
        City(tr("Tbilisi", "Тбилиси"), 41.7151, 44.8271, tr("Georgia", "Грузия")),
        City(tr("Istanbul", "Стамбул"), 41.0082, 28.9784, tr("Turkey", "Турция")),
        City(tr("Dubai", "Дубай"), 25.2048, 55.2708, tr("UAE", "ОАЭ")),
        City(tr("Rome", "Рим"), 41.9028, 12.4964, tr("Italy", "Италия")),
        City(tr("Berlin", "Берлин"), 52.52, 13.405, tr("Germany", "Германия")),
        City(tr("Paris", "Париж"), 48.8566, 2.3522, tr("France", "Франция")),
        City(tr("London", "Лондон"), 51.5074, -0.1278, tr("United Kingdom", "Великобритания")),
        City(tr("Madrid", "Мадрид"), 40.4168, -3.7038, tr("Spain", "Испания")),
        City(tr("New York", "Нью-Йорк"), 40.7128, -74.006, tr("USA", "США")),
    )

    fun validCoords(lat: Double, lon: Double) = abs(lat) <= 90 && abs(lon) <= 180
}
