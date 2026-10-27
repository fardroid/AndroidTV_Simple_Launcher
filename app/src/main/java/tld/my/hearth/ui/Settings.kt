package tld.my.hearth.ui

// Settings, in the look of the webOS app's (src/screens/Settings.tsx): the sections on the left, each with its own colour (iOS),
// the chosen one on the right in rounded groups of rows; the focused row is a light pill (Material 3 / Google TV) and its
// explanation sits at the foot of the screen. In the list of sections ↑ ↓ pick one and → or OK open it. In a section ↑ ↓ pick
// a row, OK switches it or steps a choice round, ← → step choices and sizes. Back goes back: a page to its section, a section
// to the list of sections, and the list out of Settings.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import tld.my.hearth.AppEntry
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
import tld.my.hearth.Apps
import tld.my.hearth.BuildConfigInfo
import tld.my.hearth.Cal
import tld.my.hearth.City
import tld.my.hearth.ClockColors
import tld.my.hearth.Config
import tld.my.hearth.HearthState
import tld.my.hearth.HomeRole
import tld.my.hearth.Lang
import tld.my.hearth.LangSetting
import tld.my.hearth.Net
import tld.my.hearth.PlateMode
import tld.my.hearth.Screen
import tld.my.hearth.TileShape
import tld.my.hearth.Wallpapers
import tld.my.hearth.Weather
import tld.my.hearth.count
import tld.my.hearth.tr

// ---------- the colours (the same as the webOS Settings, src/theme/global.css) ----------

private object Ui {
    val screen = Color(0xDB08090E)
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
}

