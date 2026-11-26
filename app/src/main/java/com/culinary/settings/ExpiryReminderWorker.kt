package com.culinary.settings

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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.culinary.core.database.DatabaseFactory
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExpiryReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val writeExecutor = Executors.newSingleThreadExecutor()
        val readExecutor = Executors.newFixedThreadPool(2)
        val db = DatabaseFactory.create(applicationContext, writeExecutor, readExecutor)
        val result = try {
            val archives = db.inventoryArchiveDao().listActive().associateBy { it.id }
            val today = LocalDate.now()
            val counts = mutableMapOf("过期" to 0, "临期" to 0, "关注" to 0)
            val productIds = mutableMapOf("过期" to mutableSetOf<String>(), "临期" to mutableSetOf<String>(), "关注" to mutableSetOf<String>())
            db.inventoryBatchDao().listLive().forEach { batch ->
                if (batch.quantityOnHand <= 0.0) return@forEach
                val archive = batch.archiveId?.let(archives::get) ?: return@forEach
                val production = batch.productionDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
                val explicitExpiry = batch.expiryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val expiry = explicitExpiry ?: if (archive.shelfLifeEnabled) {
                    val value = archive.defaultShelfLifeValue ?: archive.shelfLifeDays
                    if (value == null || value <= 0) null else when (archive.defaultShelfLifeUnit) {
                        "DAY" -> production.plusDays(value.toLong())
                        "MONTH" -> production.plusMonths(value.toLong())
                        "YEAR" -> production.plusYears(value.toLong())
                        else -> null
                    }
                } else null
                if (expiry == null) return@forEach
                val totalDays = java.time.temporal.ChronoUnit.DAYS.between(production, expiry)
                if (totalDays <= 0) {
                    counts["过期"] = counts.getValue("过期") + 1
                    productIds.getValue("过期").add(archive.id)
                    return@forEach
                }
                val remaining = java.time.temporal.ChronoUnit.DAYS.between(today, expiry)
                val ratio = remaining.toDouble() / totalDays.toDouble()
                val status = when {
                    remaining <= 0 -> "过期"
                    ratio <= 0.2 -> "临期"
                    ratio <= 0.5 -> "关注"
                    else -> null
                } ?: return@forEach
                counts[status] = counts.getValue(status) + 1
                productIds.getValue(status).add(archive.id)
            }
            counts to productIds
        } finally {
            db.close()
            writeExecutor.shutdown()
            readExecutor.shutdown()
        }
        val expired = result.first.getValue("过期")
        val near = result.first.getValue("临期")
        val attention = result.first.getValue("关注")
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "库存效期提醒", NotificationManager.IMPORTANCE_HIGH))
        }
        if ((Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && expired + near + attention > 0) {
            val body = "过期 $expired 批 · 临期 $near 批 · 关注 $attention 批"
            val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val pending = launchIntent?.let {
                PendingIntent.getActivity(applicationContext, 4208, it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(com.culinary.R.mipmap.ic_launcher)
                .setContentTitle("MISEBASE 库存效期提醒")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("当前库存有批次需要处理：$body。打开库存批次查看详情。"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
        val expiredProducts = result.second.getValue("过期").size
        ExpiryAlertStore.save(if (expired > 0) InventoryExpiryAlert(expiredProducts, expired, emptyList(), System.currentTimeMillis()) else null)
        scheduleNext(applicationContext, inputData.getString(KEY_NEXT_NAME) ?: WORK_B)
        Result.success()
    } catch (error: Throwable) {
        Result.retry()
    }

    companion object {
        const val WORK_A = "inventory-expiry-reminder-a"
        const val WORK_B = "inventory-expiry-reminder-b"
        const val KEY_NEXT_NAME = "next_work_name"
        private const val CHANNEL_ID = "inventory_expiry_reminder"
        private const val NOTIFICATION_ID = 4208

        fun scheduleNext(context: Context, uniqueName: String) {
            val next = if (uniqueName == WORK_A) WORK_B else WORK_A
            val now = LocalDateTime.now()
            val morning = now.toLocalDate().atTime(8, 0)
            val evening = now.toLocalDate().atTime(20, 0)
            val target = when {
                now.isBefore(morning) -> morning
                now.isBefore(evening) -> evening
                else -> now.toLocalDate().plusDays(1).atTime(8, 0)
            }
            val delay = Duration.between(now, target).toMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_NEXT_NAME to next))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        }
    }
}

object ExpiryReminderScheduler {
    fun ensureScheduled(context: Context) {
        val preferences = context.getSharedPreferences("expiry_reminder_schedule", Context.MODE_PRIVATE)
        if (preferences.getBoolean("initialized", false)) return
        ExpiryReminderWorker.scheduleNext(context, ExpiryReminderWorker.WORK_A)
        preferences.edit().putBoolean("initialized", true).apply()
    }
}
