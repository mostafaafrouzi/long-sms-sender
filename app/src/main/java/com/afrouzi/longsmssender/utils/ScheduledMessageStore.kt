package com.afrouzi.longsmssender.utils

import android.content.Context

object ScheduledMessageStore {
    private const val PREFS_NAME = "scheduled_messages"
    private const val PREFIX_DUE = "due_"
    private const val PREFIX_CONSUMED = "consumed_"

    fun register(context: Context, token: String, dueAtMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(PREFIX_DUE + token, dueAtMillis)
            .putBoolean(PREFIX_CONSUMED + token, false)
            .apply()
    }

    @Synchronized
    fun consumeIfPending(context: Context, token: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val due = prefs.getLong(PREFIX_DUE + token, -1L)
        if (due <= 0L) return false
        if (System.currentTimeMillis() + 1000L < due) return false
        if (prefs.getBoolean(PREFIX_CONSUMED + token, false)) return false
        prefs.edit().putBoolean(PREFIX_CONSUMED + token, true).commit()
        return true
    }

    fun clear(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX_DUE + token)
            .remove(PREFIX_CONSUMED + token)
            .apply()
    }
}
