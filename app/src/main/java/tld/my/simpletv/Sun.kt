package tld.my.simpletv

// Day or night, for dimming the wallpaper at night: the sun's height over a place, worked out on the box (no request), and how far
// into the night it is. A port of Simple LG launcher's data/sun.cpp.

import java.util.Calendar
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Sun {
    private const val RAD = Math.PI / 180.0

    /** The sun's height over the horizon, in degrees, at `ms` (since 1970, UTC) at this latitude and longitude (the Astronomical
     *  Almanac's low-precision formulas: right to about a hundredth of a degree). */
    fun elevation(ms: Long, lat: Double, lon: Double): Double {
        val d = ms / 86_400_000.0 + 2440587.5 - 2451545.0          // days since 1 January 2000, 12:00 UTC
        val g = (357.529 + 0.98560028 * d) * RAD                    // the sun's mean anomaly
        val q = 280.459 + 0.98564736 * d                            // its mean longitude
        val l = (q + 1.915 * sin(g) + 0.020 * sin(2 * g)) * RAD
        val e = (23.439 - 0.00000036 * d) * RAD                     // the tilt of the Earth's axis
        val ra = atan2(cos(e) * sin(l), cos(l))
        val decl = asin(sin(e) * sin(l))
        val gmst = 18.697374558 + 24.06570982441908 * d             // sidereal time at Greenwich, hours
        val hourAngle = (gmst * 15 + lon) * RAD - ra
        val h = sin(lat * RAD) * sin(decl) + cos(lat * RAD) * cos(decl) * cos(hourAngle)
        return asin(h.coerceIn(-1.0, 1.0)) / RAD
    }

    /** 0 by day, 1 at night, and in between while it gets dark or light (the sun from the horizon down to 6° under it: civil
     *  twilight, about half an hour). */
    fun nightLevel(elevation: Double): Double = (-elevation / 6.0).coerceIn(0.0, 1.0)

    /** The same without a place: night from 19:00 to 7:00 by the box's clock. */
    fun nightLevelByClock(ms: Long): Double {
        val hour = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
        return if (hour >= 19 || hour < 7) 1.0 else 0.0
    }

    /** How much darker the picture is drawn (0 to 0.7) under these settings at `ms`; the plain background is never dimmed. */
    fun dim(c: Config, ms: Long): Float {
        if (!c.wallpaperOn || c.dim == DimMode.OFF) return 0f
        val level = when {
            c.dim == DimMode.ALWAYS -> 1.0
            c.city != null -> nightLevel(elevation(ms, c.city.lat, c.city.lon))
            else -> nightLevelByClock(ms)
        }
        return (Math.round(c.dimStrength * level) / 100.0).toFloat()
    }
}
