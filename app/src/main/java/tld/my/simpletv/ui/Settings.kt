package tld.my.simpletv.ui

// Settings, in the look of the webOS app's (src/screens/Settings.tsx): the sections on the left, each with its own colour (iOS),
// the chosen one on the right in rounded groups of rows; the focused row is a light pill (Material 3 / Google TV) and its
// explanation sits at the foot of the screen. In the list of sections ↑ ↓ pick one and → or OK open it. In a section ↑ ↓ pick
// a row, OK switches it or steps a choice round, ← → step choices and sizes. Back goes back: a page to its section, a section
// to the list of sections, and the list out of Settings.

import tld.my.simpletv.R
import tld.my.simpletv.str
import tld.my.simpletv.plural
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import tld.my.simpletv.AppEntry
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tld.my.simpletv.Apps
import tld.my.simpletv.BuildConfigInfo
import tld.my.simpletv.Cal
import tld.my.simpletv.City
import tld.my.simpletv.ClockColors
import tld.my.simpletv.Config
import tld.my.simpletv.Feeds
import tld.my.simpletv.Gallery
import tld.my.simpletv.GalleryFolder
import tld.my.simpletv.LauncherState
import tld.my.simpletv.HomeRole
import tld.my.simpletv.Lang
import tld.my.simpletv.LangSetting
import tld.my.simpletv.Net
import tld.my.simpletv.PlateMode
import tld.my.simpletv.Screen
import tld.my.simpletv.TileShape
import tld.my.simpletv.UpdateStatus
import tld.my.simpletv.Wallpapers
import tld.my.simpletv.Weather

// ---------- the colours (the same as the webOS Settings, src/theme/global.css) ----------

private object Ui {
    val screen get() = Color(Themes.current.screen).copy(alpha = 0.86f)
    val nav = Color(0x09FFFFFF)
    val current = Color(0x17FFFFFF)
    val box = Color(0x13FFFFFF)
    val hairline = Color(0x17FFFFFF)
    val pill = Color(0xFFF2F2F7)
    val onPill = Color(0xFF111216)
    val onPillValue = Color(0xFF3C3F47)
    val muted = Color(0xFF8A8D96)
    val green = Color(0xFF30D158)
    val switchOff = Color(0x38FFFFFF)
    val switchOffOnPill = Color(0x2E111216)
    val dangerOnPill = Color(0xFFC5221F)
}

// ---------- rows ----------

private sealed interface Item {
    val label: String
    /** shown at the foot of the screen while the row is focused */
    val description: String
    /** the heading of its group; rows of one group follow each other */
    val group: String
    /** a glyph drawn before the label on the pages (the city, the calendars, the feeds) */
    val icon: Int? get() = null
}

private data class Toggle(override val label: String, override val description: String, override val group: String, val on: Boolean, val set: (Boolean) -> Unit) : Item
/** a choice among values: ← → step and stop at the ends, OK steps on and wraps round */
private data class Step(override val label: String, override val description: String, override val group: String, val texts: List<String>, val index: Int, val pick: (Int) -> Unit) : Item
private data class Action(override val label: String, override val description: String, override val group: String, val value: String = "", val opens: Boolean = false, val danger: Boolean = false, override val icon: Int? = null, val run: () -> Unit) : Item
/** a fact to read, nothing to change */
private data class Info(override val label: String, override val description: String, override val group: String, val value: String = "", override val icon: Int? = null) : Item
private data class Input(override val label: String, override val description: String, override val group: String, val keyboard: KeyboardType, override val icon: Int? = null, val submit: (String) -> Unit) : Item
/** one of a list to pick (a city): its detail on the right, a tick on the current one */
private data class Pick(override val label: String, val detail: String, override val group: String, val checked: Boolean, override val icon: Int? = null, val pick: () -> Unit) : Item {
    override val description get() = ""
}

private fun <T> choice(label: String, description: String, group: String, options: List<Pair<T, String>>, current: T, set: (T) -> Unit): Step =
    Step(label, description, group, options.map { it.second }, options.indexOfFirst { it.first == current }.coerceAtLeast(0)) { i -> set(options[i].first) }

private fun range(label: String, description: String, group: String, value: Int, min: Int, max: Int, step: Int, unit: String, set: (Int) -> Unit): Step {
    val values = (min..max step step).toList()
    val near = values.minByOrNull { kotlin.math.abs(it - value) } ?: min
    return choice(label, description, group, values.map { it to "$it$unit" }, near, set)
}

// ---------- sections and pages ----------

/** The sections, in the webOS app's order, with the icons of its Settings (24 x 24 line paths) on their colours. */
private enum class Section(@StringRes private val titleRes: Int, val color: Long, val icon: String) {
    APPS(R.string.apps, 0xFF0A84FF, "M4.5 4.5h5v5h-5z M14.5 4.5h5v5h-5z M4.5 14.5h5v5h-5z M14.5 14.5h5v5h-5z"),
    LOOK(R.string.appearance, 0xFF5E5CE6, "M12 3.5a8.5 8.5 0 1 0 0 17c1 0 1.6-.7 1.6-1.5s-.6-1.3-.6-2.1c0-.9.7-1.6 1.6-1.6h2a3.9 3.9 0 0 0 3.9-3.9c0-4.4-3.8-7.9-8.5-7.9z M7.8 11.5h.01 M10.2 7.6h.01 M14.6 7.8h.01"),
    WIDGETS(R.string.widgets, 0xFFFF9F0A, "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z M12 7.5v4.8l3.2 2"),
    WALLPAPER(R.string.wallpaper, 0xFFBF5AF2, "M4 5h16v14H4z M4 16l4.5-4.5 4 4 3-3 4.5 4.5 M15.5 9h.01"),
    HOME(R.string.home_screen, 0xFF30D158, "M4.5 11l7.5-6.5 7.5 6.5v8.5h-15z M10 19.5v-5h4v5");
    val title get() = str(titleRes)
}

/** A page opened from a row of a section (it takes the right-hand side until Back). */
private sealed class Page(@StringRes private val name: Int = 0) {
    open val title get() = str(name)
    data object CityPage : Page(R.string.choose_a_city)
    data object Calendars : Page(R.string.calendars)
    data object NewsCountry : Page(R.string.country_of_the_news)
    data object NewsFeeds : Page(R.string.your_feeds)
    data object Pictures : Page(R.string.picture)
    data class CalendarOne(val index: Int, val calendar: String) : Page() {
        override val title get() = calendar
    }
}

