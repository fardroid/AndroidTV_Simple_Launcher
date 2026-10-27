package tld.my.hearth

// Fetching the weather, searching for places and reading the calendars. Blocking calls: run them on Dispatchers.IO.

import java.net.HttpURLConnection
import java.net.URL

object Net {
    fun fetchText(url: String, timeoutMs: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "SimpleTVLauncher/1.0 (Android TV)")
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }

    fun weather(city: City): WeatherData =
        Weather.parse(fetchText(Weather.forecastUrl(city.lat, city.lon)), city.lat, city.lon, System.currentTimeMillis())
            ?: throw java.io.IOException("unreadable reply")

    fun searchCities(query: String): List<City> =
        if (query.trim().length < 2) emptyList() else Weather.parseCities(fetchText(Weather.geocodeUrl(query)))

    /** Every calendar of the list, read one after another; one that cannot be read is left out (an error only when none can). */
    fun calendars(urls: List<String>, key: String): CalendarData {
        val now = System.currentTimeMillis()
        val parts = urls.mapNotNull { raw ->
            val url = Cal.normalizeUrl(raw)
            runCatching { Cal.parse(fetchText(url, 30_000), url, 0, now) }.getOrNull()
        }
        if (parts.isEmpty()) throw java.io.IOException("nothing could be read")
        return Cal.join(key, parts, now)
    }
}
