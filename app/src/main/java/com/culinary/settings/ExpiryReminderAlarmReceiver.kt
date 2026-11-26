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
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * AlarmManager 是独立于应用进程的触发源；即使用户从最近任务划掉应用，
 * 系统闹钟仍会唤醒接收器，再以 expedited Work 执行库存扫描。
 */
class ExpiryReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_REMINDER -> {
                // 先登记下一次闹钟，再启动扫描；即使本次 Worker 失败，也不会断掉后续提醒。
                scheduleNextReminder(context.applicationContext)
                val nextName = intent.getStringExtra(ExpiryReminderWorker.KEY_NEXT_NAME)
                    ?: ExpiryReminderWorker.WORK_B
                val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setInputData(workDataOf(ExpiryReminderWorker.KEY_NEXT_NAME to nextName))
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    nextName,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
            ACTION_TEST_NOTIFICATION -> {
                val productCount = intent.getIntExtra(ExpiryTestNotificationWorker.KEY_PRODUCT_COUNT, 0)
                val batchCount = intent.getIntExtra(ExpiryTestNotificationWorker.KEY_BATCH_COUNT, 0)
                showTestNotification(context.applicationContext, productCount, batchCount)
            }
        }
    }

    companion object {
        private const val ACTION_REMINDER = "com.culinary.action.EXPIRY_REMINDER"
        private const val ACTION_TEST_NOTIFICATION = "com.culinary.action.EXPIRY_TEST_NOTIFICATION"
        private const val REMINDER_REQUEST_CODE = 4208
        private const val TEST_REQUEST_CODE = 4107
        private const val TEST_CHANNEL_ID = "inventory_expiry_test"
        private const val TEST_NOTIFICATION_ID = 4107

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
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }

        fun scheduleTestNotification(
            context: Context,
            productCount: Int,
            batchCount: Int,
            delayMinutes: Long,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ExpiryReminderAlarmReceiver::class.java)
                .setAction(ACTION_TEST_NOTIFICATION)
                .putExtra(ExpiryTestNotificationWorker.KEY_PRODUCT_COUNT, productCount)
                .putExtra(ExpiryTestNotificationWorker.KEY_BATCH_COUNT, batchCount)
            val pending = PendingIntent.getBroadcast(
                context,
                TEST_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes),
                pending,
            )
        }

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
                .setSmallIcon(com.culinary.R.mipmap.ic_launcher)
                .setContentTitle("库存效期警戒")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()
            manager.notify(TEST_NOTIFICATION_ID, notification)
            preferences.edit()
                .putString(ExpiryTestNotificationWorker.TEST_STATE_KEY, "系统通知已发送")
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
            intent?.action == Intent.ACTION_TIME_CHANGED ||
            intent?.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            ExpiryReminderAlarmReceiver.scheduleNextReminder(context.applicationContext)
        }
    }
}