private fun interval(sec: Int) = when {
    sec < 60 -> plural(R.plurals.seconds, sec)
    sec < 3600 -> plural(R.plurals.minutes, sec / 60)
    else -> plural(R.plurals.hours, sec / 3600)
}

private fun weightText(w: Int) = when (w) {
    200 -> str(R.string.thin); 300 -> str(R.string.weight_light); 500 -> str(R.string.medium); 600 -> str(R.string.bold); else -> str(R.string.regular)
}

/** The calendar's name (from the feed, once read) or its site; the secret part of the address is never shown. */
private fun calendarName(state: LauncherState, url: String, i: Int): String =
    state.calendar?.sources?.firstOrNull { it.src == Cal.normalizeUrl(url) }?.name?.takeIf { it.isNotEmpty() } ?: str(R.string.calendar_n, i + 1)

private fun calendarSite(url: String): String = runCatching { java.net.URI(Cal.normalizeUrl(url)).host }.getOrNull() ?: str(R.string.ical_link)

/** A feed's site, and its path without the query (a feed address may carry a key): shown instead of the whole address. */
private fun feedName(url: String): String = runCatching { java.net.URI(Feeds.normalizeUrl(url)).host?.removePrefix("www.") }.getOrNull() ?: url.take(40)
private fun feedPath(url: String): String = runCatching { java.net.URI(Feeds.normalizeUrl(url)).path.orEmpty().take(40) }.getOrDefault("")

/** The look, the margins and the widgets back to how they were at first (apps, wallpaper, weather and calendars stay). */
private fun resetLook(c: Config): Config {
    val d = Config()
    return c.copy(
        iconScale = d.iconScale, showLabels = d.showLabels, labelsSelectedOnly = d.labelsSelectedOnly, tileShape = d.tileShape, sideMargin = d.sideMargin, bottomMargin = d.bottomMargin,
        cardSize = d.cardSize, plates = d.plates, glass = d.glass, frost = d.frost, shadows = d.shadows,
        clock24 = d.clock24, clockSize = d.clockSize, clockFont = d.clockFont, clockWeight = d.clockWeight, clockColor = d.clockColor,
        showDate = d.showDate, dateSize = d.dateSize,
    )
}

private enum class SetZone { NAV, CONTENT }

