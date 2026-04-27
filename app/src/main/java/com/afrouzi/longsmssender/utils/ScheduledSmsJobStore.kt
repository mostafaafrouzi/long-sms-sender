package com.afrouzi.longsmssender.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledSmsJob(
    val token: String,
    val dueAtMillis: Long,
    val recipientsRaw: String,
    val message: String,
    val subscriptionId: Int?
)

class ScheduledSmsJobStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadJobs(): List<ScheduledSmsJob> {
        val raw = prefs.getString(KEY_JOBS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val token = obj.optString("token").trim()
                    if (token.isEmpty()) continue
                    val sub = obj.optInt("subscription_id", Int.MIN_VALUE)
                        .takeIf { it != Int.MIN_VALUE }
                    add(
                        ScheduledSmsJob(
                            token = token,
                            dueAtMillis = obj.optLong("due_at"),
                            recipientsRaw = obj.optString("recipients"),
                            message = obj.optString("message"),
                            subscriptionId = sub
                        )
                    )
                }
            }.sortedBy { it.dueAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsertJob(job: ScheduledSmsJob) {
        val current = loadJobs().filterNot { it.token == job.token }.toMutableList()
        current.add(job)
        persist(current)
    }

    fun removeJob(token: String) {
        val updated = loadJobs().filterNot { it.token == token }
        persist(updated)
    }

    private fun persist(jobs: List<ScheduledSmsJob>) {
        val array = JSONArray()
        jobs.forEach { job ->
            val obj = JSONObject()
                .put("token", job.token)
                .put("due_at", job.dueAtMillis)
                .put("recipients", job.recipientsRaw)
                .put("message", job.message)
            job.subscriptionId?.let { obj.put("subscription_id", it) }
            array.put(obj)
        }
        prefs.edit().putString(KEY_JOBS, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "scheduled_sms_jobs"
        private const val KEY_JOBS = "jobs_json"
    }
}
