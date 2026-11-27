package com.culinary.settings

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * AlarmManager 是独立于应用进程的触发源；即使用户从最近任务划掉应用，
 * 系统闹钟仍会唤醒接收器，再以 WorkManager 执行库存扫描。
 */
class ExpiryReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_REMINDER -> {
                val appContext = context.applicationContext
                recordReminderStage(appContext, "系统闹钟已触发")
                WorkManager.getInstance(appContext).cancelUniqueWork(ExpiryReminderFallbackWorker.WORK_NAME)
                scheduleNextReminder(appContext)
                val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>().build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    ExpiryReminderWorker.WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
            ACTION_TEST_NOTIFICATION -> restorePendingTestNotification(context.applicationContext)
        }
    }

    companion object {
        private const val ACTION_REMINDER = "com.culinary.action.EXPIRY_REMINDER"
        private const val ACTION_TEST_NOTIFICATION = "com.culinary.action.EXPIRY_TEST_NOTIFICATION"
        private const val REMINDER_REQUEST_CODE = 4208
        private const val TEST_REQUEST_CODE = 4107
        private const val TEST_CHANNEL_ID = "inventory_expiry_test"
        private const val TEST_NOTIFICATION_ID = 4107
        private const val TEST_PENDING_KEY = "pending_test_notification"
        private const val TEST_TRIGGER_AT_KEY = "test_notification_trigger_at"
        private const val TEST_PRODUCT_COUNT_KEY = "test_notification_product_count"
        private const val TEST_BATCH_COUNT_KEY = "test_notification_batch_count"
        const val KEY_NEXT_ALARM_AT = "next_alarm_at"
        const val KEY_ALARM_SCHEDULE_MODE = "alarm_schedule_mode"
        const val KEY_ALARM_STAGE = "alarm_stage"
        const val KEY_ALARM_STAGE_AT = "alarm_stage_at"
        const val KEY_ALARM_LAST_TRIGGERED_AT = "alarm_last_triggered_at"

        private fun recordReminderStage(context: Context, stage: String) {
            context.getSharedPreferences(ExpiryReminderWorker.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALARM_STAGE, stage)
                .putLong(KEY_ALARM_STAGE_AT, System.currentTimeMillis())
                .putLong(KEY_ALARM_LAST_TRIGGERED_AT, System.currentTimeMillis())
                .apply()
        }

        fun scheduleNextReminder(context: Context) {
            val now = LocalDateTime.now()
            val morning = now.toLocalDate().atTime(8, 0)
            val evening = now.toLocalDate().atTime(20, 0)
            val target = when {
                now.isBefore(morning) -> morning
                now.isBefore(evening) -> evening
                else -> now.toLocalDate().plusDays(1).atTime(8, 0)
            }
            scheduleReminderAt(
                context,
                target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ExpiryReminderWorker.WORK_A,
            )
        }

        fun scheduleReminderAt(context: Context, triggerAtMillis: Long, nextName: String) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExpiryReminderAlarmReceiver::class.java)
                .setAction(ACTION_REMINDER)
                .putExtra(ExpiryReminderWorker.KEY_NEXT_NAME, nextName)
            val pending = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val exactAllowed = canScheduleExactAlarms(context, alarmManager)
            val scheduleMode = if (exactAllowed) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                    "精确闹钟 + WorkManager 兜底"
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                    "非精确闹钟 + WorkManager 兜底"
                }
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                "未获精确闹钟权限：非精确闹钟 + WorkManager 兜底"
            }
            context.getSharedPreferences(ExpiryReminderWorker.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_ALARM_AT, triggerAtMillis)
                .putString(KEY_ALARM_SCHEDULE_MODE, scheduleMode)
                .putString(KEY_ALARM_STAGE, "已登记下一次提醒")
                .putLong(KEY_ALARM_STAGE_AT, System.currentTimeMillis())
                .apply()

            // AlarmManager 负责准点唤醒；WorkManager 作为持久化兜底。
            val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val fallback = OneTimeWorkRequestBuilder<ExpiryReminderFallbackWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ExpiryReminderFallbackWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                fallback,
            )
        }

        fun scheduleTestNotification(
            context: Context,
            productCount: Int,
            batchCount: Int,
            delayMinutes: Long,
        ) {
            val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes)
            val preferences = context.getSharedPreferences(
                ExpiryTestNotificationWorker.TEST_STATE_PREFS,
                Context.MODE_PRIVATE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            check(canScheduleExactAlarms(context, alarmManager)) {
                "请先在系统设置中允许 MISEBASE 使用闹钟和提醒权限。"
            }
            check(
                preferences.edit()
                    .putBoolean(TEST_PENDING_KEY, true)
                    .putLong(TEST_TRIGGER_AT_KEY, triggerAt)
                    .putInt(TEST_PRODUCT_COUNT_KEY, productCount)
                    .putInt(TEST_BATCH_COUNT_KEY, batchCount)
                    .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "正在登记精确闹钟")
                    .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                    .commit(),
            ) { "测试通知状态保存失败。" }
            try {
                scheduleTestAlarm(context, triggerAt)
                updateTestState(preferences, "精确闹钟已登记")
            } catch (error: Throwable) {
                preferences.edit()
                    .putBoolean(TEST_PENDING_KEY, false)
                    .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "后台闹钟登记失败：${error.message ?: error.javaClass.simpleName}")
                    .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                    .commit()
                throw error
            }
        }

        fun restorePendingTestNotification(context: Context) {
            val preferences = context.getSharedPreferences(
                ExpiryTestNotificationWorker.TEST_STATE_PREFS,
                Context.MODE_PRIVATE,
            )
            if (!preferences.getBoolean(TEST_PENDING_KEY, false)) return
            val triggerAt = preferences.getLong(TEST_TRIGGER_AT_KEY, 0L)
            if (triggerAt <= 0L) {
                preferences.edit().putBoolean(TEST_PENDING_KEY, false).apply()
                return
            }
            if (triggerAt > System.currentTimeMillis()) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!canScheduleExactAlarms(context, alarmManager)) {
                    updateTestState(preferences, "精确闹钟权限未授予，等待授权后恢复")
                    return
                }
                runCatching { scheduleTestAlarm(context, triggerAt) }
                    .onFailure { updateTestState(preferences, "后台闹钟恢复失败：${it.message ?: it.javaClass.simpleName}") }
                return
            }
            val productCount = preferences.getInt(TEST_PRODUCT_COUNT_KEY, 0)
            val batchCount = preferences.getInt(TEST_BATCH_COUNT_KEY, 0)
            val claimed = preferences.edit().putBoolean(TEST_PENDING_KEY, false).commit()
            if (claimed) showTestNotification(context, productCount, batchCount)
        }

        private fun scheduleTestAlarm(context: Context, triggerAtMillis: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExpiryReminderAlarmReceiver::class.java)
                .setAction(ACTION_TEST_NOTIFICATION)
            val pending = PendingIntent.getBroadcast(
                context,
                TEST_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            check(canScheduleExactAlarms(context, alarmManager)) {
                "精确闹钟权限未授予。"
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }

        private fun canScheduleExactAlarms(context: Context, alarmManager: AlarmManager): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        private fun showTestNotification(context: Context, productCount: Int, batchCount: Int) {
            val preferences = context.getSharedPreferences(
                ExpiryTestNotificationWorker.TEST_STATE_PREFS,
                Context.MODE_PRIVATE,
            )
            preferences.edit()
                .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "后台闹钟已触发")
                .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                .apply()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                preferences.edit()
                    .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "通知权限未授予")
                    .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                    .apply()
                return
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!manager.areNotificationsEnabled()) {
                preferences.edit()
                    .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "系统通知已关闭")
                    .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                    .apply()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(TEST_CHANNEL_ID, "库存效期警戒", NotificationManager.IMPORTANCE_HIGH),
                )
                if (manager.getNotificationChannel(TEST_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                    updateTestState(preferences, "测试通知渠道已关闭")
                    return
                }
            }
            val body = if (productCount > 0 && batchCount > 0) {
                "库存过期警戒：检测到 $productCount 种商品、$batchCount 个批次存在过期库存，请及时处理。"
            } else {
                "库存效期扫描测试完成：当前未发现过期库存。"
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    TEST_REQUEST_CODE,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                .setSmallIcon(com.culinary.R.drawable.ic_stat_notification)
                .setContentTitle("库存效期警戒")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()
            manager.notify(TEST_NOTIFICATION_ID, notification)
            updateTestState(preferences, "系统通知已发送")
        }

        private fun updateTestState(preferences: android.content.SharedPreferences, value: String) {
            preferences.edit()
                .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, value)
                .putLong(ExpiryTestNotificationWorker.TEST_UPDATED_KEY, System.currentTimeMillis())
                .apply()
        }
    }
}

/** 系统重启或用户调整时间/时区后，重新登记下一次提醒。 */
class ExpiryReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (
            intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent?.action == Intent.ACTION_TIME_CHANGED ||
            intent?.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            ExpiryReminderScheduler.ensureScheduled(context.applicationContext)
        }
    }
}