@Composable
fun SettingsPanel(state: LauncherState, keys: KeyTarget) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(Section.APPS) }
    var zone by remember { mutableStateOf(SetZone.NAV) }
    val pages = remember { mutableStateListOf<Page>() }
    val selectedAt = remember { HashMap<String, Int>() }
    var selected by remember { mutableStateOf(0) }
    /** the focused tile of the Apps grid; -1: the button above it */
    var appAt by remember { mutableStateOf(0) }
    var cityResults by remember { mutableStateOf<List<City>?>(null) }
    var cityStatus by remember { mutableStateOf("") }
    var calendarStatus by remember { mutableStateOf("") }
    var feedStatus by remember { mutableStateOf("") }
    // the gallery: the folders, the open one (null: the list of folders), the focused tile, and the ticked pictures (kept on leaving)
    var folders by remember { mutableStateOf<List<GalleryFolder>>(emptyList()) }
    var folderIn by remember { mutableStateOf<Int?>(null) }
    var galleryAt by remember { mutableStateOf(0) }
    val picks = remember { mutableStateListOf<String>() }
    val c = state.config
    val gridApps = remember(state.apps, c.appOrder) {
        c.appOrder.mapNotNull { pkg -> state.apps.firstOrNull { it.pkg == pkg } } + state.apps.filter { it.pkg !in c.appOrder }.sortedBy { it.label.lowercase() }
    }
    fun set(change: (Config) -> Config) = state.update(change)
    val page = pages.lastOrNull()
    val onGrid = page == null && section == Section.APPS
    val onGallery = page == Page.Pictures
    val where = page?.title ?: section.name

    LaunchedEffect(onGallery, state.photosVersion) {
        if (onGallery) folders = withContext(Dispatchers.IO) { Gallery.folders(context) }
    }
    val canReadDevice = remember(onGallery, state.photosVersion) { Gallery.canReadDevice(context) }
    /** the tiles on screen: the folders (and one asking for the box's pictures), or the open folder's pictures */
    fun galleryCount() = folderIn?.let { folders.getOrNull(it)?.pictures?.size ?: 0 } ?: (folders.size + if (canReadDevice) 0 else 1)

    fun keep(at: String) { selectedAt[at] = selected }
    fun open(p: Page) { keep(where); pages.add(p); selected = 0 }
    fun back() {
        when {
            pages.isNotEmpty() -> { pages.removeAt(pages.lastIndex); selected = selectedAt[pages.lastOrNull()?.title ?: section.name] ?: 0 }
            zone == SetZone.CONTENT -> { keep(where); zone = SetZone.NAV }
            else -> state.screen = Screen.HOME
        }
    }

    fun openGallery() { picks.clear(); picks.addAll(c.wallpaperPicks); folderIn = null; galleryAt = 0; open(Page.Pictures) }
    fun leaveGallery() { set { it.copy(wallpaperPicks = picks.toList()) }; back() }

    val items: List<Item> = when (page) {
        Page.CityPage -> buildList {
            add(Input(str(R.string.search_for_a_city), str(R.string.type_a_name_and_press_ok), "", KeyboardType.Text, R.drawable.g_search) { q ->
                cityStatus = str(R.string.searching)
                scope.launch {
                    val found = withContext(Dispatchers.IO) { runCatching { Net.searchCities(q) }.getOrNull() }
                    cityResults = found
                    cityStatus = when { found == null -> str(R.string.the_weather_service_cannot_be); found.isEmpty() -> str(R.string.nothing_found); else -> "" }
                }
            })
            if (cityStatus.isNotEmpty()) add(Info(cityStatus, "", ""))
            cityResults?.takeIf { it.isNotEmpty() }?.forEach { city ->
                add(Pick(city.name, city.detail, str(R.string.search_results), c.city == city) { set { it.copy(city = city) }; back() })
            }
            Weather.PRESET_CITIES.forEach { city ->
                add(Pick(city.name, city.detail, str(R.string.popular_cities), c.city?.name == city.name && c.city?.lat == city.lat, R.drawable.g_pin) { set { it.copy(city = city) }; back() })
            }
        }
        Page.Calendars -> buildList {
            add(Input(str(R.string.add_a_link), str(R.string.the_secret_address_in_ical_format), "", KeyboardType.Uri, R.drawable.g_search) { raw ->
                val url = Cal.normalizeUrl(raw)
                calendarStatus = when {
                    !Cal.validUrl(url) -> str(R.string.that_does_not_look_like_a)
                    url in c.calendars -> str(R.string.this_calendar_is_already_on_the)
                    else -> { set { it.copy(calendars = it.calendars + url) }; str(R.string.calendar_added) }
                }
            })
            if (calendarStatus.isNotEmpty()) add(Info(calendarStatus, "", "", "", R.drawable.g_info))
            val connected = str(R.string.connected, c.calendars.size)
            c.calendars.forEachIndexed { i, url ->
                val name = calendarName(state, url, i)
                add(Action(name, str(R.string.ok_to_remove_it), connected, calendarSite(url), opens = true, icon = R.drawable.g_picture) { open(Page.CalendarOne(i, name)) })
            }
            add(Action(
                str(R.string.refresh_now), str(R.string.read_the_calendars_again), "",
                if (state.calendarLoading) str(R.string.loading) else state.calendar?.let { str(R.string.read_at, Cal.localTime(it.at).toLocalTime().toString().take(5)) } ?: str(R.string.not_read_yet),
            ) { state.refreshCalendarNow() })
        }
        Page.Pictures -> emptyList()
        Page.NewsCountry -> buildList {
            val shown = Feeds.countryOf(c.newsCountry, state.boxTag(), Lang.current)
            val group = str(R.string.google_news_editions, Feeds.COUNTRIES.size)
            Feeds.COUNTRIES.map { it to Feeds.countryName(it, Lang.current) }.sortedWith(compareBy(java.text.Collator.getInstance(Lang.current.locale)) { it.second }).forEach { (code, name) ->
                val edition = Feeds.editionFor(code, Lang.current)
                val language = runCatching { java.util.Locale.forLanguageTag(edition.lang).getDisplayLanguage(Lang.current.locale) }.getOrDefault("").ifEmpty { edition.hl }
                add(Pick(name, language.replaceFirstChar { it.uppercase(Lang.current.locale) }, group, code == shown, R.drawable.g_pin) { set { it.copy(newsCountry = code) }; back() })
            }
        }
        Page.NewsFeeds -> buildList {
            add(Input(str(R.string.add_a_feed), str(R.string.the_address_of_an_rss_or_atom), "", KeyboardType.Uri, R.drawable.g_search) { raw ->
                val url = Feeds.normalizeUrl(raw)
                feedStatus = when {
                    !Feeds.validUrl(url) -> str(R.string.that_does_not_look_like_a_link_it)
                    url in c.newsFeeds -> str(R.string.this_feed_is_already_on_the_list)
                    c.newsFeeds.size >= Feeds.MAX_FEEDS -> str(R.string.no_more_than_n_feeds, Feeds.MAX_FEEDS)
                    else -> { set { it.copy(newsFeeds = it.newsFeeds + url) }; str(R.string.added_n, c.newsFeeds.size + 1) }
                }
            })
            if (feedStatus.isNotEmpty()) add(Info(feedStatus, "", "", "", R.drawable.g_info))
            if (c.newsFeeds.isNotEmpty()) add(Action(str(R.string.remove_all_feeds), str(R.string.only_google_news_stays_if_it_is_on), "", icon = R.drawable.g_folder) {
                set { it.copy(newsFeeds = emptyList(), newsOn = it.newsOn && it.newsGoogle) }
            })
            val group = if (c.newsFeeds.isEmpty()) str(R.string.no_feeds_of_your_own_yet) else str(R.string.added_n, c.newsFeeds.size)
            c.newsFeeds.forEach { url ->
                add(Action(feedName(url), str(R.string.ok_removes_it), group, feedPath(url), icon = R.drawable.g_picture) {
                    set { it.copy(newsFeeds = it.newsFeeds - url, newsOn = it.newsOn && (it.newsGoogle || it.newsFeeds.size > 1)) }
                })
            }
        }
        is Page.CalendarOne -> listOf(
            Action(str(R.string.remove_the_calendar), str(R.string.it_leaves_the_home_screen), "", danger = true) {
                set { it.copy(calendars = it.calendars.filterIndexed { i, _ -> i != page.index }) }
                back()
            },
            Action(str(R.string.keep_it), "", "") { back() },
        )
        null -> when (section) {
            // the apps are a grid of tiles (AppsGrid), not rows
            Section.APPS -> emptyList()
            Section.LOOK -> buildList {
                add(choice(str(R.string.colour_theme), str(R.string.colour_theme_description), "", Themes.ALL.map { it.id to str(it.name) }, c.theme) { v -> set { it.copy(theme = v) } })
                val row = str(R.string.app_row)
                add(choice(str(R.string.tiles), str(R.string.square_icons_or_the_wide_banners), row, TileShape.entries.map { it to it.label }, c.tileShape) { v -> set { it.copy(tileShape = v) } })
                add(range(str(R.string.icon_size), str(R.string.how_big_the_app_tiles_are), row, c.iconScale, 50, 140, 10, "%") { v -> set { it.copy(iconScale = v) } })
                val labels = if (!c.showLabels) 2 else if (c.labelsSelectedOnly) 1 else 0
                add(choice(str(R.string.app_names), str(R.string.a_name_under_each_tile_only), row,
                    listOf(0 to str(R.string.all), 1 to str(R.string.selected_only), 2 to str(R.string.labels_none)), labels) { v -> set { it.copy(showLabels = v != 2, labelsSelectedOnly = v == 1) } })
                add(range(str(R.string.side_margin), str(R.string.space_left_and_right_of_the), row, c.sideMargin, 0, 300, 10, " px") { v -> set { it.copy(sideMargin = v) } })
                add(range(str(R.string.bottom_margin), str(R.string.space_below_the_row), row, c.bottomMargin, 0, 200, 10, " px") { v -> set { it.copy(bottomMargin = v) } })
                val cards = str(R.string.widgets)
                add(choice(str(R.string.font), str(R.string.for_the_clock_the_date_the), cards, Fonts.ALL.map { it.id to it.label }, c.clockFont) { v -> set { it.copy(clockFont = v) } })
                add(range(str(R.string.text_size), str(R.string.all_the_text_on_the_weather), cards, c.cardSize, 60, 160, 5, "%") { v -> set { it.copy(cardSize = v) } })
                add(choice(str(R.string.card_colour), str(R.string.auto_all_the_cards_turn_light), cards, PlateMode.entries.map { it to it.label }, c.plates) { v -> set { it.copy(plates = v) } })
                if (c.wallpaperOn) add(Toggle(str(R.string.frosted_glass), str(R.string.the_cards_blur_the_picture_behind), cards, c.glass) { v -> set { it.copy(glass = v) } })
                if (c.wallpaperOn && c.glass) add(range(str(R.string.frost), str(R.string.how_much_the_glass_blurs_the), cards, c.frost, 30, 100, 10, "%") { v -> set { it.copy(frost = v) } })
                add(Toggle(str(R.string.shadows), str(R.string.a_soft_shadow_under_the_clock), cards, c.shadows) { v -> set { it.copy(shadows = v) } })
                add(Toggle(str(R.string.pixel_shift_oled), str(R.string.the_clock_the_cards_and_the), cards, c.pixelShift) { v -> set { it.copy(pixelShift = v) } })
            }
            Section.WIDGETS -> buildList {
                val clock = str(R.string.clock)
                add(choice(str(R.string.format), str(R.string.n12_hour_or_24_hour), clock, listOf(true to str(R.string.n24_hour), false to str(R.string.n12_hour_am_pm)), c.clock24) { v -> set { it.copy(clock24 = v) } })
                add(range(str(R.string.size), str(R.string.the_height_of_the_digits), clock, c.clockSize, 80, 220, 10, " px") { v -> set { it.copy(clockSize = v) } })
                add(choice(str(R.string.weight), str(R.string.from_thin_to_bold_digits), clock, Fonts.WEIGHTS.map { it to weightText(it) }, c.clockWeight) { v -> set { it.copy(clockWeight = v) } })
                add(choice(str(R.string.colour), str(R.string.white_is_hard_to_read_on), clock, ClockColors.ALL.map { it.id to it.label }, c.clockColor) { v -> set { it.copy(clockColor = v) } })
                val date = str(R.string.date)
                add(Toggle(str(R.string.show_the_date), str(R.string.the_weekday_and_the_date_beside), date, c.showDate) { v -> set { it.copy(showDate = v) } })
                if (c.showDate) add(range(str(R.string.date_size), str(R.string.the_size_of_the_weekday_and), date, c.dateSize, 60, 160, 5, "%") { v -> set { it.copy(dateSize = v) } })
                val weather = str(R.string.weather)
                add(Toggle(str(R.string.show_the_weather), str(R.string.the_temperature_and_the_forecast), weather, c.weatherOn) { v -> set { it.copy(weatherOn = v) } })
                add(Action(str(R.string.city), str(R.string.pick_a_city_from_the_list), weather, c.city?.name ?: str(R.string.not_chosen), opens = true) { open(Page.CityPage) })
                add(choice(str(R.string.temperature_unit), str(R.string.degrees_celsius_or_fahrenheit), weather, listOf(false to str(R.string.celsius_c), true to str(R.string.fahrenheit_f)), c.fahrenheit) { v -> set { it.copy(fahrenheit = v) } })
                val calendar = str(R.string.calendar)
                add(Toggle(str(R.string.show_the_calendar), str(R.string.the_day_s_events_in_the), calendar, c.calendarOn) { v -> set { it.copy(calendarOn = v) } })
                add(Action(str(R.string.calendars), str(R.string.ical_links_from_google_calendar), calendar,
                    if (c.calendars.isEmpty()) str(R.string.no_calendars) else plural(R.plurals.calendars_2, c.calendars.size), opens = true) { open(Page.Calendars) })
                val news = str(R.string.news)
                add(Toggle(str(R.string.show_the_news), str(R.string.show_the_news_description), news, c.newsOn) { v -> set { it.copy(newsOn = v && (it.newsGoogle || it.newsFeeds.isNotEmpty())) } })
                add(Toggle(str(R.string.google_news), str(R.string.the_top_stories_of_the_chosen_country), news, c.newsGoogle) { v -> set { it.copy(newsGoogle = v, newsOn = it.newsOn && (v || it.newsFeeds.isNotEmpty())) } })
                if (c.newsGoogle) add(Action(str(R.string.country), str(R.string.whose_top_stories_google_news_shows), news,
                    Feeds.countryName(Feeds.countryOf(c.newsCountry, state.boxTag(), Lang.current), Lang.current), opens = true) { open(Page.NewsCountry) })
                add(Action(str(R.string.your_feeds), str(R.string.addresses_of_rss_or_atom_feeds_of), news,
                    if (c.newsFeeds.isEmpty()) str(R.string.none_feeds) else plural(R.plurals.feeds_n, c.newsFeeds.size), opens = true) { open(Page.NewsFeeds) })
                val quote = str(R.string.quote_of_the_day)
                add(Toggle(str(R.string.show_the_quote_of_the_day), str(R.string.a_quote_from_wikiquote_in_the_launcher), quote, c.quoteOn) { v -> set { it.copy(quoteOn = v) } })
            }
            Section.WALLPAPER -> buildList {
                add(Toggle(str(R.string.pictures_in_the_background), str(R.string.off_a_calm_dark_gradient_instead), "", c.wallpaperOn) { v -> set { it.copy(wallpaperOn = v) } })
                if (c.wallpaperOn) add(Action(str(R.string.picture), str(R.string.gallery_description), "", if (c.wallpaperPicks.isEmpty()) str(R.string.all) else plural(R.plurals.pictures_n, c.wallpaperPicks.size), opens = true) { openGallery() })
                if (c.wallpaperOn) {
                    val show = str(R.string.slideshow)
                    add(choice(str(R.string.change_every), str(R.string.how_long_each_picture_stays_on), show, Config.INTERVALS.map { it to interval(it) }, c.intervalSec) { v -> set { it.copy(intervalSec = v) } })
                    add(Toggle(str(R.string.shuffle), str(R.string.random_order_off_in_turn), show, c.shuffle) { v -> set { it.copy(shuffle = v) } })
                }
                val folder = (Wallpapers.folder(context)?.absolutePath ?: "/sdcard/Android/data/tld.my.simpletv/files/wallpapers") + "/"
                add(Info(str(R.string.picture_folder), str(R.string.add_your_own_pictures_from_a, folder), str(R.string.your_own_pictures), "adb push"))
            }
            Section.HOME -> buildList {
                add(choice(if (Lang.current == tld.my.simpletv.Language.EN) "Language" else str(R.string.language) + " · Language", str(R.string.the_language_of_the_launcher_same), "", LangSetting.entries.map { it to Lang.name(it) }, c.language) { v -> set { it.copy(language = v) } })
                add(Action(str(R.string.reset_look_and_clock), str(R.string.sizes_margins_cards_and_the), "") { set(::resetLook) })
                // read again whenever the launcher comes back (from the box's settings, say)
                @Suppress("UNUSED_VARIABLE") val again = state.resumes
                val isHome = HomeRole.isDefault(context)
                val homeKey = HomeRole.homeKeyOpensLauncher(context)
                add(Action(str(R.string.launcher_by_default),
                    when {
                        homeKey -> str(R.string.the_home_button_opens_this)
                        !isHome -> str(R.string.ok_opens_the_box_s_choice)
                        else -> str(R.string.google_tv_sends_the_home_button)
                    },
                    str(R.string.opening_the_launcher), if (homeKey) str(R.string.yes) else str(R.string.no), opens = true) {
                    if (!isHome) HomeRole.openHomeSettings(context) else if (!homeKey) HomeRole.openAccessibility(context) else HomeRole.openHomeSettings(context)
                })
                val about = str(R.string.about)
                add(Info("Simple TV launcher", str(R.string.the_android_tv_version_of_simple), about, BuildConfigInfo.version(context)))
                add(Toggle(str(R.string.check_for_new_versions), str(R.string.check_for_new_versions_description), about, c.updateCheck) { v -> set { it.copy(updateCheck = v) } })
                if (c.updateCheck) {
                    val found = state.update
                    add(Action(str(R.string.check_for_a_new_version), found?.url.orEmpty(), about, when (state.updateStatus) {
                        UpdateStatus.LOOKING -> str(R.string.looking_for_a_new_version)
                        UpdateStatus.LATEST -> str(R.string.this_is_the_latest_version)
                        UpdateStatus.FAILED -> str(R.string.could_not_look_for_a_new_version)
                        else -> found?.let { str(R.string.version_n_is_out, it.version) }.orEmpty()
                    }) { state.checkUpdateNow() })
                }
                add(Action(str(R.string.tv_settings), str(R.string.network_sound_accounts_apps_the), "", opens = true) { Apps.openTvSettings(context) })
            }
        }
    }

    if (items.isNotEmpty() && selected !in items.indices) selected = items.lastIndex

    // the rows whose change shows on the home screen: Settings steps aside for a moment (as in the webOS app)
    val peekLabels = remember(Lang.current) { setOf(str(R.string.format), str(R.string.size), str(R.string.weight), str(R.string.colour), str(R.string.show_the_date), str(R.string.date_size), str(R.string.temperature_unit)) }
    val peekAnim = remember { Animatable(0f) }
    var peekBar by remember { mutableStateOf<Pair<String, String>?>(null) }
    var peekStamp by remember { mutableStateOf(0) }
    fun showsOnHome(item: Item?) = page == null && item != null && (item is Step || item is Toggle) &&
        (section == Section.LOOK && item.label != str(R.string.pixel_shift_oled) || section == Section.WIDGETS && item.label in peekLabels)
    fun peekAt(item: Item, value: String) { if (showsOnHome(item)) { peekBar = item.label to value; peekStamp++ } }
    fun endPeek() { peekBar = null }
    LaunchedEffect(peekStamp, peekBar == null) {
        if (peekBar == null) { peekAnim.animateTo(0f, tween(260, easing = Ease.out)) }
        else { peekAnim.animateTo(1f, tween(260, easing = Ease.out)); kotlinx.coroutines.delay(2200); peekBar = null }
    }
    LaunchedEffect(Unit) { androidx.compose.runtime.snapshotFlow { peekAnim.value }.collect { state.peek = it } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.peek = 0f } }

    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }

    SideEffect {
        keys.handler = handler@{ key, _ ->
            val item = items.getOrNull(selected)
            // while the home screen shows a change: ← → go on changing it; ↑ ↓ and Back bring Settings back first
            if (peekBar != null && (key == Nav.UP || key == Nav.DOWN || key == Nav.BACK)) { endPeek(); return@handler }
            fun stopEditing() { if (editing) { editing = false; keys.refocus() } }
            if (zone == SetZone.NAV) {
                val all = Section.entries
                when (key) {
                    Nav.UP -> if (section.ordinal > 0) section = all[section.ordinal - 1]
                    Nav.DOWN -> if (section.ordinal < all.lastIndex) section = all[section.ordinal + 1]
                    Nav.RIGHT, Nav.OK -> { zone = SetZone.CONTENT; selected = selectedAt[section.name] ?: 0 }
                    Nav.BACK -> back()
                    else -> {}
                }
            } else if (onGallery) {
                val count = galleryCount()
                val inside = folderIn
                val folder = inside?.let { folders.getOrNull(it) }
                fun up() { if (inside != null) { folderIn = null; galleryAt = inside } else leaveGallery() }
                when (key) {
                    Nav.UP -> galleryAt = if (galleryAt < GALLERY_COLS) -1 else galleryAt - GALLERY_COLS
                    Nav.DOWN -> if (galleryAt == -1) { if (count > 0) galleryAt = 0 } else if (galleryAt / GALLERY_COLS < (count - 1) / GALLERY_COLS) galleryAt = minOf(galleryAt + GALLERY_COLS, count - 1)
                    Nav.LEFT -> if (galleryAt <= 0 || galleryAt % GALLERY_COLS == 0) up() else galleryAt--
                    Nav.RIGHT -> if (galleryAt in 0 until count - 1) galleryAt++
                    Nav.OK -> when {
                        galleryAt == -1 -> picks.clear()
                        folder != null -> folder.pictures.getOrNull(galleryAt)?.let { id -> if (id in picks) picks.remove(id) else picks.add(id) }
                        galleryAt < folders.size -> { folderIn = galleryAt; galleryAt = 0 }
                        else -> state.requestPhotos()
                    }
                    Nav.BACK -> up()
                    else -> {}
                }
            } else if (onGrid) {
                val last = gridApps.lastIndex
                when (key) {
                    Nav.UP -> appAt = if (appAt < GRID_COLS) -1 else appAt - GRID_COLS
                    Nav.DOWN -> if (appAt == -1) { if (last >= 0) appAt = 0 } else if (appAt / GRID_COLS < last / GRID_COLS) appAt = minOf(appAt + GRID_COLS, last)
                    Nav.LEFT -> if (appAt <= 0 || appAt % GRID_COLS == 0) back() else appAt--
                    Nav.RIGHT -> if (appAt in 0 until last) appAt++
                    Nav.OK -> if (appAt == -1) set { it.copy(appOrder = emptyList()) } else gridApps.getOrNull(appAt)?.let { app ->
                        set { it.copy(hidden = if (app.pkg in it.hidden) it.hidden - app.pkg else it.hidden + app.pkg) }
                    }
                    Nav.BACK -> back()
                    else -> {}
                }
            } else when (key) {
                Nav.UP -> { stopEditing(); if (selected > 0) selected-- }
                Nav.DOWN -> { stopEditing(); if (selected < items.lastIndex) selected++ }
                Nav.LEFT, Nav.RIGHT -> if (item is Step) {
                    val i = (item.index + if (key == Nav.LEFT) -1 else 1).coerceIn(0, item.texts.lastIndex)
                    if (i != item.index) { item.pick(i); peekAt(item, item.texts[i]) }
                } else if (key == Nav.LEFT && !editing) back()
                Nav.OK -> when (item) {
                    is Toggle -> { item.set(!item.on); peekAt(item, str(if (item.on) R.string.switch_off else R.string.switch_on)) }
                    is Step -> { val i = (item.index + 1) % item.texts.size; item.pick(i); peekAt(item, item.texts[i]) }
                    is Action -> item.run()
                    is Pick -> item.pick()
                    is Input -> { editing = true; runCatching { inputFocus.requestFocus() }; keyboard?.show() }
                    else -> {}
                }
                Nav.BACK -> if (editing) stopEditing() else back()
                Nav.HOLD -> {}
            }
        }
    }

    // Settings fades in over the home screen
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220, easing = Ease.out)) }

    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value * (1f - peekAnim.value) }.background(Ui.screen)) {
        // ----- the sections -----
        Column(Modifier.width(600.dp).fillMaxHeight().background(Ui.nav).padding(start = 96.dp, end = 36.dp, top = 84.dp, bottom = 60.dp)) {
            BasicText(str(R.string.settings), Modifier.padding(start = 20.dp, bottom = 34.dp), style = text(46f, 600))
            Section.entries.forEach { s -> NavItem(s, current = s == section, focused = zone == SetZone.NAV && s == section) }
        }
        // ----- the chosen section, or a page opened from it -----
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 64.dp, end = 120.dp, top = 84.dp)) {
            BasicText(page?.title ?: section.title, style = text(46f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val hint = when {
                zone == SetZone.NAV -> str(R.string.choose_open_back_closes_settings)
                page != null -> str(R.string.ok_chooses_back_returns)
                else -> str(R.string.ok_changes_adjust_back_to_the)
            }
            BasicText(if (onGallery) str(if (folderIn == null) R.string.choose_where_the_pictures_are_back_returns else R.string.ok_ticks_pictures_for_a_slideshow_or_opens) else if (onGrid && zone == SetZone.CONTENT) str(R.string.ok_shows_or_hides_an_app) else hint, Modifier.padding(top = 10.dp, bottom = 30.dp), style = text(21f, 400, Ui.muted))
            if (onGallery) GalleryGrid(folders, folderIn, if (zone == SetZone.CONTENT) galleryAt else -2, picks, canReadDevice, Modifier.weight(1f))
            else if (onGrid) AppsGrid(gridApps, c.hidden, if (zone == SetZone.CONTENT) appAt else -2, Modifier.weight(1f))
            else OptionList(items, Modifier.weight(1f), where, if (zone == SetZone.CONTENT) selected else -1, editing, inputFocus) { editing = false; keys.refocus() }
            val footer = when {
                zone != SetZone.CONTENT -> ""
                onGallery -> if (galleryAt == -1) str(R.string.nothing_ticked_all_the_pictures_show) else galleryFooter(folders, folderIn, galleryAt, picks)
                onGrid -> if (appAt == -1) str(R.string.back_to_a_to_z_hold)
                    else gridApps.getOrNull(appAt)?.let { app -> app.label + if (app.pkg in c.hidden) str(R.string.hidden_from_the_home_screen) else str(R.string.on_the_home_screen) }.orEmpty()
                else -> items.getOrNull(selected)?.description.orEmpty()
            }
            BasicText(footer, Modifier.padding(start = 24.dp, top = 16.dp, bottom = 56.dp).heightIn(min = 64.dp), style = text(22f, 400, Hue.textSecondary, 1.45f), maxLines = 3)
        }
    }
    // the setting's name and its value in a bar at the top, over the home screen
    peekBar?.let { (label, value) -> PeekBar(label, value, peekAnim.value) }
    }
}

