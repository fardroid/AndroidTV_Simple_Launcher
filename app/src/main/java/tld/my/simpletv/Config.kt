package tld.my.simpletv

// The user's choices, kept in the app's own preferences as one JSON text. The defaults are those of the webOS version
// (Simple LG launcher), so both start out looking the same.

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

enum class PlateMode {
    AUTO, LIGHT, DARK;
    val label get() = when (this) { AUTO -> str(R.string.auto); LIGHT -> str(R.string.plates_light); DARK -> str(R.string.dark) }
}

enum class TileShape {
    ICON, BANNER;
    val label get() = when (this) { ICON -> str(R.string.icons); BANNER -> str(R.string.banners) }
}

data class Config(
    /** the language of the screens: AUTO follows the box */
    val language: LangSetting = LangSetting.AUTO,
    /** packages in the user's order; apps not listed follow, A to Z */
    val appOrder: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val iconScale: Int = 110,
    val showLabels: Boolean = true,
    /** with names shown: only the selected app's name (the room for the names is kept, so nothing moves) */
    val labelsSelectedOnly: Boolean = false,
    val tileShape: TileShape = TileShape.ICON,
    val sideMargin: Int = 100,
    val bottomMargin: Int = 50,
    /** text size of the weather and calendar cards, % of the standard */
    val cardSize: Int = 100,
    val plates: PlateMode = PlateMode.AUTO,
    val glass: Boolean = true,
    /** how frosted the glass is: % of its full blur (Wallpapers.frost) */
    val frost: Int = 100,
    val shadows: Boolean = true,
    /** OLED care: the clock, the cards and the apps move a few pixels once a minute (Plates.shiftAt) */
    val pixelShift: Boolean = true,
    val clock24: Boolean = true,
    val clockSize: Int = 180,
    /** the font of all the widgets (ui/Fonts.kt), as in the webOS app */
    val clockFont: String = "inter",
    val clockWeight: Int = 400,
    val clockColor: String = "white",
    val showDate: Boolean = true,
    val dateSize: Int = 100,
    val weatherOn: Boolean = true,
    val city: City? = Weather.PRESET_CITIES[0],
    val fahrenheit: Boolean = false,
    val calendarOn: Boolean = true,
    /** iCal addresses; they stay on the box (never in the repository) */
    val calendars: List<String> = emptyList(),
    val newsOn: Boolean = false,
    /** Google News headlines of the chosen country */
    val newsGoogle: Boolean = true,
    /** a country code of Feeds.EDITIONS, "" until one is chosen (Feeds.countryOf) */
    val newsCountry: String = "",
    /** the user's own RSS or Atom feeds (at most Feeds.MAX_FEEDS) */
    val newsFeeds: List<String> = emptyList(),
    val quoteOn: Boolean = false,
    /** look on GitHub once a day for a newer version (it only says so; nothing is downloaded) */
    val updateCheck: Boolean = true,
    /** false: the plain dark gradient instead of pictures */
    val wallpaperOn: Boolean = true,
    val intervalSec: Int = 60,
    val shuffle: Boolean = false,
    /** the pictures ticked in Settings → Wallpaper → Pictures (Wallpapers' ids); none: all the built-in and the user's own */
    val wallpaperPicks: List<String> = emptyList(),
) {
    fun toJson(): String = JSONObject().apply {
        put("language", language.name)
        put("appOrder", JSONArray(appOrder)); put("hidden", JSONArray(hidden.toList()))
        put("iconScale", iconScale); put("showLabels", showLabels); put("labelsSelectedOnly", labelsSelectedOnly); put("tileShape", tileShape.name)
        put("sideMargin", sideMargin); put("bottomMargin", bottomMargin); put("cardSize", cardSize)
        put("plates", plates.name); put("glass", glass); put("frost", frost); put("shadows", shadows); put("pixelShift", pixelShift)
        put("clock24", clock24); put("clockSize", clockSize); put("clockFont", clockFont); put("clockWeight", clockWeight); put("clockColor", clockColor)
        put("showDate", showDate); put("dateSize", dateSize)
        put("weatherOn", weatherOn); put("fahrenheit", fahrenheit)
        city?.let { put("city", JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("detail", it.detail)) }
        put("calendarOn", calendarOn); put("calendars", JSONArray(calendars))
        put("newsOn", newsOn); put("newsGoogle", newsGoogle); put("newsCountry", newsCountry); put("newsFeeds", JSONArray(newsFeeds))
        put("quoteOn", quoteOn); put("updateCheck", updateCheck)
        put("wallpaperOn", wallpaperOn); put("intervalSec", intervalSec); put("shuffle", shuffle); put("wallpaperPicks", JSONArray(wallpaperPicks))
    }.toString()

    companion object {
        val INTERVALS = listOf(10, 30, 60, 300, 900, 1800, 3600)
        /** the ids of ui/Fonts.kt (kept here so the settings can be read without the UI) */
        val FONT_IDS = listOf("inter", "tv", "poppins", "oswald", "mono", "serif")

        fun fromJson(s: String?): Config {
            val d = Config()
            if (s == null) return d
            return try {
                val o = JSONObject(s)
                fun strings(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } }
                Config(
                    language = runCatching { LangSetting.valueOf(o.getString("language")) }.getOrDefault(d.language),
                    appOrder = strings("appOrder") ?: d.appOrder,
                    hidden = strings("hidden")?.toSet() ?: d.hidden,
                    iconScale = o.optInt("iconScale", d.iconScale).coerceIn(50, 140),
                    showLabels = o.optBoolean("showLabels", d.showLabels),
                    labelsSelectedOnly = o.optBoolean("labelsSelectedOnly", d.labelsSelectedOnly),
                    tileShape = runCatching { TileShape.valueOf(o.getString("tileShape")) }.getOrDefault(d.tileShape),
                    sideMargin = o.optInt("sideMargin", d.sideMargin).coerceIn(0, 300),
                    bottomMargin = o.optInt("bottomMargin", d.bottomMargin).coerceIn(0, 200),
                    cardSize = o.optInt("cardSize", d.cardSize).coerceIn(60, 160),
                    plates = runCatching { PlateMode.valueOf(o.getString("plates")) }.getOrDefault(d.plates),
                    glass = o.optBoolean("glass", d.glass),
                    frost = o.optInt("frost", d.frost).coerceIn(30, 100),
                    shadows = o.optBoolean("shadows", d.shadows),
                    pixelShift = o.optBoolean("pixelShift", d.pixelShift),
                    clock24 = o.optBoolean("clock24", d.clock24),
                    clockSize = o.optInt("clockSize", d.clockSize).coerceIn(80, 220),
                    clockFont = o.optString("clockFont", d.clockFont).let { f -> if (f in FONT_IDS) f else d.clockFont },
                    clockWeight = o.optInt("clockWeight", d.clockWeight).let { if (it in listOf(200, 300, 400, 500, 600)) it else d.clockWeight },
                    clockColor = o.optString("clockColor", d.clockColor).let { c -> if (ClockColors.ALL.any { it.id == c }) c else d.clockColor },
                    showDate = o.optBoolean("showDate", d.showDate),
                    dateSize = o.optInt("dateSize", d.dateSize).coerceIn(60, 160),
                    weatherOn = o.optBoolean("weatherOn", d.weatherOn),
                    city = if (o.has("city")) o.getJSONObject("city").let { c -> City(c.getString("name"), c.getDouble("lat"), c.getDouble("lon"), c.optString("detail")) } else null,
                    fahrenheit = o.optBoolean("fahrenheit", d.fahrenheit),
                    calendarOn = o.optBoolean("calendarOn", d.calendarOn),
                    calendars = strings("calendars")?.filter { Cal.validUrl(it) } ?: d.calendars,
                    newsGoogle = o.optBoolean("newsGoogle", d.newsGoogle),
                    newsCountry = o.optString("newsCountry", d.newsCountry).let { if (Feeds.EDITIONS.containsKey(it)) it else "" },
                    newsFeeds = (strings("newsFeeds")?.filter { Feeds.validUrl(it) } ?: d.newsFeeds).take(Feeds.MAX_FEEDS),
                    newsOn = o.optBoolean("newsOn", d.newsOn) && (o.optBoolean("newsGoogle", d.newsGoogle) || !strings("newsFeeds").isNullOrEmpty()),
                    quoteOn = o.optBoolean("quoteOn", d.quoteOn),
                    updateCheck = o.optBoolean("updateCheck", d.updateCheck),
                    wallpaperOn = o.optBoolean("wallpaperOn", d.wallpaperOn),
                    intervalSec = o.optInt("intervalSec", d.intervalSec).let { if (it in INTERVALS) it else d.intervalSec },
                    shuffle = o.optBoolean("shuffle", d.shuffle),
                    wallpaperPicks = strings("wallpaperPicks") ?: d.wallpaperPicks,
                )
            } catch (_: Exception) { d }
        }
    }
}

