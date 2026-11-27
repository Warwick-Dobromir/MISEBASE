package com.culinary.plans

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.culinary.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Persistent, per-plan reminder scheduling. WorkManager retains requests across
 * process death and device restart; delivery can be delayed by Android battery policy.
 */
object PlanReminderScheduler {
    private const val UNIQUE_PREFIX = "plan-reminder-"

    fun schedule(context: Context, planId: String, title: String, date: String, time: String) {
        val normalizedTime = time.trim()
        if (normalizedTime.isBlank() || normalizedTime == "不指定") {
            cancel(context, planId)
            return
        }
        val target = runCatching {
            LocalDateTime.parse("${date.trim()}T${normalizedTime}", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: run {
            cancel(context, planId)
            return
        }
        val delay = target - System.currentTimeMillis()
        if (delay <= 0L) {
            cancel(context, planId)
            return
        }
        val request = OneTimeWorkRequestBuilder<PlanReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putString(PlanReminderWorker.KEY_PLAN_ID, planId)
                    .putString(PlanReminderWorker.KEY_TITLE, title)
                    .putString(PlanReminderWorker.KEY_TIME, normalizedTime)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_PREFIX + planId,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, planId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PREFIX + planId)
    }
}

class PlanReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val planId = inputData.getString(KEY_PLAN_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val time = inputData.getString(KEY_TIME).orEmpty()
        if (planId.isBlank() || title.isBlank()) return Result.failure()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "计划提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "制作计划和工作计划的执行时间提醒"
                },
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            planId.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.culinary.R.drawable.ic_stat_notification)
            .setContentTitle("计划时间到了")
            .setContentText("现在是 $time，请开始：$title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("现在是 $time，请开始执行「$title」。"))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(planId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_PLAN_ID = "plan_id"
        const val KEY_TITLE = "plan_title"
        const val KEY_TIME = "plan_time"
        private const val CHANNEL_ID = "plan_time_reminders"
    }
}
