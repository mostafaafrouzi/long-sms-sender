package com.afrouzi.longsmssender.data.repository

import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.afrouzi.longsmssender.data.model.SmsResult
import java.util.concurrent.atomic.AtomicInteger

data class SimOption(
    val subscriptionId: Int?,
    val displayName: String
)

class SmsRepository(private val context: Context) {

    private val smsManager: SmsManager by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }

    private val requestCodeCounter = AtomicInteger(0)
    
    /**
     * @param displayContext Activity (or other context) whose locale matches the UI language.
     * Application context keeps the process-default locale and does not update after in-app language change.
     */
    fun getAvailableSimOptions(displayContext: Context): List<SimOption> {
        val options = mutableListOf(
            SimOption(null, displayContext.getString(com.afrouzi.longsmssender.R.string.sim_default))
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subscriptionManager = displayContext.getSystemService(SubscriptionManager::class.java)
                val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                    displayContext,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                val active = if (hasPhoneStatePermission) {
                    subscriptionManager?.activeSubscriptionInfoList.orEmpty()
                } else {
                    emptyList()
                }
                active.forEachIndexed { index, info ->
                    val label = info.displayName?.toString()?.ifBlank { null }
                        ?: displayContext.getString(
                            com.afrouzi.longsmssender.R.string.sim_slot_format,
                            index + 1
                        )
                    options.add(SimOption(info.subscriptionId, label))
                }
                if (active.isEmpty() && hasPhoneStatePermission) {
                    val slotCount = subscriptionManager?.activeSubscriptionInfoCountMax ?: 0
                    for (slotIndex in 0 until slotCount) {
                        val slotInfo = subscriptionManager?.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
                        if (slotInfo != null) {
                            val slotLabel = slotInfo.displayName?.toString()?.ifBlank { null }
                                ?: displayContext.getString(
                                    com.afrouzi.longsmssender.R.string.sim_slot_format,
                                    slotIndex + 1
                                )
                            options.add(SimOption(slotInfo.subscriptionId, slotLabel))
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Some devices require privileged phone-state access for active subscription list.
            } catch (_: Exception) {
                // Fallback to default SIM only.
            }
        }
        
        return options.distinctBy { it.subscriptionId to it.displayName }
    }

    fun sendSms(
        destinationAddress: String,
        message: String,
        subscriptionId: Int? = null
    ): SmsResult {
        return try {
            val targetSmsManager = if (
                subscriptionId != null &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1
            ) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                smsManager
            }
            val parts = targetSmsManager.divideMessage(message)
            
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                
                for (i in parts.indices) {
                    val sentRequestCode = requestCodeCounter.getAndIncrement()
                    val deliveryRequestCode = requestCodeCounter.getAndIncrement()
                    
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
                
                targetSmsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                val sentRequestCode = requestCodeCounter.getAndIncrement()
                val deliveryRequestCode = requestCodeCounter.getAndIncrement()
                
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
                
                targetSmsManager.sendTextMessage(
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
