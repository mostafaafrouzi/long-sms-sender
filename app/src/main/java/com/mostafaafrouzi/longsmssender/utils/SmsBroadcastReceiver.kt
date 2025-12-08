package com.mostafaafrouzi.longsmssender.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "SMS_SENT" -> {
                // Status is handled by MainActivity via viewModel.sendStatus
                // No Toast needed - Dialog will be shown instead
            }
            "SMS_DELIVERED" -> {
                // Delivery status is handled by MainActivity via status updates
                // No Toast needed - Dialog will be shown instead
            }
        }
    }
}
