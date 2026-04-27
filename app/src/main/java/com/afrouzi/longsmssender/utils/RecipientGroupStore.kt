package com.afrouzi.longsmssender.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecipientGroup(
    val name: String,
    val recipients: List<String>
)

class RecipientGroupStore(context: Context) {
    private val prefs = context.getSharedPreferences("recipient_groups", Context.MODE_PRIVATE)
    private val groupsKey = "groups_json"

    fun loadGroups(): List<RecipientGroup> {
        val raw = prefs.getString(groupsKey, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isEmpty()) continue
                    val recipientsArray = obj.optJSONArray("recipients") ?: JSONArray()
                    val recipients = buildList {
                        for (j in 0 until recipientsArray.length()) {
                            val value = recipientsArray.optString(j).trim()
                            if (value.isNotEmpty()) add(value)
                        }
                    }
                    add(RecipientGroup(name, recipients))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGroup(group: RecipientGroup) {
        val existing = loadGroups().toMutableList()
        val index = existing.indexOfFirst { it.name.equals(group.name, ignoreCase = true) }
        if (index >= 0) {
            existing[index] = group
        } else {
            existing.add(group)
        }
        persist(existing)
    }

    fun deleteGroup(name: String) {
        val updated = loadGroups().filterNot { it.name.equals(name, ignoreCase = true) }
        persist(updated)
    }

    private fun persist(groups: List<RecipientGroup>) {
        val array = JSONArray()
        groups.forEach { group ->
            val obj = JSONObject()
                .put("name", group.name)
                .put("recipients", JSONArray(group.recipients))
            array.put(obj)
        }
        prefs.edit().putString(groupsKey, array.toString()).apply()
    }
}
