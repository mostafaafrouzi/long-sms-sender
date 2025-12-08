package com.afrouzi.longsmssender.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LocaleManager {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val LANGUAGE_EN = "en"
    private const val LANGUAGE_FA = "fa"

    fun setLocale(context: Context, languageCode: String): Context {
        persistLanguage(context, languageCode)
        return updateResources(context, languageCode)
    }

    fun getLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val languageCode = prefs.getString(KEY_LANGUAGE, null) ?: getSystemLanguage(context)
        return Locale(languageCode)
    }

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, null) ?: getSystemLanguage(context)
    }

    fun getSystemLanguage(context: Context): String {
        val systemLocale = context.resources.configuration.locales[0]
        return when (systemLocale.language) {
            "fa", "ar" -> LANGUAGE_FA
            else -> LANGUAGE_EN
        }
    }

    private fun persistLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration
        
        // Set locale
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        
        // Set layout direction explicitly
        val isRtl = languageCode == "fa" || languageCode == "ar"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale)
        }

        return context.createConfigurationContext(configuration)
    }

    fun toggleLanguage(context: Context): String {
        val current = getCurrentLanguage(context)
        val newLanguage = if (current == LANGUAGE_FA) LANGUAGE_EN else LANGUAGE_FA
        return newLanguage
    }
}

