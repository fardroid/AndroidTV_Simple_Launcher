package tld.my.hearth.ui

// The home screen: the clock, weather and calendar cards on the frosted plates, and the row of apps. With the remote:
// ← → along the row, OK opens, OK held (or Menu) opens the app's menu, ↑ climbs to the weather card and from there to the
// calendar (← → change the day, ↑ the month, OK back to today), ↓ or Back returns. Back on the row goes to the first tile.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tld.my.hearth.Apps
import tld.my.hearth.Cal
import tld.my.hearth.HearthState
import tld.my.hearth.Lang
import tld.my.hearth.Plates
import tld.my.hearth.Screen
import tld.my.hearth.Weather
import tld.my.hearth.tr
import java.time.Instant
import java.time.ZoneId

private enum class Zone { DOCK, WEATHER, CALENDAR }

private data class MenuItem(val label: String, val run: () -> Unit)

@Composable
fun HomeScreen(state: HearthState, keys: KeyTarget, active: Boolean) {
    val context = LocalContext.current
    val c = state.config
    var zone by remember { mutableStateOf(Zone.DOCK) }
    var selected by remember { mutableIntStateOf(0) }
    var fast by remember { mutableStateOf(false) }
    var weatherDay by remember { mutableIntStateOf(0) }
    var dayOffset by remember { mutableIntStateOf(0) }
    var dayDir by remember { mutableIntStateOf(0) }
    var monthMode by remember { mutableStateOf(false) }
    var month by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf<List<String>?>(null) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<List<MenuItem>?>(null) }
    var menuAt by remember { mutableIntStateOf(0) }

    val nowMs = state.nowMs
    val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val weatherData = if (c.weatherOn) state.weatherFor(nowMs) else null
    val weatherView = remember(weatherData, c.fahrenheit, c.city, weatherDay, Lang.ru) {
        weatherData?.let { Weather.view(it, c.fahrenheit, c.city?.name.orEmpty(), weatherDay) }
    }
    val calendarOn = c.calendarOn && c.calendars.isNotEmpty()
    val calendar = if (calendarOn) state.calendarFor(nowMs) else null

    // the home row: the apps in the user's order, then the launcher's own tiles
    val appTiles = remember(state.apps, c.appOrder, c.hidden) { Apps.ordered(state.apps, c).map { TileItem(it.pkg, it.label, it) } }
    val ownTiles = remember(Lang.ru) {
        listOf(TileItem(Apps.TV_SETTINGS, tr("TV settings", "Настройки ТВ"), null, Glyph.TV), TileItem(Apps.HEARTH_SETTINGS, tr("Launcher settings", "Настройки лаунчера"), null, Glyph.GEAR))
    }
    val ordered = moving?.let { keysInOrder -> keysInOrder.mapNotNull { k -> appTiles.firstOrNull { it.key == k } } } ?: appTiles
    val items = ordered + ownTiles
    if (selected > items.lastIndex) selected = maxOf(0, items.lastIndex)

    // Home pressed while the launcher was already in front: back to the start. Coming back from an app: the cards reset,
    // the row stays where it was.
    var seenPresses by remember { mutableIntStateOf(state.homePresses) }
    LaunchedEffect(state.homePresses, state.homeReset) {
        if (state.homePresses == seenPresses) return@LaunchedEffect
        seenPresses = state.homePresses
        zone = Zone.DOCK; weatherDay = 0; dayOffset = 0; dayDir = 0; monthMode = false; month = 0; menu = null
        if (state.homeReset) selected = 0
    }

    fun open(item: TileItem) {
        when (item.key) {
            Apps.HEARTH_SETTINGS -> state.screen = Screen.SETTINGS
            Apps.TV_SETTINGS -> Apps.openTvSettings(context)
            else -> item.app?.let { Apps.launch(context, it) }
        }
    }

    fun menuFor(item: TileItem): List<MenuItem>? {
        val app = item.app ?: return null
        return listOf(
            MenuItem(tr("Move", "Переместить")) { moving = appTiles.map { it.key }; movingKey = item.key },
            MenuItem(tr("Hide from the home screen", "Скрыть с главного экрана")) { state.hide(app.pkg) },
            MenuItem(tr("App info", "О приложении")) { Apps.openAppInfo(context, app.pkg) },
            MenuItem(tr("Uninstall", "Удалить")) { Apps.uninstall(context, app.pkg) },
        )
    }

    fun shiftDay(days: Int) {
        dayDir = if (days > 0) 1 else if (days < 0) -1 else 0
        dayOffset = (dayOffset + days).coerceIn(-Cal.BACK_DAYS, Cal.AHEAD_DAYS)
    }

    fun shiftMonth(months: Int) {
        val lo = Cal.monthOffsetOfDay(nowMs, -Cal.BACK_DAYS); val hi = Cal.monthOffsetOfDay(nowMs, Cal.AHEAD_DAYS)
        month = (month + months).coerceIn(lo, hi)
    }

    fun leaveMonth() {
        if (Cal.monthOffsetOfDay(nowMs, dayOffset) != month) {
            val first = Cal.dayOffsetOfMonth(nowMs, month)
            dayDir = if (first > dayOffset) 1 else -1
            dayOffset = first.coerceIn(-Cal.BACK_DAYS, Cal.AHEAD_DAYS)
        }
        monthMode = false
    }

    SideEffect {
        if (!active) return@SideEffect
        keys.handler = handler@{ key, repeat ->
            fast = repeat
            menu?.let { list ->
                when (key) {
                    Nav.UP -> menuAt = (menuAt - 1).coerceAtLeast(0)
                    Nav.DOWN -> menuAt = (menuAt + 1).coerceAtMost(list.lastIndex)
                    Nav.OK -> { menu = null; list[menuAt].run() }
                    Nav.BACK, Nav.HOLD -> menu = null
                    else -> {}
                }
                return@handler
            }
            moving?.let { order ->
                val i = order.indexOf(movingKey)
                when (key) {
                    Nav.LEFT, Nav.RIGHT -> {
                        val j = i + if (key == Nav.LEFT) -1 else 1
                        if (i >= 0 && j in order.indices) {
                            moving = order.toMutableList().also { it[i] = order[j]; it[j] = order[i] }
                            selected = j
                        }
                    }
                    Nav.OK, Nav.BACK, Nav.HOLD -> { state.setOrder(order); moving = null; movingKey = null }
                    else -> {}
                }
                return@handler
            }
            when (zone) {
                Zone.WEATHER -> when (key) {
                    Nav.LEFT -> weatherDay = (weatherDay - 1).coerceAtLeast(0)
                    Nav.RIGHT -> weatherDay = (weatherDay + 1).coerceAtMost(maxOf(0, (weatherView?.days?.size ?: 1) - 1))
                    Nav.UP -> if (calendarOn) zone = Zone.CALENDAR
                    Nav.DOWN, Nav.BACK -> zone = Zone.DOCK
                    Nav.OK -> weatherDay = 0
                    else -> {}
                }
                Zone.CALENDAR -> if (monthMode) when (key) {
                    Nav.LEFT -> shiftMonth(-1)
                    Nav.RIGHT -> shiftMonth(1)
                    Nav.DOWN, Nav.BACK, Nav.OK -> leaveMonth()
                    else -> {}
                } else when (key) {
                    Nav.LEFT -> shiftDay(-1)
                    Nav.RIGHT -> shiftDay(1)
                    Nav.UP -> { month = Cal.monthOffsetOfDay(nowMs, dayOffset); monthMode = true }
                    Nav.DOWN, Nav.BACK -> zone = if (weatherView != null) Zone.WEATHER else Zone.DOCK
                    Nav.OK -> { dayDir = if (dayOffset > 0) -1 else 1; dayOffset = 0 }
                    else -> {}
                }
                Zone.DOCK -> when (key) {
                    Nav.LEFT -> selected = (selected - 1).coerceAtLeast(0)
                    Nav.RIGHT -> selected = (selected + 1).coerceAtMost(items.lastIndex)
                    Nav.UP -> if (weatherView != null) zone = Zone.WEATHER else if (calendarOn) zone = Zone.CALENDAR
                    Nav.OK -> items.getOrNull(selected)?.let(::open)
                    Nav.HOLD -> items.getOrNull(selected)?.let(::menuFor)?.let { menu = it; menuAt = 0 }
                    Nav.BACK -> selected = 0
                    Nav.DOWN -> {}
                }
            }
        }
    }

    val dock = Plates.dock(c)
    val board = Plates.rects(weatherView != null, calendarOn, Plates.boardTop(c), Plates.clockHeight(c))
    val looks = Plates.looks(c.plates, if (c.wallpaperOn) state.wall.current?.map else null, board)
    val before = state.wall.previous?.let { Plates.looks(c.plates, it.map, board) }
    val glass = c.glass && c.wallpaperOn

    Box(Modifier.fillMaxSize()) {
        Background(state.wall, c.wallpaperOn)
        // under Settings only the wallpaper shows (as in the webOS app); the rest stays put, ready for when Settings closes
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (state.screen == Screen.SETTINGS) 0f else 1f }) {
        Plate(board.clock, state.wall, looks.clock, before?.clock, glass, c.shadows) { ClockCard(now, c) }
        weatherView?.let { v ->
            Plate(board.weather, state.wall, looks.weather, before?.weather, glass, c.shadows, zone == Zone.WEATHER) {
                WeatherCard(v, c.cardSize / 100f, now, zone == Zone.WEATHER)
            }
        }
        if (calendarOn) {
            Plate(board.calendar, state.wall, looks.calendar, before?.calendar, glass, c.shadows, zone == Zone.CALENDAR) {
                CalendarCard(calendar, dayOffset, nowMs, c.clock24, zone == Zone.CALENDAR, monthMode, month, dayDir, c.cardSize / 100f, state.calendarLoading)
            }
        }
        Dock(
            items, selected, active && zone == Zone.DOCK && menu == null, fast, movingKey, dock, c.tileShape,
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = c.sideMargin.dp, end = c.sideMargin.dp, bottom = c.bottomMargin.dp)
                .height((dock.tileH + dock.labelBlock).dp),
        )
        if (moving != null) {
            Hint(tr("← → move the app, OK when done", "← → двигают приложение, OK — готово"), Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        menu?.let { list -> AppMenu(items.getOrNull(selected)?.label.orEmpty(), list.map { it.label }, menuAt) }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    BasicText(text, modifier.background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 8.dp), style = text(20f, 400, Hue.textSecondary))
}

/** The app's menu: a small panel in the middle of the screen. */
@Composable
private fun AppMenu(title: String, labels: List<String>, at: Int) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).background(Color(0xF0161616), RoundedCornerShape(32.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(title, Modifier.padding(start = 18.dp, bottom = 12.dp), style = text(30f, 500), maxLines = 1)
            labels.forEachIndexed { i, label ->
                BasicText(
                    label,
                    Modifier.fillMaxWidth().height(64.dp)
                        .background(if (i == at) Hue.textPrimary else Color.Transparent, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    style = text(26f, if (i == at) 500 else 400, if (i == at) Color.Black else Hue.textPrimary),
                )
            }
        }
    }
}
