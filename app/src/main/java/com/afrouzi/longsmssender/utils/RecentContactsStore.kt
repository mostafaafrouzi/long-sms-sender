package com.afrouzi.longsmssender.utils

import android.content.Context

class RecentContactsStore(context: Context) {
    private val prefs = context.getSharedPreferences("recent_contacts", Context.MODE_PRIVATE)
    private val key = "recent_ids"
    private val delimiter = "|"

    fun loadRecentIds(): List<String> {
        return prefs.getString(key, "")
            .orEmpty()
            .split(delimiter)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun saveRecentSelection(selectedIds: Collection<String>, maxSize: Int = 100) {
        if (selectedIds.isEmpty()) return
        val merged = LinkedHashSet<String>()
        selectedIds.forEach { merged.add(it) }
        loadRecentIds().forEach { merged.add(it) }
        val capped = merged.take(maxSize)
        prefs.edit().putString(key, capped.joinToString(delimiter)).apply()
    }
}