/** The app's own small store: the settings and the last weather and calendar readings. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun loadConfig(): Config = Config.fromJson(prefs.getString("config", null))
    fun saveConfig(c: Config) = prefs.edit().putString("config", c.toJson()).apply()

    fun loadWeather(): WeatherData? = Weather.fromJson(prefs.getString("weather", null))
    fun saveWeather(d: WeatherData) = prefs.edit().putString("weather", Weather.toJson(d)).apply()

    fun loadCalendar(): CalendarData? = Cal.fromJson(prefs.getString("calendar", null))
    fun saveCalendar(d: CalendarData) = prefs.edit().putString("calendar", Cal.toJson(d)).apply()

    fun loadNews(): NewsData? = Feeds.fromJson(prefs.getString("news", null))
    fun saveNews(d: NewsData) = prefs.edit().putString("news", Feeds.toJson(d)).apply()

    fun loadQuote(): Quote? = Quotes.fromJson(prefs.getString("quote", null))
    fun saveQuote(q: Quote) = prefs.edit().putString("quote", Quotes.toJson(q)).apply()

    /** the last look for a new version: when, and the newer release found (null: none) */
    var updateCheck: String?
        get() = prefs.getString("updateCheck", null)
        set(v) = prefs.edit().putString("updateCheck", v).apply()

    /** the list of Wikiquote authors, kept for a month (Quotes.authorsFromJson) */
    var authors: String?
        get() = prefs.getString("quoteAuthors", null)
        set(v) = prefs.edit().putString("quoteAuthors", v).apply()

    /** where the slideshow was, so it goes on from there after a restart */
    var wallpaperIndex: Int
        get() = prefs.getInt("wallpaperIndex", 0)
        set(v) = prefs.edit().putInt("wallpaperIndex", v).apply()
}

