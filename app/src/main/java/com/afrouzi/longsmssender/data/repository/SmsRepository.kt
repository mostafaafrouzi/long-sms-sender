package com.afrouzi.longsmssender.data.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.afrouzi.longsmssender.data.model.SmsResult

class SmsRepository(private val context: Context) {

    private val smsManager: SmsManager by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }

    private var requestCodeCounter = 0
        get() = field++

    fun sendSms(
        destinationAddress: String,
        message: String
    ): SmsResult {
        return try {
            val parts = smsManager.divideMessage(message)
            
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                
                for (i in parts.indices) {
                    val sentRequestCode = requestCodeCounter
                    val deliveryRequestCode = requestCodeCounter
                    
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        sentRequestCode,
                        Intent("SMS_SENT").apply {
                            putExtra("request_code", sentRequestCode)
                            putExtra("destination", destinationAddress)
                            putExtra("part_index", i)
                            putExtra("total_parts", parts.size)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val deliveryIntent = PendingIntent.getBroadcast(
                        context,
                        deliveryRequestCode,
                        Intent("SMS_DELIVERED").apply {
                            putExtra("request_code", deliveryRequestCode)
                            putExtra("destination", destinationAddress)
                            putExtra("part_index", i)
                            putExtra("total_parts", parts.size)
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    sentIntents.add(sentIntent)
                    deliveryIntents.add(deliveryIntent)
                }
                
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                val sentRequestCode = requestCodeCounter
                val deliveryRequestCode = requestCodeCounter
                
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    sentRequestCode,
                    Intent("SMS_SENT").apply {
                        putExtra("request_code", sentRequestCode)
                        putExtra("destination", destinationAddress)
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                
                val deliveryIntent = PendingIntent.getBroadcast(
                    context,
                    deliveryRequestCode,
                    Intent("SMS_DELIVERED").apply {
                        putExtra("request_code", deliveryRequestCode)
                        putExtra("destination", destinationAddress)
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
                
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    message,
                    sentIntent,
                    deliveryIntent
                )
            }
            
            SmsResult.Success
        } catch (e: SecurityException) {
            SmsResult.Error("SMS permission denied")
        } catch (e: Exception) {
            SmsResult.Error(e.message ?: "Unknown error sending SMS")
        }
    }

    fun calculateLength(message: String): IntArray {
        // Returns: [msgCount, codeUnitCount, codeUnitsRemaining, codeUnitSize]
        // msgCount = number of SMS segments
        return SmsManager.getDefault().divideMessage(message).let { parts ->
             // This is a simplified calculation because divideMessage doesn't give remaining chars directly easily
             // without using the internal logic. 
             // But SmsMessage.calculateLength() is better for UI.
             // However, calculateLength is static on SmsMessage.
             // Let's use a wrapper in ViewModel or here.
             intArrayOf(parts.size, 0, 0, 0) // Placeholder, we will use SmsMessage in ViewModel
        }
    }
}
