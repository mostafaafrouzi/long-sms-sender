package com.afrouzi.longsmssender.utils

import android.content.Context
import android.os.Build

object AppVersionTracker {
    private const val PREFS = "app_version_tracker"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"

    fun shouldShowWhatsNew(context: Context): Boolean {
        val currentVersionCode = getCurrentVersionCode(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getLong(KEY_LAST_VERSION_CODE, -1L)
        return lastVersionCode in 1 until currentVersionCode
    }

    fun markCurrentVersionSeen(context: Context) {
        val currentVersionCode = getCurrentVersionCode(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
            .apply()
    }

    private fun getCurrentVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}
