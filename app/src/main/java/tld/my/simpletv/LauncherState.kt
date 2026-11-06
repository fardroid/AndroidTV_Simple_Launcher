package tld.my.simpletv

// Everything the screens show, and the work that keeps it fresh: the app list, the slideshow, the weather and the calendars, and
// the minute clock. Work runs only while the launcher is on screen (resume/pause), so it never competes with an app.

import android.content.Context
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import tld.my.simpletv.ui.WallState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Screen { HOME, SETTINGS }

enum class UpdateStatus { LOOKING, LATEST, FOUND, FAILED }

class LauncherState(private val context: Context, private val scope: CoroutineScope) {
    private val store = Store(context)

    var config by mutableStateOf(store.loadConfig().also { Lang.apply(it.language, context); tld.my.simpletv.ui.Themes.id = it.theme })
        private set
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var weather by mutableStateOf(store.loadWeather())
        private set
    var calendar by mutableStateOf(store.loadCalendar())
        private set
    var calendarLoading by mutableStateOf(false)
    var news by mutableStateOf(store.loadNews())
        private set
    var newsLoading by mutableStateOf(false)
        private set
    var quote by mutableStateOf(store.loadQuote())
        private set
    var quoteLoading by mutableStateOf(false)
        private set
    /** a release newer than the one running (null: none known) */
    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    /** how the last look for a new version went: null while none was made this time */
    var updateStatus by mutableStateOf<UpdateStatus?>(null)
        private set
    /** counts the returns to the launcher: what the box says about it (is it the home app?) is read again then */
    var resumes by mutableStateOf(0)
        private set
    /** the time, to the minute (the screen redraws once a minute, not every second) */
    var nowMs by mutableLongStateOf(minute(System.currentTimeMillis()))
        private set
    var screen by mutableStateOf(Screen.HOME)
    /** 0..1: Settings steps aside to show a change on the home screen (its peek) */
    var peek by mutableFloatStateOf(0f)
    /** grows by one whenever the launcher is brought to the front with Home: the cards go back to today */
    var homePresses by mutableIntStateOf(0)
        private set
    /** the last Home press came while the launcher was already in front: the row goes back to its first tile too */
    var homeReset by mutableStateOf(false)
        private set
    val wall = WallState()
    /** grows when the user answers the question about the box's pictures (the gallery looks again) */
    var photosVersion by mutableIntStateOf(0)
        private set
    /** asks the system for the permission to read the box's pictures (set by the activity) */
    var requestPhotos: () -> Unit = {}
    fun photosAsked() { photosVersion++ }

    private var work: Job? = null

    init {
        // the saved result of the last look, shown until the next one
        Updates.fromJson(store.updateCheck)?.second?.takeIf { Updates.newer(it.version, BuildConfigInfo.version(context)) }?.let { update = it }
    }
    private var appsFor = ""
    private var lastSlideAt = 0L

    fun update(change: (Config) -> Config) {
        val next = change(config)
        if (next == config) return
        val before = config
        config = next
        store.saveConfig(next)
        if (before.language != next.language) Lang.apply(next.language, context)
        if (before.theme != next.theme) tld.my.simpletv.ui.Themes.id = next.theme
        if (before.iconScale != next.iconScale || before.tileShape != next.tileShape) reloadApps()
        if (before.city != next.city || before.weatherOn != next.weatherOn) scope.launch { refreshWeather(force = true) }
        if (before.calendars != next.calendars || before.calendarOn != next.calendarOn) scope.launch { refreshCalendar(force = true) }
        if (before.newsOn != next.newsOn || before.newsGoogle != next.newsGoogle || before.newsCountry != next.newsCountry || before.newsFeeds != next.newsFeeds || before.language != next.language) scope.launch { refreshNews(force = true) }
        if (!before.updateCheck && next.updateCheck) scope.launch { checkUpdate() }
        if (before.quoteOn != next.quoteOn || before.language != next.language) scope.launch { refreshQuote() }
        if (before.wallpaperOn != next.wallpaperOn || before.intervalSec != next.intervalSec || before.shuffle != next.shuffle || before.wallpaperPicks != next.wallpaperPicks) { pause(); resume() }
        if (before.frost != next.frost) refrost()
    }

