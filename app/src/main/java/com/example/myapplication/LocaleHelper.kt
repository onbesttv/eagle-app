package com.example.myapplication

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    private const val PREF_KEY_LANG = "app_language"

    fun getSelectedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString(PREF_KEY_LANG, "pt") ?: "pt"
    }

    fun setLocale(context: Context, langTag: String) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_LANG, langTag).apply()

        val locale = Locale.forLanguageTag(langTag)
        Locale.setDefault(locale)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTag))
    }

    fun applyLocale(context: Context): Context {
        val langTag = getSelectedLanguage(context)
        val locale = Locale.forLanguageTag(langTag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
