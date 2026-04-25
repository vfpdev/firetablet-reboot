package de.hysight.firereboot

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-component locale override. Every Activity/Service that surfaces user-visible strings
 * must override `attachBaseContext` and pass the incoming context through [wrap], otherwise
 * the in-app language picker has no effect on that component (resources resolve against the
 * unmodified base context's configuration).
 */
object LocaleHelper {
    private const val PREFS = "settings"
    private const val KEY = "lang"

    fun savedLang(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun save(ctx: Context, lang: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lang == null) remove(KEY) else putString(KEY, lang)
        }.apply()
    }

    fun wrap(base: Context): Context {
        val lang = savedLang(base) ?: return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