    /** Makes the glass of the picture on screen again with the new frost (the next pictures are made with it anyway). */
    private fun refrost() {
        val id = wall.current?.id ?: return
        val (w, h) = screenSize()
        val frost = config.frost
        scope.launch(AndroidUiDispatcher.Main) {
            val slide = withContext(Dispatchers.Default) { Wallpapers.load(context, id, w, h, frost) } ?: return@launch
            if (config.frost == frost) wall.replace(slide)
        }
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
            launch { while (isActive) { delay(if (refreshNews()) 5 * 60_000L else 20_000L) } }
            launch { while (isActive) { delay(if (checkUpdate()) 60 * 60_000L else 10 * 60_000L) } }
            launch { while (isActive) { delay(if (refreshQuote()) 10 * 60_000L else 60_000L) } }
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
        val list = withContext(Dispatchers.IO) { Gallery.slideshow(context, config.wallpaperPicks) }
        if (list.isEmpty()) { wall.show(null); return }
        val (w, h) = screenSize()
        var index = store.wallpaperIndex.coerceIn(0, list.size - 1)
        if (wall.current == null || list.indexOf(wall.current?.id) == -1) {
            withContext(Dispatchers.Default) { Wallpapers.load(context, list[index], w, h, config.frost) }?.let { wall.show(it) }
            lastSlideAt = System.currentTimeMillis()
        } else index = list.indexOf(wall.current?.id)
        if (list.size < 2) return
        while (true) {
            // after coming back from an app the picture stays a little while: nothing changes the moment the screen returns
            val wait = max(config.intervalSec * 1000L - (System.currentTimeMillis() - lastSlideAt), 12_000L)
            delay(wait)
            index = if (config.shuffle) (list.indices - index).random() else (index + 1) % list.size
            val slide = withContext(Dispatchers.Default) { Wallpapers.load(context, list[index], w, h, config.frost) } ?: continue
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

    // ---------- a new version ----------

    /** Looks for a newer release: when it is on and a day has passed (or `force`: asked for in Settings). false when it could not be asked. */
    suspend fun checkUpdate(force: Boolean = false): Boolean {
        if (!force && !config.updateCheck) return true
        val now = System.currentTimeMillis()
        val saved = Updates.fromJson(store.updateCheck)
        if (!force && saved != null && now - saved.first < Updates.DAY_MS) return true
        if (force) updateStatus = UpdateStatus.LOOKING
        val release = withContext(Dispatchers.IO) { runCatching { Net.latestRelease() } }
        if (release.isFailure) { if (force) updateStatus = UpdateStatus.FAILED; return false }
        val newer = release.getOrNull()?.takeIf { Updates.newer(it.version, BuildConfigInfo.version(context)) }
        store.updateCheck = Updates.toJson(newer, now)
        update = newer
        if (force) updateStatus = if (newer != null) UpdateStatus.FOUND else UpdateStatus.LATEST
        return true
    }

    fun checkUpdateNow() { scope.launch { checkUpdate(force = true) } }

    // ---------- news and the quote of the day ----------

    /** Google News (when on) first, then the user's own feeds. */
    fun newsSources(c: Config = config): List<String> {
        val google = if (c.newsGoogle) listOf(Feeds.googleNewsUrl(Feeds.countryOf(c.newsCountry, boxTag(), Lang.current), Lang.current)) else emptyList()
        return google + c.newsFeeds.map { Feeds.normalizeUrl(it) }
    }

    /** The country of the box ("de-DE"): the country of the news follows it until one is chosen. */
    fun boxTag(): String = runCatching { android.content.res.Resources.getSystem().configuration.locales[0].toLanguageTag() }.getOrDefault("")

    private fun newsKey() = newsSources().joinToString("\n")

    /** false when the news is on but could not be read: the caller tries again soon */
    private suspend fun refreshNews(force: Boolean = false): Boolean {
        val sources = newsSources()
        if (!config.newsOn || sources.isEmpty()) return true
        val key = sources.joinToString("\n")
        if (!force && Feeds.isFresh(news, key, System.currentTimeMillis())) return true
        newsLoading = true
        try {
            val data = withContext(Dispatchers.IO) { runCatching { Net.news(sources, key) }.getOrNull() } ?: return false
            news = data
            store.saveNews(data)
            return true
        } finally {
            newsLoading = false
        }
    }

    fun newsFor(): NewsData? = Feeds.usable(news, newsKey())

    /** false when the quote could not be fetched: the caller tries again soon. Today's saved quote is not fetched again. */
    private suspend fun refreshQuote(): Boolean {
        if (!config.quoteOn) return true
        val lang = Lang.current
        val now = System.currentTimeMillis()
        if (Quotes.todays(quote, lang.code, now) != null) return true
        quoteLoading = true
        try {
            val q = withContext(Dispatchers.IO) { runCatching { Net.quote(lang, store, now) }.getOrNull() } ?: return false
            quote = q
            store.saveQuote(q)
            return true
        } finally {
            quoteLoading = false
        }
    }

    /** today's quote, or the last one (of this language) until today's is in */
    fun quoteFor(): Quote? = Quotes.shown(quote, Lang.current.code)

    fun weatherFor(now: Long): WeatherData? = config.city?.let { Weather.usable(weather, it, now) }

    fun calendarFor(now: Long): CalendarData? = Cal.usable(calendar, calendarKey(), now)
}
