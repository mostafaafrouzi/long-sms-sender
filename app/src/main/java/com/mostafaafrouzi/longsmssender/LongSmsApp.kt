package com.mostafaafrouzi.longsmssender

import android.app.Application
import android.content.Context
import com.mostafaafrouzi.longsmssender.utils.LocaleManager
import com.mostafaafrouzi.longsmssender.utils.ThemeManager

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
