package tld.my.simpletv.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tld.my.simpletv.R

/** One colour theme (Settings → Appearance → Colour theme): the same table as Simple LG launcher's `themes.inc`.
 *  bg: under everything; start/mid/end: the plain background's gradient; text1-3: primary, secondary, muted; tile/tileFocus:
 *  the app tiles; accent: today, the weather curve, an event now; plate: the colour of a dark card; screen: Settings' background. */
class ThemeColors(
    val id: String, @StringRes val name: Int,
    val bg: Long, val start: Long, val mid: Long, val end: Long,
    val text1: Long, val text2: Long, val text3: Long,
    val tile: Long, val tileFocus: Long, val accent: Long, val plate: Long, val screen: Long,
)

object Themes {
    val ALL = listOf(
        ThemeColors("dusk", R.string.theme_dusk, 0xFF0A0D1F, 0xFF0B1636, 0xFF26357A, 0xFFA2506F, 0xFFF2F5F8, 0xFFB9C6D3, 0xFF7F90A1, 0xFF1B2142, 0xFF2A3260, 0xFFFFC36B, 0xFF0E1228, 0xFF08090E),
        ThemeColors("black", R.string.theme_black, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000, 0xFFF2F2F2, 0xFFA8A8A8, 0xFF6A6A6A, 0xFF101010, 0xFF1D1D1D, 0xFFFFC36B, 0xFF0A0A0A, 0xFF000000),
        ThemeColors("grey", R.string.theme_grey, 0xFF101A24, 0xFF1C2B3A, 0xFF25384C, 0xFF101A24, 0xFFF2F5F8, 0xFFB9C6D3, 0xFF7F90A1, 0xFF1A2733, 0xFF243546, 0xFF9FD0FF, 0xFF0F1A26, 0xFF0A1118),
        ThemeColors("forest", R.string.theme_forest, 0xFF08140F, 0xFF0D2A1D, 0xFF1F4D36, 0xFF0A1A12, 0xFFEEF5F0, 0xFFB3C9BB, 0xFF7A9585, 0xFF13261C, 0xFF1D3A2B, 0xFFA8E07A, 0xFF0C1F15, 0xFF07110C),
        ThemeColors("ocean", R.string.theme_ocean, 0xFF04121A, 0xFF062A3D, 0xFF0E5A70, 0xFF0A2233, 0xFFEDF8FB, 0xFFA9CBD6, 0xFF6F98A6, 0xFF0C2633, 0xFF123A4C, 0xFF5FDBE6, 0xFF071C28, 0xFF040E15),
        ThemeColors("sunset", R.string.theme_sunset, 0xFF160A0A, 0xFF2A0F1A, 0xFF8A3B2A, 0xFFD9853A, 0xFFFFF3EC, 0xFFE0BFAE, 0xFFA6857A, 0xFF2A1614, 0xFF3D201C, 0xFFFFB35C, 0xFF24100E, 0xFF120808),
        ThemeColors("lavender", R.string.theme_lavender, 0xFF110D1F, 0xFF1C1538, 0xFF4B3A8A, 0xFFB08AD6, 0xFFF5F0FF, 0xFFC8BEE6, 0xFF8D82AD, 0xFF1E1836, 0xFF2C2450, 0xFFD9BCFF, 0xFF171230, 0xFF0D0A18),
        ThemeColors("graphite", R.string.theme_graphite, 0xFF121212, 0xFF1B1B1D, 0xFF2C2C30, 0xFF141416, 0xFFF0F0F0, 0xFFB5B5B5, 0xFF7A7A7A, 0xFF1C1C1E, 0xFF2A2A2D, 0xFFFF9F0A, 0xFF141416, 0xFF0E0E10),
    )

    /** the chosen theme's id; a snapshot state, so whatever reads a colour from [Hue] redraws when it changes */
    var id by mutableStateOf("dusk")
    val current: ThemeColors get() = of(id)
    fun of(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
