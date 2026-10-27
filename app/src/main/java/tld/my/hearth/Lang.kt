package tld.my.hearth

// The language of the screens: English or Russian. AUTO follows the box's own language (Russian when the box is set to
// Russian, English otherwise). Every text is written where it is used, as a pair: tr("Today", "Сегодня").

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class LangSetting { AUTO, EN, RU }

object Lang {
    /** Russian on screen; a change redraws whatever read it */
    var ru by mutableStateOf(system())
        private set

    fun apply(setting: LangSetting) {
        ru = when (setting) { LangSetting.AUTO -> system(); LangSetting.EN -> false; LangSetting.RU -> true }
    }

    private fun system() = Locale.getDefault().language == "ru"

    /** The name of a language setting, in that language (so it can be found whatever the screen shows). */
    fun name(setting: LangSetting) = when (setting) { LangSetting.EN -> "English"; LangSetting.RU -> "Русский"; LangSetting.AUTO -> tr("Same as the box", "Как в системе") }
}

/** The text in the current language. */
fun tr(en: String, ru: String) = if (Lang.ru) ru else en

/** A number with its noun: count(3, "day", "days", "день", "дня", "дней") → "3 days" / "3 дня". */
fun count(n: Int, enOne: String, enMany: String, ruOne: String, ruFew: String, ruMany: String) =
    "$n " + if (Lang.ru) Texts.plural(n, ruOne, ruFew, ruMany) else if (Math.abs(n) == 1) enOne else enMany
