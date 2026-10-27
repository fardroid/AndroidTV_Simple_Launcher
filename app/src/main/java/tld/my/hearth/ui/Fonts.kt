package tld.my.hearth.ui

// The typefaces of the widgets (the clock, the date, the weather and the calendar cards). A port of src/lib/clockfont.ts:
// the same faces as the webOS app, from res/font (full Google Fonts files, Cyrillic included); "TV font" is the box's own.
// Inter, Oswald, Roboto Mono and Cormorant are variable fonts, set to the weight asked for.

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import tld.my.hearth.R
import tld.my.hearth.tr

/** `cap`: the height of the digits, as a share of the font size (the clock is centred by it) */
data class WidgetFontInfo(val id: String, val en: String, val ru: String, val family: FontFamily, val cap: Float) {
    val label get() = tr(en, ru)
}

object Fonts {
    /** the weights Settings offers (Thin to Bold) */
    val WEIGHTS = listOf(200, 300, 400, 500, 600)

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, lightest: Int = 200) = FontFamily(WEIGHTS.map { w ->
        val at = maxOf(w, lightest)
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(at)))
    })

    val ALL = listOf(
        WidgetFontInfo("inter", "Inter (default)", "Inter (по умолчанию)", variable(R.font.inter), 0.727f),
        WidgetFontInfo("tv", "TV font", "Шрифт ТВ", FontFamily.SansSerif, 0.711f),
        WidgetFontInfo("poppins", "Poppins (rounded)", "Poppins (округлый)", FontFamily(
            Font(R.font.poppins_200, FontWeight(200)), Font(R.font.poppins_300, FontWeight(300)), Font(R.font.poppins_400, FontWeight(400)),
            Font(R.font.poppins_500, FontWeight(500)), Font(R.font.poppins_600, FontWeight(600)),
        ), 0.698f),
        WidgetFontInfo("oswald", "Oswald (narrow)", "Oswald (узкий)", variable(R.font.oswald), 0.812f),
        WidgetFontInfo("mono", "Roboto Mono (digital)", "Roboto Mono (цифровой)", variable(R.font.roboto_mono, 100), 0.71f),
        WidgetFontInfo("serif", "Cormorant (elegant serif)", "Cormorant (изящный с засечками)", variable(R.font.cormorant, 300), 0.63f),
    )

    fun of(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/** The font the widgets use now (Settings → Widgets → Font); MainActivity keeps it in step with the settings. */
object WidgetFont {
    var id by mutableStateOf("inter")
}

/** The widgets' text: as `text`, in the chosen font. Read during composition, so the cards redraw when the font changes. */
fun wtext(size: Float, weight: Int = 400, color: Color = Hue.textPrimary, lineHeight: Float = 1.2f, spacing: TextUnit = TextUnit.Unspecified): TextStyle =
    text(size, weight, color, lineHeight, spacing).copy(fontFamily = Fonts.of(WidgetFont.id).family)