/** The name and the new value of a setting that is showing on the home screen. */
@Composable
private fun PeekBar(label: String, value: String, p: Float) {
    Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer { alpha = p; translationY = -(1f - p) * 20f * density }.height(76.dp)
                .background(Ui.pill, RoundedCornerShape(38.dp)).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(label, style = text(27f, 500, Ui.onPill), maxLines = 1)
            Spacer(Modifier.width(36.dp))
            BasicText(value, style = text(27f, 400, Ui.onPillValue), maxLines = 1)
        }
    }
}

// ---------- the list of sections ----------

private val pathCache = HashMap<String, Path>()

@Composable
private fun NavItem(s: Section, current: Boolean, focused: Boolean) {
    val bg by animateColorAsState(if (focused) Ui.pill else if (current) Ui.current else Color.Transparent, tween(150), label = "nav")
    val grow by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "navScale")
    Row(
        Modifier.padding(bottom = 6.dp).fillMaxWidth().height(70.dp).graphicsLayer { scaleX = grow; scaleY = grow }
            .background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Color(s.color), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            val path = pathCache.getOrPut(s.icon) { PathParser().parsePathString(s.icon).toPath() }
            Canvas(Modifier.size(28.dp)) {
                val k = size.width / 24f
                scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    drawPath(path, Color.White, style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Spacer(Modifier.width(20.dp))
        BasicText(s.title, style = text(27f, 400, if (focused) Ui.onPill else if (current) Hue.textPrimary else Hue.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- the rows, in their groups ----------

@Composable
private fun OptionList(items: List<Item>, modifier: Modifier, where: String, selected: Int, editing: Boolean, inputFocus: FocusRequester, doneEditing: () -> Unit) {
    // the rows with their headings: a heading before the first row of each named group
    data class Entry(val heading: String?, val index: Int, val first: Boolean, val last: Boolean)
    val entries = buildList {
        items.forEachIndexed { i, item ->
            val first = i == 0 || items[i - 1].group != item.group
            val last = i == items.lastIndex || items[i + 1].group != item.group
            if (first && item.group.isNotEmpty()) add(Entry(item.group, i, false, false))
            add(Entry(null, i, first, last))
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(where) { list.scrollToItem(0) }
    LaunchedEffect(where, selected) {
        if (selected < 0) return@LaunchedEffect
        val at = entries.indexOfFirst { it.heading == null && it.index == selected }
        // the first row of a group brings its heading into view with it
        val target = if (at > 0 && entries[at - 1].heading != null) at - 1 else at
        val shown = list.layoutInfo.visibleItemsInfo
        val firstShown = shown.firstOrNull()?.index ?: 0
        val lastShown = shown.lastOrNull()?.index ?: 0
        if (target <= firstShown || at >= lastShown) list.animateScrollToItem((target - 1).coerceAtLeast(0))
    }
    LazyColumn(modifier.fillMaxWidth().widthIn(max = 1100.dp), state = list) {
        items(entries.size, key = { n -> "$where/$n" }) { n ->
            val e = entries[n]
            if (e.heading != null) {
                BasicText(e.heading.uppercase(), Modifier.padding(start = 26.dp, top = if (n == 0) 0.dp else 30.dp, bottom = 12.dp), style = text(19f, 600, Ui.muted, spacing = (19f * 0.08f).sp))
            } else {
                val item = items[e.index]
                val top = if (e.first && n > 0 && entries[n - 1].heading == null) 30.dp else 0.dp
                RowView(item, e.first, e.last, top, e.index == selected, e.index - 1 == selected && !e.first, editing && e.index == selected, inputFocus, doneEditing)
            }
        }
    }
}

@Composable
private fun RowView(item: Item, first: Boolean, last: Boolean, gapAbove: androidx.compose.ui.unit.Dp, on: Boolean, afterFocused: Boolean, editing: Boolean, inputFocus: FocusRequester, doneEditing: () -> Unit) {
    val bg by animateColorAsState(if (on) Ui.pill else Color.Transparent, tween(120), label = "pill")
    val labelColor = if (on) Ui.onPill else Hue.textPrimary
    val valueColor = if (on) Ui.onPillValue else Hue.textSecondary
    val r = 28.dp
    val box = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
    Box(
        Modifier.padding(top = gapAbove).fillMaxWidth().background(Ui.box, box)
            .padding(start = 6.dp, end = 6.dp, top = if (first) 6.dp else 0.dp, bottom = if (last) 6.dp else 0.dp),
    ) {
        // hairlines between the rows of a group, hidden around the focused one
        if (!first && !on && !afterFocused) Box(Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(1.dp).background(Ui.hairline))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).background(bg, RoundedCornerShape(22.dp)).padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.icon?.let { Image(androidx.compose.ui.res.painterResource(it), null, Modifier.padding(end = 28.dp).size(40.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (on) Ui.onPillValue else Hue.textSecondary)) }
            Box(Modifier.weight(1f)) {
                if (item is Input) InputField(item, on, editing, inputFocus, doneEditing)
                else BasicText(item.label, style = text(27f, 400, if (item is Action && item.danger) (if (on) Ui.dangerOnPill else Hue.danger) else labelColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(30.dp))
            when (item) {
                is Toggle -> SwitchMark(item.on, on)
                is Step -> Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("‹", Modifier.width(30.dp).graphicsLayer { alpha = if (on) 0.85f else 0f }, style = text(34f, 400, valueColor, 1f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    BasicText(item.texts.getOrElse(item.index) { "" }, style = text(25f, 400, valueColor), maxLines = 1)
                    BasicText("›", Modifier.width(30.dp).graphicsLayer { alpha = if (on) 0.85f else 0f }, style = text(34f, 400, valueColor, 1f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                }
                is Action -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.value.isNotEmpty()) BasicText(item.value, Modifier.widthIn(max = 520.dp), style = text(25f, 400, valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.opens) BasicText("›", Modifier.padding(start = 14.dp), style = text(34f, 400, valueColor.copy(alpha = 0.5f), 1f))
                }
                is Info -> if (item.value.isNotEmpty()) BasicText(item.value, Modifier.widthIn(max = 560.dp), style = text(25f, 400, valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                is Pick -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.detail.isNotEmpty()) BasicText(item.detail, Modifier.widthIn(max = 520.dp), style = text(25f, 400, valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.checked) BasicText("✓", Modifier.padding(start = 16.dp), style = text(27f, 600, if (on) Ui.onPill else Ui.green, 1f))
                }
                is Input -> {}
            }
        }
    }
}

/** An iOS switch. */
@Composable
private fun SwitchMark(checked: Boolean, onPill: Boolean) {
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switch")
    val track = if (checked) Ui.green else if (onPill) Ui.switchOffOnPill else Ui.switchOff
    Box(Modifier.width(74.dp).height(42.dp).background(track, RoundedCornerShape(21.dp))) {
        Box(Modifier.offset(x = (4 + 32 * t).dp, y = 4.dp).size(34.dp).background(Color.White, CircleShape))
    }
}

/** The text field of an Input row: its label shows until something is typed; dark text on the light pill. */
@Composable
private fun InputField(item: Input, onPill: Boolean, editing: Boolean, focus: FocusRequester, done: () -> Unit) {
    var value by remember { mutableStateOf("") }
    Box(contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) BasicText(if (onPill && !editing) item.label + str(R.string.ok_to_type) else item.label, style = text(27f, 400, if (onPill) Ui.onPillValue else Hue.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value, { value = it },
            Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            textStyle = text(27f, 400, if (onPill) Ui.onPill else Hue.textPrimary),
            cursorBrush = SolidColor(Ui.onPill),
            keyboardOptions = KeyboardOptions(keyboardType = item.keyboard, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) { item.submit(value.trim()); value = "" }; done() }),
        )
    }
}

// ---------- the apps, as tiles (as the webOS Settings has them) ----------

private const val GRID_COLS = 6
private const val GALLERY_COLS = 5
private const val GRID_TILE = 150

/** `focused`: the focused tile, -1 for the button above the tiles, -2 when the focus is in the list of sections. */
@Composable
private fun AppsGrid(apps: List<AppEntry>, hidden: Set<String>, focused: Int, modifier: Modifier) {
    Row(Modifier.padding(bottom = 8.dp)) { Pill(str(R.string.reset_the_order), focused == -1) }
    val rows = apps.chunked(GRID_COLS)
    val list = rememberLazyListState()
    LaunchedEffect(focused) {
        if (focused < 0) return@LaunchedEffect
        val r = focused / GRID_COLS
        val shown = list.layoutInfo.visibleItemsInfo
        val first = shown.firstOrNull()?.index ?: 0
        val last = shown.lastOrNull()?.index ?: 0
        if (r < first || r >= last) list.animateScrollToItem((r - 1).coerceAtLeast(0))
    }
    // room around the tiles for a focused one, which grows past its square
    // (the room above each row is part of the row, so a row scrolled to the top keeps it)
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
        items(rows.size, key = { r -> rows[r].first().pkg }) { r ->
            Row(Modifier.padding(top = 20.dp)) {
                rows[r].forEachIndexed { i, app -> AppTile(app, app.pkg !in hidden, focused == r * GRID_COLS + i); Spacer(Modifier.width(16.dp)) }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, shown: Boolean, focused: Boolean) {
    val grow by animateFloatAsState(if (focused) 1.12f else 1f, tween(160, easing = Ease.dock), label = "appTile")
    Column(Modifier.width(GRID_TILE.dp).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(GRID_TILE.dp).graphicsLayer { scaleX = grow; scaleY = grow }) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = if (shown) 1f else 0.45f }.clip(RoundedCornerShape(24.dp))
                    .background(if (focused) Hue.tileBgFocus else Hue.tileBg)
                    .then(if (focused) Modifier.border(3.dp, Ui.pill, RoundedCornerShape(24.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val icon = app.icon
                if (icon != null) Image(icon, null, if (app.iconFull) Modifier.fillMaxSize() else Modifier.fillMaxSize(0.8f), contentScale = ContentScale.Fit, filterQuality = FilterQuality.Medium)
                else BasicText(app.label.take(1).uppercase(), style = text(64f, 600))
            }
            // a tick on the apps that are on the home screen
            if (shown) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).background(Color(0xE6F2F2F7), CircleShape), contentAlignment = Alignment.Center) {
                BasicText("✓", style = text(20f, 700, Ui.onPill, 1f))
            }
        }
        BasicText(app.label, Modifier.padding(top = 12.dp), style = text(20f, 400, if (focused) Hue.textPrimary else Hue.textSecondary).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A rounded button over a list or grid (as .pill in the webOS app). */
@Composable
private fun Pill(label: String, focused: Boolean) {
    val bg by animateColorAsState(if (focused) Ui.pill else Color(0x17FFFFFF), tween(120), label = "pillBg")
    BasicText(label, Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 30.dp, vertical = 12.dp), style = text(23f, 500, if (focused) Ui.onPill else Hue.textPrimary))
}

// ---------- the gallery ----------

private fun galleryFooter(folders: List<GalleryFolder>, inside: Int?, at: Int, picks: List<String>): String {
    val folder = inside?.let { folders.getOrNull(it) }
    return when {
        folder != null -> folder.name + " · " + str(R.string.n_of_m_ticked, folder.pictures.count { it in picks }, folder.pictures.size)
        at < folders.size -> folders[at].name + " · " + plural(R.plurals.pictures_n, folders[at].pictures.size)
        else -> str(R.string.ok_allows_the_launcher_to_read_the_pictures)
    }
}

@Composable
private fun GalleryGrid(folders: List<GalleryFolder>, inside: Int?, focused: Int, picks: List<String>, canRead: Boolean, modifier: Modifier) {
    val folder = inside?.let { folders.getOrNull(it) }
    Row(Modifier.padding(bottom = 8.dp)) { Pill(str(R.string.untick_all), focused == -1) }
    val tiles: List<String> = folder?.pictures ?: (folders.map { it.id } + if (canRead) emptyList() else listOf("ask"))
    val rows = tiles.chunked(GALLERY_COLS)
    val list = rememberLazyListState()
    LaunchedEffect(focused, inside) {
        if (focused < 0) return@LaunchedEffect
        val r = focused / GALLERY_COLS
        val shown = list.layoutInfo.visibleItemsInfo
        val first = shown.firstOrNull()?.index ?: 0
        val last = shown.lastOrNull()?.index ?: 0
        if (r < first || r >= last) list.animateScrollToItem((r - 1).coerceAtLeast(0))
    }
    LaunchedEffect(inside) { list.scrollToItem(0) }
    if (tiles.isEmpty()) BasicText(str(R.string.this_folder_has_no_pictures_or_folders), Modifier.padding(24.dp), style = text(24f, 400, Hue.textSecondary))
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
        items(rows.size, key = { r -> (inside ?: -1).toString() + "/" + r }) { r ->
            Row(Modifier.padding(top = 18.dp)) {
                rows[r].forEachIndexed { i, id ->
                    val index = r * GALLERY_COLS + i
                    val f = folder == null && index < folders.size
                    when {
                        folder != null -> PictureTile(id, id in picks, focused == index)
                        f -> FolderTile(folders[index], folders[index].pictures.count { it in picks }, focused == index)
                        else -> AskTile(focused == index)
                    }
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}

private const val TILE_W = 200
private const val TILE_H = 113

@Composable
private fun Thumb(id: String, modifier: Modifier) {
    val context = LocalContext.current
    val image by androidx.compose.runtime.produceState(Gallery.cached(id), id) { if (value == null) value = withContext(Dispatchers.IO) { Gallery.thumb(context, id) } }
    image?.let { Image(it, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium) }
}

@Composable
private fun TileFrame(focused: Boolean, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val grow by animateFloatAsState(if (focused) 1.08f else 1f, tween(160, easing = Ease.dock), label = "galleryTile")
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.width(TILE_W.dp).height(TILE_H.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(shape)
            .background(Hue.tileBg).then(if (focused) Modifier.border(3.dp, Ui.pill, shape) else Modifier),
        content = content,
    )
}

@Composable
private fun PictureTile(id: String, ticked: Boolean, focused: Boolean) {
    TileFrame(focused) {
        Thumb(id, Modifier.fillMaxSize())
        if (ticked) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).background(Color(0xE6F2F2F7), CircleShape), contentAlignment = Alignment.Center) {
            BasicText("✓", style = text(19f, 700, Ui.onPill, 1f))
        }
    }
}

@Composable
private fun FolderTile(folder: GalleryFolder, ticked: Int, focused: Boolean) {
    TileFrame(focused) {
        folder.pictures.firstOrNull()?.let { Thumb(it, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }) }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            BasicText(folder.name, style = text(20f, 600, lineHeight = 1.15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(plural(R.plurals.pictures_n, folder.pictures.size), style = text(15f, 400, Hue.textSecondary, 1.2f), maxLines = 1)
        }
        if (ticked > 0) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xE6F2F2F7), CircleShape).padding(horizontal = 11.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            BasicText("✓ $ticked", style = text(16f, 700, Ui.onPill, 1.1f))
        }
    }
}

/** Asks to see the box's pictures (OK opens the system's question). */
@Composable
private fun AskTile(focused: Boolean) {
    TileFrame(focused) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            BasicText(str(R.string.pictures_on_this_box), style = text(20f, 600, lineHeight = 1.15f), maxLines = 2)
        }
    }
}