private data class Toggle(override val label: String, override val description: String, override val group: String, val on: Boolean, val set: (Boolean) -> Unit) : Item
/** a choice among values: ← → step and stop at the ends, OK steps on and wraps round */
private data class Step(override val label: String, override val description: String, override val group: String, val texts: List<String>, val index: Int, val pick: (Int) -> Unit) : Item
private data class Action(override val label: String, override val description: String, override val group: String, val value: String = "", val opens: Boolean = false, val danger: Boolean = false, val run: () -> Unit) : Item
/** a fact to read, nothing to change */
private data class Info(override val label: String, override val description: String, override val group: String, val value: String = "") : Item
private data class Input(override val label: String, override val description: String, override val group: String, val keyboard: KeyboardType, val submit: (String) -> Unit) : Item
/** one of a list to pick (a city): its detail on the right, a tick on the current one */
private data class Pick(override val label: String, val detail: String, override val group: String, val checked: Boolean, val pick: () -> Unit) : Item {
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
private enum class Section(private val en: String, private val ru: String, val color: Long, val icon: String) {
    APPS("Apps", "Приложения", 0xFF0A84FF, "M4.5 4.5h5v5h-5z M14.5 4.5h5v5h-5z M4.5 14.5h5v5h-5z M14.5 14.5h5v5h-5z"),
    LOOK("Appearance", "Оформление", 0xFF5E5CE6, "M12 3.5a8.5 8.5 0 1 0 0 17c1 0 1.6-.7 1.6-1.5s-.6-1.3-.6-2.1c0-.9.7-1.6 1.6-1.6h2a3.9 3.9 0 0 0 3.9-3.9c0-4.4-3.8-7.9-8.5-7.9z M7.8 11.5h.01 M10.2 7.6h.01 M14.6 7.8h.01"),
    WIDGETS("Widgets", "Виджеты", 0xFFFF9F0A, "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z M12 7.5v4.8l3.2 2"),
    WALLPAPER("Wallpaper", "Обои", 0xFFBF5AF2, "M4 5h16v14H4z M4 16l4.5-4.5 4 4 3-3 4.5 4.5 M15.5 9h.01"),
    HOME("Home screen", "Главный экран", 0xFF30D158, "M4.5 11l7.5-6.5 7.5 6.5v8.5h-15z M10 19.5v-5h4v5");
    val title get() = tr(en, ru)
}

/** A page opened from a row of a section (it takes the right-hand side until Back). */
private sealed class Page(private val en: String, private val ru: String) {
    val title get() = tr(en, ru)
    data object CityPage : Page("Choose a city", "Выберите город")
    data object Calendars : Page("Calendars", "Календари")
    data class CalendarOne(val index: Int, val name: String) : Page(name, name)
}

private fun interval(sec: Int) = when {
    sec < 60 -> count(sec, "second", "seconds", "секунда", "секунды", "секунд")
    sec < 3600 -> count(sec / 60, "minute", "minutes", "минута", "минуты", "минут")
    else -> count(sec / 3600, "hour", "hours", "час", "часа", "часов")
}

private fun weightText(w: Int) = when (w) {
    200 -> tr("Thin", "Тонкий"); 300 -> tr("Light", "Лёгкий"); 500 -> tr("Medium", "Средний"); 600 -> tr("Bold", "Жирный"); else -> tr("Regular", "Обычный")
}

/** The calendar's name (from the feed, once read) or its site; the secret part of the address is never shown. */
private fun calendarName(state: HearthState, url: String, i: Int): String =
    state.calendar?.sources?.firstOrNull { it.src == Cal.normalizeUrl(url) }?.name?.takeIf { it.isNotEmpty() } ?: tr("Calendar ${i + 1}", "Календарь ${i + 1}")

private fun calendarSite(url: String): String = runCatching { java.net.URI(Cal.normalizeUrl(url)).host }.getOrNull() ?: tr("iCal link", "ссылка iCal")

/** The look, the margins and the widgets back to how they were at first (apps, wallpaper, weather and calendars stay). */
private fun resetLook(c: Config): Config {
    val d = Config()
    return c.copy(
        iconScale = d.iconScale, showLabels = d.showLabels, tileShape = d.tileShape, sideMargin = d.sideMargin, bottomMargin = d.bottomMargin,
        cardSize = d.cardSize, plates = d.plates, glass = d.glass, shadows = d.shadows,
        clock24 = d.clock24, clockSize = d.clockSize, clockFont = d.clockFont, clockWeight = d.clockWeight, clockColor = d.clockColor,
        showDate = d.showDate, dateSize = d.dateSize,
    )
}

private enum class SetZone { NAV, CONTENT }

@Composable
fun SettingsPanel(state: HearthState, keys: KeyTarget) {
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
    val c = state.config
    val gridApps = remember(state.apps, c.appOrder) {
        c.appOrder.mapNotNull { pkg -> state.apps.firstOrNull { it.pkg == pkg } } + state.apps.filter { it.pkg !in c.appOrder }.sortedBy { it.label.lowercase() }
    }
    fun set(change: (Config) -> Config) = state.update(change)
    val page = pages.lastOrNull()
    val onGrid = page == null && section == Section.APPS
    val where = page?.title ?: section.name

    fun keep(at: String) { selectedAt[at] = selected }
    fun open(p: Page) { keep(where); pages.add(p); selected = 0 }
    fun back() {
        when {
            pages.isNotEmpty() -> { pages.removeAt(pages.lastIndex); selected = selectedAt[pages.lastOrNull()?.title ?: section.name] ?: 0 }
            zone == SetZone.CONTENT -> { keep(where); zone = SetZone.NAV }
            else -> state.screen = Screen.HOME
        }
    }

    val items: List<Item> = when (page) {
        Page.CityPage -> buildList {
            add(Input(tr("Search for a city", "Найти город"), tr("Type a name and press OK, for example Milan. Needs the internet.", "Введите название и нажмите OK, например «Милан». Нужен интернет."), "", KeyboardType.Text) { q ->
                cityStatus = tr("Searching…", "Ищу…")
                scope.launch {
                    val found = withContext(Dispatchers.IO) { runCatching { Net.searchCities(q) }.getOrNull() }
                    cityResults = found
                    cityStatus = when { found == null -> tr("The weather service cannot be reached", "Нет связи с сервисом погоды"); found.isEmpty() -> tr("Nothing found", "Ничего не нашлось"); else -> "" }
                }
            })
            if (cityStatus.isNotEmpty()) add(Info(cityStatus, "", ""))
            cityResults?.takeIf { it.isNotEmpty() }?.forEach { city ->
                add(Pick(city.name, city.detail, tr("Search results", "Результаты поиска"), c.city == city) { set { it.copy(city = city) }; back() })
            }
            Weather.PRESET_CITIES.forEach { city ->
                add(Pick(city.name, city.detail, tr("Popular cities", "Популярные города"), c.city?.name == city.name && c.city?.lat == city.lat) { set { it.copy(city = city) }; back() })
            }
        }
        Page.Calendars -> buildList {
            add(Input(tr("Add a link…", "Добавить ссылку…"), tr("The secret address in iCal format from Google Calendar: Settings → the calendar → Secret address in iCal format.", "Закрытый адрес в формате iCal из Google Календаря: Настройки → календарь → «Закрытый адрес в формате iCal»."), "", KeyboardType.Uri) { raw ->
                val url = Cal.normalizeUrl(raw)
                calendarStatus = when {
                    !Cal.validUrl(url) -> tr("That does not look like a calendar address", "Это не похоже на адрес календаря")
                    url in c.calendars -> tr("This calendar is already on the list", "Этот календарь уже есть")
                    else -> { set { it.copy(calendars = it.calendars + url) }; tr("Calendar added", "Календарь добавлен") }
                }
            })
            if (calendarStatus.isNotEmpty()) add(Info(calendarStatus, "", ""))
            val connected = tr("Connected: ", "Подключено: ") + c.calendars.size
            c.calendars.forEachIndexed { i, url ->
                val name = calendarName(state, url, i)
                add(Action(name, tr("OK to remove it.", "OK, чтобы убрать его."), connected, calendarSite(url), opens = true) { open(Page.CalendarOne(i, name)) })
            }
            add(Action(
                tr("Refresh now", "Обновить сейчас"), tr("Read the calendars again.", "Прочитать календари заново."), "",
                if (state.calendarLoading) tr("Loading…", "Загружаю…") else state.calendar?.let { tr("Read at ", "Прочитан в ") + Cal.localTime(it.at).toLocalTime().toString().take(5) } ?: tr("Not read yet", "Ещё не читался"),
            ) { state.refreshCalendarNow() })
        }
        is Page.CalendarOne -> listOf(
            Action(tr("Remove the calendar", "Убрать календарь"), tr("It leaves the home screen.", "Он пропадёт с главного экрана."), "", danger = true) {
                set { it.copy(calendars = it.calendars.filterIndexed { i, _ -> i != page.index }) }
                back()
            },
            Action(tr("Keep it", "Оставить"), "", "") { back() },
        )
        null -> when (section) {
            // the apps are a grid of tiles (AppsGrid), not rows
            Section.APPS -> emptyList()
            Section.LOOK -> buildList {
                val row = tr("App row", "Ряд приложений")
                add(choice(tr("Tiles", "Вид плиток"), tr("Square icons, or the wide banners the apps have for TVs.", "Квадратные иконки или широкие баннеры, которые есть у приложений для ТВ."), row, TileShape.entries.map { it to it.label }, c.tileShape) { v -> set { it.copy(tileShape = v) } })
                add(range(tr("Icon size", "Размер значков"), tr("How big the app tiles are.", "Насколько крупные плитки приложений."), row, c.iconScale, 50, 140, 10, "%") { v -> set { it.copy(iconScale = v) } })
                add(Toggle(tr("App names", "Названия приложений"), tr("A name under each tile. Off leaves only the icons.", "Подпись под каждой плиткой. Если выключить, останутся только значки."), row, c.showLabels) { v -> set { it.copy(showLabels = v) } })
                add(range(tr("Side margin", "Поля по бокам"), tr("Space left and right of the row.", "Отступ слева и справа от ряда."), row, c.sideMargin, 0, 300, 10, " px") { v -> set { it.copy(sideMargin = v) } })
                add(range(tr("Bottom margin", "Поле снизу"), tr("Space below the row.", "Отступ под рядом."), row, c.bottomMargin, 0, 200, 10, " px") { v -> set { it.copy(bottomMargin = v) } })
                val cards = tr("Cards", "Карточки")
                add(range(tr("Text size", "Размер текста"), tr("All the text on the weather and calendar cards.", "Весь текст на карточках погоды и календаря."), cards, c.cardSize, 60, 160, 5, "%") { v -> set { it.copy(cardSize = v) } })
                add(choice(tr("Card colour", "Цвет карточек"), tr("Auto: all the cards turn light or dark together, depending on the picture behind them, so the text stays readable on each.", "Авто: все карточки вместе становятся светлыми или тёмными в зависимости от картинки за ними, чтобы текст хорошо читался на каждой."), cards, PlateMode.entries.map { it to it.label }, c.plates) { v -> set { it.copy(plates = v) } })
                if (c.wallpaperOn) add(Toggle(tr("Frosted glass", "Матовое стекло"), tr("The cards blur the picture behind them.", "Карточки размывают картинку под собой."), cards, c.glass) { v -> set { it.copy(glass = v) } })
                add(Toggle(tr("Shadows", "Тени"), tr("A soft shadow under the clock and the cards. Without it the box has a little less to draw.", "Мягкая тень под часами и карточками. Без неё приставка рисует экран чуть легче."), cards, c.shadows) { v -> set { it.copy(shadows = v) } })
            }
            Section.WIDGETS -> buildList {
                add(choice(tr("Font", "Шрифт"), tr("For the clock, the date, the weather and the calendar. \"TV font\" is the box's own; the others come with the launcher.", "Для часов, даты, погоды и календаря. «Шрифт ТВ» встроен в приставку, остальные идут с лаунчером."), "", Fonts.ALL.map { it.id to it.label }, c.clockFont) { v -> set { it.copy(clockFont = v) } })
                val clock = tr("Clock", "Часы")
                add(choice(tr("Format", "Формат"), tr("12-hour or 24-hour.", "12 или 24 часа."), clock, listOf(true to tr("24-hour", "24-часовой"), false to tr("12-hour (AM/PM)", "12-часовой (AM/PM)")), c.clock24) { v -> set { it.copy(clock24 = v) } })
                add(range(tr("Size", "Размер"), tr("The height of the digits.", "Высота цифр."), clock, c.clockSize, 80, 220, 10, " px") { v -> set { it.copy(clockSize = v) } })
                add(choice(tr("Weight", "Толщина"), tr("From thin to bold digits.", "От тонких до жирных цифр."), clock, Fonts.WEIGHTS.map { it to weightText(it) }, c.clockWeight) { v -> set { it.copy(clockWeight = v) } })
                add(choice(tr("Colour", "Цвет"), tr("White is hard to read on a light picture: pick a dark colour or a gradient for light wallpapers.", "Белый плохо читается на светлой картинке: для светлых обоев выберите тёмный цвет или градиент."), clock, ClockColors.ALL.map { it.id to it.label }, c.clockColor) { v -> set { it.copy(clockColor = v) } })
                val date = tr("Date", "Дата")
                add(Toggle(tr("Show the date", "Показывать дату"), tr("The weekday and the date beside the time.", "День недели и дата рядом со временем."), date, c.showDate) { v -> set { it.copy(showDate = v) } })
                if (c.showDate) add(range(tr("Date size", "Размер даты"), tr("The size of the weekday and the date.", "Размер дня недели и даты."), date, c.dateSize, 60, 160, 5, "%") { v -> set { it.copy(dateSize = v) } })
                val weather = tr("Weather", "Погода")
                add(Toggle(tr("Show the weather", "Показывать погоду"), tr("The temperature and the forecast, on their own card. Open-Meteo, no account needed.", "Температура и прогноз на отдельной карточке. Open-Meteo, без аккаунта."), weather, c.weatherOn) { v -> set { it.copy(weatherOn = v) } })
                add(Action(tr("City", "Город"), tr("Pick a city from the list, or search for any place by name. Needs the internet.", "Выберите город из списка или найдите любое место по названию. Нужен интернет."), weather, c.city?.name ?: tr("Not chosen", "Не выбран"), opens = true) { open(Page.CityPage) })
                add(choice(tr("Temperature unit", "Единицы температуры"), tr("Degrees Celsius or Fahrenheit.", "Градусы Цельсия или Фаренгейта."), weather, listOf(false to tr("Celsius (°C)", "Цельсий (°C)"), true to tr("Fahrenheit (°F)", "Фаренгейт (°F)")), c.fahrenheit) { v -> set { it.copy(fahrenheit = v) } })
                val calendar = tr("Calendar", "Календарь")
                add(Toggle(tr("Show the calendar", "Показывать календарь"), tr("The day's events in the top right of the home screen.", "События дня в правом верхнем углу главного экрана."), calendar, c.calendarOn) { v -> set { it.copy(calendarOn = v) } })
                add(Action(tr("Calendars", "Календари"), tr("iCal links from Google Calendar. Several calendars can be shown at once.", "Ссылки в формате iCal из Google Календаря. Можно подключить несколько календарей сразу."), calendar,
                    if (c.calendars.isEmpty()) tr("None", "Не задан") else count(c.calendars.size, "calendar", "calendars", "календарь", "календаря", "календарей"), opens = true) { open(Page.Calendars) })
            }
            Section.WALLPAPER -> buildList {
                add(Toggle(tr("Pictures in the background", "Картинки на фоне"), tr("Off: a calm dark gradient instead.", "Если выключить, на фоне будет спокойный тёмный градиент."), "", c.wallpaperOn) { v -> set { it.copy(wallpaperOn = v) } })
                if (c.wallpaperOn) {
                    val show = tr("Slideshow", "Слайд-шоу")
                    add(choice(tr("Change every", "Менять каждые"), tr("How long each picture stays on screen.", "Сколько каждая картинка остаётся на экране."), show, Config.INTERVALS.map { it to interval(it) }, c.intervalSec) { v -> set { it.copy(intervalSec = v) } })
                    add(Toggle(tr("Shuffle", "Перемешивать"), tr("Random order. Off: in turn.", "Случайный порядок. Если выключено, по очереди."), show, c.shuffle) { v -> set { it.copy(shuffle = v) } })
                }
                val folder = (Wallpapers.folder(context)?.absolutePath ?: "/sdcard/Android/data/tld.my.hearth/files/wallpapers") + "/"
                add(Info(tr("Picture folder", "Папка для картинок"), tr("Add your own pictures from a computer: adb push photo.jpg ", "Свои картинки добавляются с компьютера: adb push фото.jpg ") + folder, tr("Your own pictures", "Свои картинки"), "adb push"))
            }
            Section.HOME -> buildList {
                add(choice("Language · Язык", tr("The language of the launcher. \"Same as the box\" follows the box's own language.", "Язык лаунчера. «Как в системе» следует языку самой приставки."), "", LangSetting.entries.map { it to Lang.name(it) }, c.language) { v -> set { it.copy(language = v) } })
                add(Action(tr("Reset look and clock", "Сбросить вид и часы"), tr("Sizes, margins, cards and the widgets go back to how they were at first. Apps, wallpaper, weather and calendars stay.", "Размеры, поля, карточки и виджеты вернутся к начальным. Приложения, обои, погода и календари останутся."), "") { set(::resetLook) })
                add(Action(tr("TV settings", "Настройки ТВ"), tr("Network, sound, accounts, apps: the box's own settings.", "Сеть, звук, аккаунты, приложения: настройки самой приставки."), "", opens = true) { Apps.openTvSettings(context) })
                // read again whenever the launcher comes back (from the box's settings, say)
                @Suppress("UNUSED_VARIABLE") val again = state.resumes
                val isHome = HomeRole.isDefault(context)
                val homeKey = HomeRole.homeKeyOpensLauncher(context)
                add(Action(tr("Launcher by default", "Лаунчер по умолчанию"),
                    when {
                        homeKey -> tr("The Home button opens this launcher.", "Кнопка Home открывает этот лаунчер.")
                        !isHome -> tr("OK opens the box's choice of home screen: pick Simple TV launcher there. On Google TV, also switch on Simple TV launcher in Accessibility (OK here again).", "OK откроет выбор главного экрана: выберите там Simple TV launcher. На Google TV нужно ещё включить Simple TV launcher в «Специальных возможностях» (снова OK здесь).")
                        else -> tr("Google TV sends the Home button to its own screen. OK opens Accessibility: switch on Simple TV launcher there.", "Google TV отправляет кнопку Home на свой экран. OK откроет «Специальные возможности»: включите там Simple TV launcher.")
                    },
                    tr("Opening the launcher", "Запуск лаунчера"), if (homeKey) tr("Yes", "Да") else tr("No", "Нет"), opens = true) {
                    if (!isHome) HomeRole.openHomeSettings(context) else if (!homeKey) HomeRole.openAccessibility(context) else HomeRole.openHomeSettings(context)
                })
                add(Info("Simple TV launcher", tr("The Android TV version of Simple LG launcher, which is based on Hearth by rudra-avatar. github.com/fardroid/AndroidTV_Simple_Launcher", "Android TV версия Simple LG launcher, который основан на Hearth от rudra-avatar. github.com/fardroid/AndroidTV_Simple_Launcher"), tr("About", "О лаунчере"), BuildConfigInfo.version(context)))
            }
        }
    }

    if (items.isNotEmpty() && selected !in items.indices) selected = items.lastIndex

    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editing by remember { mutableStateOf(false) }

    SideEffect {
        keys.handler = { key, _ ->
            val item = items.getOrNull(selected)
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
                    if (i != item.index) item.pick(i)
                } else if (key == Nav.LEFT && !editing) back()
                Nav.OK -> when (item) {
                    is Toggle -> item.set(!item.on)
                    is Step -> item.pick((item.index + 1) % item.texts.size)
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

    Row(Modifier.fillMaxSize().graphicsLayer { alpha = enter.value }.background(Ui.screen)) {
        // ----- the sections -----
        Column(Modifier.width(600.dp).fillMaxHeight().background(Ui.nav).padding(start = 96.dp, end = 36.dp, top = 84.dp, bottom = 60.dp)) {
            BasicText(tr("Settings", "Настройки"), Modifier.padding(start = 20.dp, bottom = 34.dp), style = text(46f, 600))
            Section.entries.forEach { s -> NavItem(s, current = s == section, focused = zone == SetZone.NAV && s == section) }
        }
        // ----- the chosen section, or a page opened from it -----
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 64.dp, end = 120.dp, top = 84.dp)) {
            BasicText(page?.title ?: section.title, style = text(46f, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val hint = when {
                zone == SetZone.NAV -> tr("↑ ↓ choose · → open · Back closes Settings", "↑ ↓ выбор · → открыть · «Назад» закрывает настройки")
                page != null -> tr("OK chooses · Back returns", "OK выбирает · «Назад» возвращает")
                else -> tr("OK changes · ← → adjust · Back to the list", "OK меняет · ← → подстраивают · «Назад» к списку")
            }
            BasicText(if (onGrid && zone == SetZone.CONTENT) tr("OK shows or hides an app · Back to the list", "OK показывает или скрывает приложение · «Назад» к списку") else hint, Modifier.padding(top = 10.dp, bottom = 30.dp), style = text(21f, 400, Ui.muted))
            if (onGrid) AppsGrid(gridApps, c.hidden, if (zone == SetZone.CONTENT) appAt else -2, Modifier.weight(1f))
            else OptionList(items, Modifier.weight(1f), where, if (zone == SetZone.CONTENT) selected else -1, editing, inputFocus) { editing = false; keys.refocus() }
            val footer = when {
                zone != SetZone.CONTENT -> ""
                onGrid -> if (appAt == -1) tr("Back to A to Z. Hold OK on a tile on the home screen to move it.", "Вернуть порядок по алфавиту. Чтобы переместить плитку, удерживайте на ней OK на главном экране.")
                    else gridApps.getOrNull(appAt)?.let { app -> app.label + if (app.pkg in c.hidden) tr(" · hidden from the home screen", " · скрыто с главного экрана") else tr(" · on the home screen", " · на главном экране") }.orEmpty()
                else -> items.getOrNull(selected)?.description.orEmpty()
            }
            BasicText(footer, Modifier.padding(start = 24.dp, top = 16.dp, bottom = 56.dp).heightIn(min = 64.dp), style = text(22f, 400, Hue.textSecondary, 1.45f), maxLines = 3)
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
        if (value.isEmpty()) BasicText(if (onPill && !editing) item.label + tr("  ·  OK to type", "  ·  OK, чтобы ввести") else item.label, style = text(27f, 400, if (onPill) Ui.onPillValue else Hue.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private const val GRID_TILE = 150

/** `focused`: the focused tile, -1 for the button above the tiles, -2 when the focus is in the list of sections. */
@Composable
private fun AppsGrid(apps: List<AppEntry>, hidden: Set<String>, focused: Int, modifier: Modifier) {
    Row(Modifier.padding(bottom = 8.dp)) { Pill(tr("Reset the order", "Сбросить порядок"), focused == -1) }
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
