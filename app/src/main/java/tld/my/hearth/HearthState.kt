package tld.my.hearth

// Everything the screens show, and the work that keeps it fresh: the app list, the slideshow, the weather and the calendars, and
// the minute clock. Work runs only while the launcher is on screen (resume/pause), so it never competes with an app.

import android.content.Context
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tld.my.hearth.ui.WallState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Screen { HOME, SETTINGS }

class HearthState(private val context: Context, private val scope: CoroutineScope) {
    private val store = Store(context)

    var config by mutableStateOf(store.loadConfig().also { Lang.apply(it.language) })
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var weather by mutableStateOf(store.loadWeather())
        private set
    var calendar by mutableStateOf(store.loadCalendar())
        private set
    var calendarLoading by mutableStateOf(false)
    /** counts the returns to the launcher: what the box says about it (is it the home app?) is read again then */
    var resumes by mutableStateOf(0)
        private set
    /** the time, to the minute (the screen redraws once a minute, not every second) */
    var nowMs by mutableLongStateOf(minute(System.currentTimeMillis()))
        private set
    var screen by mutableStateOf(Screen.HOME)
    /** grows by one whenever the launcher is brought to the front with Home: the cards go back to today */
    var homePresses by mutableIntStateOf(0)
        private set
    /** the last Home press came while the launcher was already in front: the row goes back to its first tile too */
    var homeReset by mutableStateOf(false)
        private set
    val wall = WallState()

    private var work: Job? = null
    private var appsFor = ""
    private var lastSlideAt = 0L

    fun update(change: (Config) -> Config) {
        val next = change(config)
        if (next == config) return
        val before = config
        config = next
        store.saveConfig(next)
        if (before.language != next.language) Lang.apply(next.language)
        if (before.iconScale != next.iconScale || before.tileShape != next.tileShape) reloadApps()
        if (before.city != next.city || before.weatherOn != next.weatherOn) scope.launch { refreshWeather(force = true) }
        if (before.calendars != next.calendars || before.calendarOn != next.calendarOn) scope.launch { refreshCalendar(force = true) }
        if (before.wallpaperOn != next.wallpaperOn || before.intervalSec != next.intervalSec || before.shuffle != next.shuffle) { pause(); resume() }
    }

    fun homePressed(whileShown: Boolean) {
        screen = Screen.HOME
        homeReset = whileShown
        homePresses++
    }

    // ---------- on screen / off screen ----------

    fun resume() {
        resumes++
        nowMs = minute(System.currentTimeMillis())
        reloadApps()
        work?.cancel()
        // on Compose's main dispatcher: it carries the frame clock the wallpaper's cross-fade runs on
        work = scope.launch(AndroidUiDispatcher.Main) {
            launch { tickClock() }
            launch { slideshow() }
            // every 5 minutes; while there is nothing to show yet (the box still joining the network), again after 20 s
            launch { while (isActive) { delay(if (refreshWeather()) 5 * 60_000L else 20_000L) } }
            launch { while (isActive) { delay(if (refreshCalendar()) 5 * 60_000L else 20_000L) } }
        }
    }

    fun pause() {
        work?.cancel()
        work = null
    }

    // ---------- apps ----------

    /** Pixel sizes of the tile pictures: the design size times the screen's scale, with room for the selected tile's growth. */
    private fun pictureSizes(): Triple<Int, Int, Int> {
        val scale = context.resources.displayMetrics.widthPixels / 1920f
        val d = Plates.dock(config)
        val h = (d.tileH * scale * Plates.FOCUS_SCALE).roundToInt()
        return Triple(h, (h * 16f / 9f).roundToInt(), h)
    }

    /** Reads the app list again when the tile size changed or `force` (an app was installed, updated or removed). */
    fun reloadApps(force: Boolean = false) {
        val sizes = pictureSizes()
        val key = sizes.toString()
        if (!force && key == appsFor && apps.isNotEmpty()) return
        appsFor = key
        scope.launch {
            val list = withContext(Dispatchers.IO) { Apps.load(context, sizes.first, sizes.second, sizes.third) }
            apps = list
        }
    }

    fun setOrder(packages: List<String>) = update { it.copy(appOrder = packages) }

    fun hide(pkg: String) = update { it.copy(hidden = it.hidden + pkg) }

    // ---------- clock ----------

    private fun minute(ms: Long) = ms - ms % 60_000L

    private suspend fun tickClock() {
        while (true) {
            val now = System.currentTimeMillis()
            nowMs = minute(now)
            delay(60_000L - now % 60_000L + 50)
        }
    }

    // ---------- slideshow ----------

    private fun screenSize(): Pair<Int, Int> {
        val m = context.resources.displayMetrics
        val w = min(max(m.widthPixels, m.heightPixels), 1920)
        return w to (w * 9 / 16)
    }

    private suspend fun slideshow() {
        if (!config.wallpaperOn) { wall.show(null); return }
        val list = withContext(Dispatchers.IO) { Wallpapers.list(context) }
        if (list.isEmpty()) { wall.show(null); return }
        val (w, h) = screenSize()
        var index = store.wallpaperIndex.coerceIn(0, list.size - 1)
        if (wall.current == null || list.indexOf(wall.current?.id) == -1) {
            withContext(Dispatchers.Default) { Wallpapers.load(context, list[index], w, h) }?.let { wall.show(it) }
            lastSlideAt = System.currentTimeMillis()
        } else index = list.indexOf(wall.current?.id)
        if (list.size < 2) return
        while (true) {
            // after coming back from an app the picture stays a little while: nothing changes the moment the screen returns
            val wait = max(config.intervalSec * 1000L - (System.currentTimeMillis() - lastSlideAt), 12_000L)
            delay(wait)
            index = if (config.shuffle) (list.indices - index).random() else (index + 1) % list.size
            val slide = withContext(Dispatchers.Default) { Wallpapers.load(context, list[index], w, h) } ?: continue
            store.wallpaperIndex = index
            lastSlideAt = System.currentTimeMillis()
            wall.show(slide)
        }
    }

    // ---------- weather and calendar ----------

    /** false when the weather is on but could not be read (and none fresh is kept): the caller tries again soon */
    private suspend fun refreshWeather(force: Boolean = false): Boolean {
        val city = config.city ?: return true
        if (!config.weatherOn) return true
        val now = System.currentTimeMillis()
        if (!force && Weather.isFresh(weather, city, now)) return true
        val data = withContext(Dispatchers.IO) { runCatching { Net.weather(city) }.getOrNull() } ?: return false
        weather = data
        store.saveWeather(data)
        return true
    }

    fun refreshCalendarNow() { scope.launch { refreshCalendar(force = true) } }

    private fun calendarKey() = config.calendars.joinToString("\n")

    /** false when the calendars are on but could not be read: the caller tries again soon */
    private suspend fun refreshCalendar(force: Boolean = false): Boolean {
        if (!config.calendarOn || config.calendars.isEmpty()) return true
        val key = calendarKey()
        if (!force && Cal.isFresh(calendar, key, System.currentTimeMillis())) return true
        calendarLoading = true
        try {
            val data = withContext(Dispatchers.IO) { runCatching { Net.calendars(config.calendars, key) }.getOrNull() } ?: return false
            calendar = data
            store.saveCalendar(data)
            return true
        } finally {
            calendarLoading = false
        }
    }

    fun weatherFor(now: Long): WeatherData? = config.city?.let { Weather.usable(weather, it, now) }

    fun calendarFor(now: Long): CalendarData? = Cal.usable(calendar, calendarKey(), now)
}
