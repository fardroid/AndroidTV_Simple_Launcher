package tld.my.simpletv.ui

// The home screen: the clock, weather and calendar cards on the frosted plates, and the row of apps. With the remote:
// ← → along the row, OK opens, OK held (or Menu) opens the app's menu, ↑ climbs to the weather card and from there to the
// calendar (← → change the day, ↑ the month, OK back to today), ↓ or Back returns. Back on the row goes to the first tile.

import tld.my.simpletv.R
import tld.my.simpletv.str
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
import tld.my.simpletv.Apps
import tld.my.simpletv.Cal
import tld.my.simpletv.FeedItem
import tld.my.simpletv.LauncherState
import tld.my.simpletv.Lang
import tld.my.simpletv.Plates
import tld.my.simpletv.Screen
import tld.my.simpletv.Weather
import java.time.Instant
import java.time.ZoneId

private enum class Zone { DOCK, WEATHER, CALENDAR, NEWS }

private data class MenuItem(val label: String, val run: () -> Unit)

@Composable
fun HomeScreen(state: LauncherState, keys: KeyTarget, active: Boolean) {
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
    /** the calendar: the remote is on its head (the date and its arrows) rather than on the day's events */
    var calHead by remember { mutableStateOf(false) }
    var calScroll by remember { mutableIntStateOf(0) }
    var calMax by remember { mutableIntStateOf(0) }
    var newsPage by remember { mutableIntStateOf(0) }
    var newsSel by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<FeedItem?>(null) }

    val nowMs = state.nowMs
    val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val weatherData = if (c.weatherOn) state.weatherFor(nowMs) else null
    // three columns of 560 px: the weather shows five days instead of seven
    val maxDays = if (c.newsOn && c.calendarOn && c.calendars.isNotEmpty()) 5 else 7
    val weatherView = remember(weatherData, c.fahrenheit, c.city, weatherDay, Lang.current, maxDays) {
        weatherData?.let { Weather.view(it, c.fahrenheit, c.city?.name.orEmpty(), weatherDay).let { v -> if (v.days.size > maxDays) v.copy(days = v.days.take(maxDays)) else v } }
    }
    val calendarOn = c.calendarOn && c.calendars.isNotEmpty()
    val calendar = if (calendarOn) state.calendarFor(nowMs) else null
    val newsOn = c.newsOn && state.newsSources().isNotEmpty()
    val news = if (newsOn) state.newsFor() else null
    val quote = if (c.quoteOn) state.quoteFor() else null

    // where the cards stand, and the row's geometry (for the way up from the selected tile)
    val dock = Plates.dock(c)
    val board = Plates.boardLayout(Plates.Cards(weatherView != null, calendarOn, newsOn, c.quoteOn, Plates.clockShape(c)), Plates.boardTop(c))
    val newsRect = board.rect(Plates.CardId.NEWS)
    val newsK = c.cardSize / 100f
    val (newsPer, newsPageCount) = newsPages(news?.items?.size ?: 0, (newsRect?.h ?: 686).toFloat(), newsK)

    // the home row: the apps in the user's order, then the launcher's own tiles
    val appTiles = remember(state.apps, c.appOrder, c.hidden) { Apps.ordered(state.apps, c).map { TileItem(it.pkg, it.label, it) } }
    val update = if (c.updateCheck) state.update else null
    val ownTiles = remember(Lang.current, update) {
        listOfNotNull(
            update?.let { TileItem(Apps.UPDATE, str(R.string.update_the_launcher), null, Glyph.DOWNLOAD, R.drawable.sys_update, Color(0xFF248A3D)) },
            TileItem(Apps.TV_SETTINGS, str(R.string.tv_settings), null, Glyph.TV, R.drawable.sys_gear, Color(0xFF636366)), TileItem(Apps.LAUNCHER_SETTINGS, str(R.string.launcher_settings), null, Glyph.GEAR, R.drawable.sys_launcher, Color(0xFF5245D0)),
        )
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
        calHead = false; calScroll = 0; newsPage = 0; newsSel = 0; detail = null
        if (state.homeReset) selected = 0
    }

    fun open(item: TileItem) {
        when (item.key) {
            Apps.LAUNCHER_SETTINGS -> state.screen = Screen.SETTINGS
            Apps.TV_SETTINGS -> Apps.openTvSettings(context)
            // the new version: its release over the screen, with the QR code of its page (nothing is downloaded here)
            Apps.UPDATE -> update?.let { detail = FeedItem(str(R.string.version_of_the_launcher_is_out, it.version), it.url, 0L, "Simple TV launcher", it.notes, -1) }
            else -> item.app?.let { Apps.launch(context, it) }
        }
    }

    fun menuFor(item: TileItem): List<MenuItem>? {
        val app = item.app ?: return null
        return listOf(
            MenuItem(str(R.string.move)) { moving = appTiles.map { it.key }; movingKey = item.key },
            MenuItem(str(R.string.hide_from_the_home_screen)) { state.hide(app.pkg) },
            MenuItem(str(R.string.app_info)) { Apps.openAppInfo(context, app.pkg) },
            MenuItem(str(R.string.uninstall)) { Apps.uninstall(context, app.pkg) },
        )
    }

    fun shiftDay(days: Int) {
        dayDir = if (days > 0) 1 else if (days < 0) -1 else 0
        dayOffset = (dayOffset + days).coerceIn(-Cal.BACK_DAYS, Cal.AHEAD_DAYS)
        calScroll = 0
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

    // ----- stepping between the cards (the rules of the webOS version's Home screen) -----
    fun enter(card: Plates.CardId?): Boolean {
        when (card) {
            Plates.CardId.WEATHER -> zone = Zone.WEATHER
            Plates.CardId.CALENDAR -> { calHead = false; zone = Zone.CALENDAR }
            Plates.CardId.NEWS -> { newsSel = newsPage * newsPer; zone = Zone.NEWS }
            else -> return false
        }
        return true
    }
    /** The card above, or (with nothing above) the card the remote steps onto in the next column on the right. */
    fun enterAbove(from: Plates.CardId) {
        if (enter(Plates.nextInColumn(board, from, -1))) return
        val index = board.columns.indexOfFirst { col -> col.cards.any { it.first == from } }
        for (i in index + 1 until board.columns.size) if (enter(Plates.entryCard(board, i))) return
    }
    fun leaveDown(from: Plates.CardId) { if (!enter(Plates.nextInColumn(board, from, 1))) zone = Zone.DOCK }
    fun leaveSide(from: Plates.CardId, step: Int) { enter(Plates.besideCard(board, from, step)) }
    fun selectedTileX(): Float {
        val step = dock.tileW + TILE_GAP
        val total = items.size * step - TILE_GAP
        val viewport = (1920 - 2 * c.sideMargin).toFloat()
        val off = if (total <= viewport) -(viewport - total) / 2f else (selected * step + dock.tileW / 2f - viewport / 2f).coerceIn(0f, total - viewport)
        return c.sideMargin + selected * step - off + dock.tileW / 2f
    }

    SideEffect {
        if (!active) return@SideEffect
        keys.handler = handler@{ key, repeat ->
            fast = repeat
            detail?.let {
                if (key == Nav.BACK || key == Nav.OK || key == Nav.HOLD) detail = null
                return@handler
            }
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
                Zone.WEATHER -> {
                    val days = weatherView?.days?.size ?: 1
                    val day = weatherDay.coerceAtMost(maxOf(0, days - 1))
                    when (key) {
                        // ← → choose the day; past the first or the last day they go on to the card beside
                        Nav.LEFT -> if (day > 0) weatherDay = day - 1 else leaveSide(Plates.CardId.WEATHER, -1)
                        Nav.RIGHT -> if (day < days - 1) weatherDay = day + 1 else leaveSide(Plates.CardId.WEATHER, 1)
                        Nav.UP -> enterAbove(Plates.CardId.WEATHER)
                        Nav.DOWN -> leaveDown(Plates.CardId.WEATHER)
                        Nav.BACK -> zone = Zone.DOCK
                        Nav.OK -> weatherDay = 0
                        else -> {}
                    }
                }
                // Two steps on the calendar: its body (the day's events, or the month's days), where ← → go on to the cards beside,
                // and above it the head (the date and its arrows), where ← → turn the days or the months.
                Zone.CALENDAR -> if (calHead) when (key) {
                    Nav.LEFT -> if (monthMode) shiftMonth(-1) else shiftDay(-1)
                    Nav.RIGHT -> if (monthMode) shiftMonth(1) else shiftDay(1)
                    Nav.DOWN -> calHead = false
                    Nav.UP -> if (!monthMode) { month = Cal.monthOffsetOfDay(nowMs, dayOffset); monthMode = true; calHead = false }
                    Nav.BACK -> if (monthMode) leaveMonth() else zone = Zone.DOCK
                    Nav.OK -> if (monthMode) leaveMonth() else { dayDir = if (dayOffset > 0) -1 else 1; dayOffset = 0; calScroll = 0 }
                    else -> {}
                } else when (key) {
                    Nav.LEFT -> leaveSide(Plates.CardId.CALENDAR, -1)
                    Nav.RIGHT -> leaveSide(Plates.CardId.CALENDAR, 1)
                    Nav.OK -> if (monthMode) leaveMonth() else { dayDir = if (dayOffset > 0) -1 else 1; dayOffset = 0; calScroll = 0 }
                    Nav.UP -> if (monthMode) calHead = true else if (calScroll > 0) calScroll-- else calHead = true
                    Nav.DOWN -> if (monthMode) { leaveMonth(); calHead = true } else if (calScroll < calMax) calScroll++ else leaveDown(Plates.CardId.CALENDAR)
                    Nav.BACK -> if (monthMode) { leaveMonth(); calHead = true } else zone = Zone.DOCK
                    else -> {}
                }
                Zone.NEWS -> {
                    val count = news?.items?.size ?: 0
                    val first = newsPage * newsPer
                    val last = minOf(count, first + newsPer) - 1
                    when (key) {
                        // ← → turn the pages; before the first or after the last they go on to the card beside
                        Nav.LEFT, Nav.RIGHT -> {
                            val step = if (key == Nav.LEFT) -1 else 1
                            val page = newsPage + step
                            if (page < 0 || page > newsPageCount - 1) leaveSide(Plates.CardId.NEWS, step) else { newsPage = page; newsSel = page * newsPer }
                        }
                        Nav.UP -> if (newsSel > first) newsSel-- else enterAbove(Plates.CardId.NEWS)
                        Nav.DOWN -> if (newsSel < last) newsSel++ else leaveDown(Plates.CardId.NEWS)
                        Nav.OK -> news?.items?.getOrNull(newsSel)?.let { detail = it }
                        Nav.BACK -> zone = Zone.DOCK
                        else -> {}
                    }
                }
                Zone.DOCK -> when (key) {
                    Nav.LEFT -> selected = (selected - 1).coerceAtLeast(0)
                    Nav.RIGHT -> selected = (selected + 1).coerceAtMost(items.lastIndex)
                    // up from the row climbs to the column above the selected tile
                    Nav.UP -> enter(Plates.entryCard(board, Plates.columnAt(board, selectedTileX())))
                    Nav.OK -> items.getOrNull(selected)?.let(::open)
                    Nav.HOLD -> items.getOrNull(selected)?.let(::menuFor)?.let { menu = it; menuAt = 0 }
                    Nav.BACK -> selected = 0
                    Nav.DOWN -> {}
                }
            }
        }
    }

    val look = Plates.looks(c.plates, if (c.wallpaperOn) state.wall.current?.map else null, board)
    val before = state.wall.previous?.let { Plates.looks(c.plates, it.map, board) }
    val glass = c.glass && c.wallpaperOn

    // the news turn their pages by themselves every 20 s while the remote is elsewhere
    LaunchedEffect(newsOn, zone == Zone.NEWS, detail == null, newsPageCount, newsPage) {
        if (newsOn && zone != Zone.NEWS && detail == null && newsPageCount > 1) {
            kotlinx.coroutines.delay(20_000)
            newsPage = (newsPage + 1) % newsPageCount
            newsSel = newsPage * newsPer
        }
    }

    // a card that has gone (switched off in Settings) cannot keep the focus
    LaunchedEffect(newsOn, weatherView != null, calendarOn) {
        if ((zone == Zone.NEWS && !newsOn) || (zone == Zone.WEATHER && weatherView == null) || (zone == Zone.CALENDAR && !calendarOn)) zone = Zone.DOCK
        if (!newsOn) detail = null
    }

    Box(Modifier.fillMaxSize()) {
        Background(state.wall, c.wallpaperOn)
        // under Settings only the wallpaper shows (as in the webOS app), the rest only while Settings peeks aside to show a change; the rest stays put, ready for when Settings closes
        // pixel shift moves the cards and the row a few pixels once a minute (Plates.shiftAt); the wallpaper stays
        val shift = if (c.pixelShift) Plates.shiftAt(nowMs) else 0 to 0
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = if (state.screen == Screen.SETTINGS) state.peek else 1f
            translationX = shift.first * density; translationY = shift.second * density
        }) {
        board.cards.forEach { (id, rect) ->
            when (id) {
                Plates.CardId.CLOCK -> Plate(rect, state.wall, look, before, glass, c.shadows) { ClockCard(now, c, board.narrow) }
                Plates.CardId.WEATHER -> weatherView?.let { v ->
                    Plate(rect, state.wall, look, before, glass, c.shadows, zone == Zone.WEATHER) { WeatherCard(v, c.cardSize / 100f, now, zone == Zone.WEATHER) }
                }
                Plates.CardId.CALENDAR -> Plate(rect, state.wall, look, before, glass, c.shadows, zone == Zone.CALENDAR) {
                    CalendarCard(
                        calendar, dayOffset, nowMs, c.clock24, zone == Zone.CALENDAR, monthMode, month, dayDir, c.cardSize / 100f, state.calendarLoading,
                        headFocus = calHead, scroll = calScroll, onMaxScroll = { calMax = it },
                    )
                }
                Plates.CardId.NEWS -> Plate(rect, state.wall, look, before, glass, c.shadows, zone == Zone.NEWS) {
                    NewsCard(news?.items, state.newsLoading, newsPage, newsSel, zone == Zone.NEWS && active, rect.h.toFloat(), newsK, nowMs)
                }
                Plates.CardId.QUOTE -> Plate(rect, state.wall, look, before, glass, c.shadows) { QuoteCard(quote, state.quoteLoading, rect.h.toFloat(), newsK) }
            }
        }
        Dock(
            items, selected, active && zone == Zone.DOCK && menu == null, fast, movingKey, dock, c.tileShape, c.labelsSelectedOnly,
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = c.sideMargin.dp, end = c.sideMargin.dp, bottom = c.bottomMargin.dp)
                .height((dock.tileH + dock.labelBlock).dp),
        )
        if (moving != null) {
            Hint(str(R.string.move_the_app_ok_when_done), Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        menu?.let { list -> AppMenu(items.getOrNull(selected)?.label.orEmpty(), list.map { it.label }, menuAt) }
        detail?.let { NewsDetail(it, nowMs) }
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
