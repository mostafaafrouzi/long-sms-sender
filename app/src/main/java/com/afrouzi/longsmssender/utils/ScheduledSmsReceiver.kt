package com.afrouzi.longsmssender.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.afrouzi.longsmssender.data.repository.SmsRepository

class ScheduledSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ScheduledSmsReceiver", "onReceive action=${intent.action}")
        val token = intent.getStringExtra(EXTRA_SCHEDULE_TOKEN).orEmpty()
        if (token.isBlank()) {
            Log.w("ScheduledSmsReceiver", "Missing schedule token")
            return
        }
        if (!ScheduledMessageStore.consumeIfPending(context, token)) {
            Log.d("ScheduledSmsReceiver", "Token already consumed or not due yet token=$token")
            return
        }

        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val recipientsRaw = intent.getStringExtra(EXTRA_RECIPIENTS).orEmpty()
        val subscriptionId = intent.getIntExtra(EXTRA_SIM_SUBSCRIPTION_ID, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        if (message.isBlank() || recipientsRaw.isBlank()) return

        val recipients = recipientsRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (recipients.isEmpty()) return

        Log.d("ScheduledSmsReceiver", "Sending scheduled SMS token=$token recipients=${recipients.size}")
        val repository = SmsRepository(context)
        recipients.forEach { number ->
            repository.sendSms(number, message, subscriptionId)
        }
        ScheduledMessageStore.clear(context, token)
        ScheduledSmsJobStore(context).removeJob(token)
        Log.d("ScheduledSmsReceiver", "Completed scheduled SMS token=$token")
    }

    companion object {
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_RECIPIENTS = "extra_recipients"
        const val EXTRA_SIM_SUBSCRIPTION_ID = "extra_sim_subscription_id"
        const val EXTRA_SCHEDULE_TOKEN = "extra_schedule_token"
    }
}
