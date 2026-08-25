package com.przunk.kratkoza.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.przunk.kratkoza.domain.settings.AppLanguage
import java.util.Locale

/**
 * Applies the chosen app language (project.md section 4).
 *
 * Two paths, because the platform grew one halfway through the range this app supports:
 *
 *  - **API 33+** — [LocaleManager] holds a real per-app language. The system resolves resources,
 *    restarts the activity, remembers the choice across reinstalls of the *setting*, and shows it in
 *    Settings › Apps › Kratkoza › Language. Nothing here has to imitate any of that.
 *  - **API 29–32** — no such thing exists. The activity's base context is wrapped with an overridden
 *    locale in `attachBaseContext`, and the activity is recreated when the choice changes.
 *
 * androidx.appcompat would hide this difference, but it is a large dependency for one screen's worth
 * of behaviour, and technical_requirements.md section 9 asks a dependency to earn its place. This is
 * the whole of what it would have provided.
 */
object AppLocales {

    /** True when the app, not the system, has to carry the choice. */
    val handledByApp: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    /**
     * Hands the choice to the platform on API 33+. No-op below that, where [wrap] does the work.
     *
     * Safe to call with the value already set: the platform ignores a no-change write, so this does
     * not restart the activity in a loop.
     */
    fun applyToSystem(context: Context, language: AppLanguage) {
        if (handledByApp) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val desired = language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        if (manager.applicationLocales != desired) {
            manager.applicationLocales = desired
        }
    }

    /**
     * A context whose resources resolve in [language]. Returns [base] unchanged for
     * [AppLanguage.SYSTEM] and on API 33+, where the platform has already done it.
     */
    fun wrap(base: Context, language: AppLanguage): Context {
        if (!handledByApp) return base
        val tag = language.tag ?: return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    /** The language [context] is currently rendering in, as one of the app's own choices. */
    fun currentOf(context: Context): String =
        context.resources.configuration.locales[0].language
}
