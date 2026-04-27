package com.afrouzi.longsmssender.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afrouzi.longsmssender.data.repository.SmsRepository

class ScheduledSmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d("ScheduledSmsWorker", "doWork start")
        val token = inputData.getString(KEY_TOKEN).orEmpty()
        if (token.isBlank() || !ScheduledMessageStore.consumeIfPending(applicationContext, token)) {
            Log.d("ScheduledSmsWorker", "Skip token=$token (blank/consumed/not due)")
            return Result.success()
        }

        val message = inputData.getString(KEY_MESSAGE).orEmpty()
        val recipientsRaw = inputData.getString(KEY_RECIPIENTS).orEmpty()
        val subscriptionId = inputData.getInt(KEY_SUBSCRIPTION_ID, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }

        if (message.isBlank() || recipientsRaw.isBlank()) {
            ScheduledMessageStore.clear(applicationContext, token)
            return Result.failure()
        }

        val recipients = recipientsRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (recipients.isEmpty()) {
            ScheduledMessageStore.clear(applicationContext, token)
            return Result.failure()
        }

        val repository = SmsRepository(applicationContext)
        recipients.forEach { number ->
            repository.sendSms(number, message, subscriptionId)
        }
        ScheduledMessageStore.clear(applicationContext, token)
        ScheduledSmsJobStore(applicationContext).removeJob(token)
        Log.d("ScheduledSmsWorker", "Completed fallback scheduled SMS token=$token")
        return Result.success()
    }

    companion object {
        const val KEY_TOKEN = "token"
        const val KEY_MESSAGE = "message"
        const val KEY_RECIPIENTS = "recipients"
        const val KEY_SUBSCRIPTION_ID = "subscription_id"
    }
}
