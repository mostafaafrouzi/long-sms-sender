package com.afrouzi.longsmssender

import android.app.Application
import android.content.Context
import com.afrouzi.longsmssender.utils.LocaleManager
import com.afrouzi.longsmssender.utils.ThemeManager

class LongSmsApp : Application() {
    override fun attachBaseContext(base: Context) {
        val language = LocaleManager.getCurrentLanguage(base)
        super.attachBaseContext(LocaleManager.setLocale(base, language))
    }

    override fun onCreate() {
        super.onCreate()
        // Apply theme
        ThemeManager.applyTheme(this)
    }
}
