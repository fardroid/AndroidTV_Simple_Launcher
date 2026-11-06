package tld.my.simpletv

// The language of the screens. The texts are Android resources (res/values*/strings.xml, by id, with <plurals> for counted
// nouns); the launcher's own choice of language is a locale of its own: the app's resources are opened again with that
// locale (createConfigurationContext), whatever the box is set to. AUTO follows the box's own language (English when the
// box speaks a language the launcher does not have).

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** `native`: the language's name in itself (so it can be found whatever the screen shows); `tag`: the BCP 47 tag for the
 *  resources, the dates and the weather service's place search. */
enum class Language(val native: String, val tag: String) {
    EN("English", "en"),
    RU("Русский", "ru"),
    ES("Español", "es"),
    FR("Français", "fr"),
    DE("Deutsch", "de"),
    IT("Italiano", "it"),
    PT("Português", "pt"),
    TR("Türkçe", "tr"),
    UK("Українська", "uk"),
    ZH("简体中文", "zh-CN"),
    JA("日本語", "ja"),
    KO("한국어", "ko"),
    PL("Polski", "pl");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val code get() = tag.substringBefore('-')

    companion object {
        /** The launcher's language for a language code such as "pt" or "zh" (English when it has none). */
        fun of(code: String): Language = entries.firstOrNull { it.code == code.lowercase() } ?: EN
    }
}

/** AUTO, or one of the languages (the same names as Language, so a saved choice reads back). */
enum class LangSetting {
    AUTO, EN, RU, ES, FR, DE, IT, PT, TR, UK, ZH, JA, KO, PL;
    val language: Language? get() = if (this == AUTO) null else Language.valueOf(name)
}

/** Where the texts come from: the app's resources in the chosen locale (the unit tests read the strings.xml files instead). */
interface TextSource {
    fun string(@StringRes id: Int): String
    fun plural(@PluralsRes id: Int, n: Int): String
}

private class ResourceTexts(private val res: Resources) : TextSource {
    override fun string(id: Int) = res.getString(id)
    override fun plural(id: Int, n: Int) = res.getQuantityString(id, n, n)
}

object Lang {
    /** The language on screen; a change redraws whatever read it */
    var current by mutableStateOf(system())
        private set

    val ru get() = current == Language.RU

    var texts: TextSource? = null
        private set

    /** Switches the language. `context` opens the resources in its locale; without one (the unit tests) `source` is used. */
    fun apply(setting: LangSetting, context: Context? = null, source: TextSource? = null) {
        current = setting.language ?: system()
        texts = source ?: context?.let { ResourceTexts(localized(it, setting.language?.locale ?: systemLocale())) } ?: texts
    }

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    private fun system() = Language.of(runCatching { systemLocale().language }.getOrDefault(Locale.getDefault().language))

    private fun localized(context: Context, locale: Locale): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config).resources
    }

    /** The name of a language setting, in that language (so it can be found whatever the screen shows). */
    fun name(setting: LangSetting) = setting.language?.native ?: str(R.string.same_as_the_box)
}

/** The text in the current language; `args` fill its %1$s, %2$s… */
fun str(@StringRes id: Int, vararg args: Any): String {
    val lang = Lang.current      // read, so a change of language redraws whoever asked
    val text = Lang.texts?.string(id) ?: return ""
    return if (args.isEmpty()) text else String.format(lang.locale, text, *args)
}

/** A number with its noun in the current language: plural(R.plurals.days, 3) → "3 days" / "3 дня". */
fun plural(@PluralsRes id: Int, n: Int): String {
    Lang.current
    return Lang.texts?.plural(id, n) ?: n.toString()
}
