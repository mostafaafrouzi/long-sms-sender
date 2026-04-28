package com.afrouzi.longsmssender.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ScheduledSmsRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val jobs = ScheduledSmsJobStore(context).loadJobs()
        if (jobs.isEmpty()) {
            Log.d(TAG, "No scheduled jobs to re-register on $action")
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        jobs.forEach { job ->
            // Keep consumeIfPending consistent after restart.
            ScheduledMessageStore.register(context, job.token, job.dueAtMillis)
            val pendingIntent = createScheduledPendingIntent(context, job)
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    job.dueAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Re-registered exact alarm token=${job.token} due=${job.dueAtMillis}")
            } catch (_: SecurityException) {
                Log.w(TAG, "Exact alarm denied on reboot; fallback WorkManager token=${job.token}")
                val delayMillis = (job.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                val workData = Data.Builder()
                    .putString(ScheduledSmsWorker.KEY_TOKEN, job.token)
                    .putString(ScheduledSmsWorker.KEY_MESSAGE, job.message)
                    .putString(ScheduledSmsWorker.KEY_RECIPIENTS, job.recipientsRaw)
                    .apply {
                        job.subscriptionId?.let {
                            putInt(ScheduledSmsWorker.KEY_SUBSCRIPTION_ID, it)
                        }
                    }
                    .build()
                val request = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(workData)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "scheduled_sms_${job.token}",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }
    }

    private fun createScheduledPendingIntent(context: Context, job: ScheduledSmsJob): PendingIntent {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = "com.afrouzi.longsmssender.SCHEDULED_SEND.${job.token}"
            data = Uri.parse("longsmssender://scheduled/${job.token}")
            putExtra(ScheduledSmsReceiver.EXTRA_MESSAGE, job.message)
            putExtra(ScheduledSmsReceiver.EXTRA_RECIPIENTS, job.recipientsRaw)
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULE_TOKEN, job.token)
            job.subscriptionId?.let {
                putExtra(ScheduledSmsReceiver.EXTRA_SIM_SUBSCRIPTION_ID, it)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            job.token.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ScheduledSmsReschedule"
    }
}
