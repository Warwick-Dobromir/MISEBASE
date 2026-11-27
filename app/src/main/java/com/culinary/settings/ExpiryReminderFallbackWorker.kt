package com.culinary.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
    override suspend fun doWork(): Result {
        val preferences = applicationContext.getSharedPreferences(
            ExpiryReminderWorker.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        preferences.edit()
            .putString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "WorkManager 兜底已触发")
            .putLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, System.currentTimeMillis())
            .apply()

        // 兜底先接管下一次闹钟登记，再触发扫描；与 AlarmManager 接收器竞争时由唯一任务去重。
        ExpiryReminderAlarmReceiver.scheduleNextReminder(applicationContext)
        val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ExpiryReminderWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "inventory-expiry-reminder-fallback"
    }
}
