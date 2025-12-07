package com.mostafaafrouzi.longsmssender.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast
import com.mostafaafrouzi.longsmssender.R

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "SMS_SENT" -> {
                val partIndex = intent.getIntExtra("part_index", -1)
                val totalParts = intent.getIntExtra("total_parts", 1)
                
                // Only show Toast for the last part to avoid spam
                // Note: Main status is now shown via Snackbar in MainActivity
                // Only show critical errors here
                if (partIndex == totalParts - 1 || partIndex == -1) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            // Success is handled by MainActivity via status updates
                            // No Toast needed to reduce spam
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Toast.makeText(context, context.getString(R.string.sms_error_generic_failure), Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Toast.makeText(context, context.getString(R.string.sms_error_no_service), Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_NULL_PDU -> {
                            Toast.makeText(context, context.getString(R.string.sms_error_null_pdu), Toast.LENGTH_SHORT).show()
                        }
                        SmsManager.RESULT_ERROR_RADIO_OFF -> {
                            Toast.makeText(context, context.getString(R.string.sms_error_radio_off), Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            Toast.makeText(context, context.getString(R.string.sms_error_unknown), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            "SMS_DELIVERED" -> {
                val partIndex = intent.getIntExtra("part_index", -1)
                val totalParts = intent.getIntExtra("total_parts", 1)
                
                // Only show Toast for the last part to avoid spam
                if (partIndex == totalParts - 1 || partIndex == -1) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            Toast.makeText(context, context.getString(R.string.sms_delivered), Toast.LENGTH_SHORT).show()
                        }
                        Activity.RESULT_CANCELED -> {
                            Toast.makeText(context, context.getString(R.string.sms_not_delivered), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
