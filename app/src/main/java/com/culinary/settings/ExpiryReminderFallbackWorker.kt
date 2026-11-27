package com.culinary.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * AlarmManager 的持久化兜底。
 *
 * 某些设备的后台省电策略可能延迟或漏掉应用闹钟；WorkManager 任务由系统持久化，
 * 在应用进程不存活时仍可由系统调度。它与 AlarmManager 共用同一条扫描工作链。
 */
class ExpiryReminderFallbackWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        ExpiryReminderDiagnostics.record(
            applicationContext,
            "WORKMANAGER_FALLBACK_TRIGGERED",
            "fallback worker started",
        )
        val preferences = applicationContext.getSharedPreferences(
            ExpiryReminderWorker.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        preferences.edit()
            .putString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "WorkManager 兜底已触发")
            .putLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, System.currentTimeMillis())
            .apply()

        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        ExpiryReminderDiagnostics.record(
            applicationContext,
            "SCAN_WORK_ENQUEUED",
            "source=WorkManager fallback; policy=APPEND_OR_REPLACE; expedited=true",
        )
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ExpiryReminderWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        // Keep the current scan even if registering tomorrow's alarm fails.
        runCatching { ExpiryReminderAlarmReceiver.scheduleNextReminder(applicationContext) }
            .onFailure {
                ExpiryReminderDiagnostics.record(
                    applicationContext,
                    "NEXT_REMINDER_RESCHEDULE_FAILED",
                    "${it.javaClass.simpleName}: ${it.message ?: "no message"}",
                )
            }
        Result.success()
    } catch (error: Throwable) {
        ExpiryReminderDiagnostics.record(
            applicationContext,
            "WORKMANAGER_FALLBACK_FAILED",
            "${error.javaClass.simpleName}: ${error.message ?: "no message"}",
        )
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "inventory-expiry-reminder-fallback"
    }
}
