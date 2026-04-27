package com.afrouzi.longsmssender.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PreparedMessage(
    val title: String,
    val body: String
)

class PreparedMessageStore(context: Context) {
    private val prefs = context.getSharedPreferences("prepared_messages", Context.MODE_PRIVATE)
    private val key = "templates_json"

    fun loadAll(): List<PreparedMessage> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val title = obj.optString("title").trim()
                    val body = obj.optString("body")
                    if (title.isEmpty()) continue
                    add(PreparedMessage(title, body))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(message: PreparedMessage) {
        val existing = loadAll().toMutableList()
        val index = existing.indexOfFirst { it.title.equals(message.title, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = message
        } else {
            existing.add(message)
        }
        persist(existing)
    }

    fun delete(title: String) {
        val updated = loadAll().filterNot { it.title.equals(title, ignoreCase = true) }
        persist(updated)
    }

    private fun persist(list: List<PreparedMessage>) {
        val array = JSONArray()
        list.forEach { m ->
            array.put(
                JSONObject()
                    .put("title", m.title)
                    .put("body", m.body)
            )
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