/** The colours the clock can have (Settings → Clock → Colour). A port of src/lib/clockcolor.ts. */
data class ClockColor(val id: String, @StringRes val name: Int, val colors: List<Long>, val dark: Boolean = false) {
    val label get() = str(name)
}

object ClockColors {
    val ALL = listOf(
        ClockColor("white", R.string.white, listOf(0xFFF2F2F2)),
        ClockColor("warm", R.string.warm_white, listOf(0xFFFFE9C7)),
        ClockColor("blue", R.string.ice_blue, listOf(0xFFC4E2FF)),
        ClockColor("amber", R.string.amber, listOf(0xFFFFB74D)),
        ClockColor("grey", R.string.grey, listOf(0xFF9AA0A6)),
        ClockColor("charcoal", R.string.dark_grey, listOf(0xFF3C4043), dark = true),
        ClockColor("black", R.string.black, listOf(0xFF0A0A0A), dark = true),
        ClockColor("ember", R.string.gradient_ember_red_to_gold, listOf(0xFFD62839, 0xFFF08C1A)),
        ClockColor("ocean", R.string.gradient_ocean_blue_to_teal, listOf(0xFF1E4FD8, 0xFF19C6D8), dark = true),
        ClockColor("dusk", R.string.gradient_dusk_violet_to_coral, listOf(0xFF6A2FC9, 0xFFF2634F), dark = true),
    )

    fun of(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
