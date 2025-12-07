package com.mostafaafrouzi.longsmssender

import android.app.Application
import android.content.Context
import com.mostafaafrouzi.longsmssender.utils.LocaleManager

class LongSmsApp : Application() {
    override fun attachBaseContext(base: Context) {
        val language = LocaleManager.getCurrentLanguage(base)
        super.attachBaseContext(LocaleManager.setLocale(base, language))
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize things here if needed
    }
}
