package com.culinary.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.culinary.core.database.DatabaseFactory
import com.culinary.core.database.entity.InventoryBatchCapacityAllocationEntity
import com.culinary.core.database.entity.InventoryCapacityCellEntity
import com.culinary.core.domain.model.CapacityCellSnapshot
import com.culinary.core.domain.model.CapacityUnitAllocator
import com.culinary.core.domain.model.ExpiryCapacityZone
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException

class ExpiryReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        ExpiryReminderDiagnostics.record(applicationContext, "SCAN_STARTED", "expiry reminder worker started")
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "后台扫描任务已启动")
            .putLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, System.currentTimeMillis())
            .apply()
        val writeExecutor = Executors.newSingleThreadExecutor()
        val readExecutor = Executors.newFixedThreadPool(2)
        val db = DatabaseFactory.create(applicationContext, writeExecutor, readExecutor)
        val result = try {
            val archives = db.inventoryArchiveDao().listActive().associateBy { it.id }
            val batches = db.inventoryBatchDao().listLive().filter { it.quantityOnHand > 0.0 }
            val today = LocalDate.now()
            batches.forEach { batch ->
                val archive = batch.archiveId?.let(archives::get) ?: return@forEach
                val expiry = expiryDate(batch.productionDate, batch.expiryDate, if (archive.shelfLifeEnabled) archive.defaultShelfLifeValue ?: archive.shelfLifeDays else null, if (archive.shelfLifeEnabled) archive.defaultShelfLifeUnit ?: "DAY" else null)
                val zone = expiryZone(batch.productionDate, expiry, today)
                db.withTransaction {
                    reclassifyCapacity(db, batch, archive.capacityUnitPerStockUnit, zone)
                }
            }
            val counts = mutableMapOf("过期" to 0, "临期" to 0, "关注" to 0)
            val productIds = mutableMapOf("过期" to mutableSetOf<String>(), "临期" to mutableSetOf<String>(), "关注" to mutableSetOf<String>())
            batches.forEach { batch ->
                val archive = batch.archiveId?.let(archives::get) ?: return@forEach
                val expiry = expiryDate(batch.productionDate, batch.expiryDate, if (archive.shelfLifeEnabled) archive.defaultShelfLifeValue ?: archive.shelfLifeDays else null, if (archive.shelfLifeEnabled) archive.defaultShelfLifeUnit ?: "DAY" else null) ?: return@forEach
                val zone = expiryZone(batch.productionDate, expiry, today)
                val label = when (zone) {
                    ExpiryCapacityZone.EXPIRED -> "过期"
                    ExpiryCapacityZone.NEAR_EXPIRY -> "临期"
                    ExpiryCapacityZone.ATTENTION -> "关注"
                    else -> null
                } ?: return@forEach
                counts[label] = counts.getValue(label) + 1
                productIds.getValue(label).add(archive.id)
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
        val actionableCount = expired + near + attention
        ExpiryReminderDiagnostics.record(
            applicationContext,
            "SCAN_COUNTS",
            "expired=$expired; near=$near; attention=$attention",
        )
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        val notificationStatus = when {
            actionableCount == 0 -> "扫描完成：当前没有需要提醒的批次"
            !permissionGranted -> "通知权限未授予"
            !manager.areNotificationsEnabled() -> "系统通知已关闭"
            !channelEnabled -> "库存效期提醒通知渠道已关闭"
            else -> {
                val body = "过期 " + expired + " 批 · 临期 " + near + " 批 · 关注 " + attention + " 批"
                val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pending = launchIntent?.let {
                    PendingIntent.getActivity(
                        applicationContext,
                        NOTIFICATION_ID,
                        it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(com.culinary.R.drawable.ic_stat_notification)
                    .setContentTitle("MISEBASE 库存效期提醒")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("当前库存有批次需要处理：" + body + "。打开库存批次查看详情。"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .apply { if (pending != null) setContentIntent(pending) }
                    .build()
                manager.notify(NOTIFICATION_ID, notification)
                "通知已发送"
            }
        }
        val expiredProducts = result.second.getValue("过期").size
        ExpiryAlertStore.save(if (expired > 0) InventoryExpiryAlert(expiredProducts, expired, emptyList(), System.currentTimeMillis()) else null)
        val completedAt = System.currentTimeMillis()
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SUCCESSFUL_SCAN, completedAt)
            .putLong(KEY_LAST_SCAN_AT, completedAt)
            .putString(KEY_LAST_SCAN_STATUS, notificationStatus)
            .putString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "后台扫描完成：$notificationStatus")
            .putLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, completedAt)
            .remove(KEY_LAST_SCAN_ERROR)
            .apply()
        ExpiryReminderDiagnostics.record(applicationContext, "SCAN_FINISHED", notificationStatus)
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SCAN_AT, System.currentTimeMillis())
            .putString(KEY_LAST_SCAN_STATUS, "扫描执行失败")
            .putString(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE, "后台扫描失败")
            .putLong(ExpiryReminderAlarmReceiver.KEY_ALARM_STAGE_AT, System.currentTimeMillis())
            .putString(KEY_LAST_SCAN_ERROR, error.message ?: error.javaClass.simpleName)
            .apply()
        ExpiryReminderDiagnostics.record(
            applicationContext,
            "SCAN_FAILED",
            "${error.javaClass.simpleName}: ${error.message ?: "no message"}",
        )
        Log.e("ExpiryReminderWorker", "Inventory expiry scan failed", error)
        Result.retry()
    }

    private suspend fun reclassifyCapacity(
        db: com.culinary.core.database.CulinaryDatabase,
        batch: com.culinary.core.database.entity.InventoryBatchEntity,
        coefficient: Double,
        zone: ExpiryCapacityZone,
    ) {
        if (!coefficient.isFinite() || coefficient <= 0.0) return
        val dao = db.inventoryCapacityDao()
        val existing = dao.allocationsForBatch(batch.id)
        if (existing.isNotEmpty() && existing.all { dao.findCell(it.cellId)?.expiryZone == zone.name }) return
        val now = System.currentTimeMillis()
        existing.forEach { allocation ->
            check(dao.changeOccupied(allocation.cellId, -allocation.occupiedCu, now) == 1)
            dao.deleteAllocation(allocation.id)
        }
        val metadata = db.appMetadataDao().find("inventory.capacity.location.${batch.locationId}.limit_cu")
            ?.value?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 100.0
        val cells = dao.cellsForScope(batch.locationId, zone.name)
        cells.filter { it.occupiedCu <= metadata }.forEach { dao.updateCapacityIfFits(it.id, metadata, now) }
        val refreshed = dao.cellsForScope(batch.locationId, zone.name)
        val snapshots = refreshed.map { CapacityCellSnapshot(it.sequence, BigDecimal(minOf(it.capacityCu, metadata).toString()), BigDecimal(it.occupiedCu.toString())) }
        val required = CapacityUnitAllocator.calculateRequiredCu(batch.quantityOnHand, coefficient)
        val plan = CapacityUnitAllocator.plan(required, BigDecimal(metadata.toString()), snapshots)
        val bySequence = refreshed.associateBy { it.sequence }
        var quantityRemaining = batch.quantityOnHand
        plan.allocations.forEachIndexed { index, item ->
            val cell = bySequence[item.sequence] ?: InventoryCapacityCellEntity(
                id = UUID.randomUUID().toString(),
                baseLocationId = batch.locationId,
                expiryZone = zone.name,
                sequence = item.sequence,
                capacityCu = metadata,
                occupiedCu = 0.0,
                createdAt = now,
                updatedAt = now,
            ).also { dao.insertCell(it) }
            val cu = item.allocatedCu.toDouble()
            check(dao.changeOccupied(cell.id, cu, now) == 1)
            val quantity = if (index == plan.allocations.lastIndex) quantityRemaining else (batch.quantityOnHand * cu / required.toDouble()).also { quantityRemaining -= it }
            dao.insertAllocation(InventoryBatchCapacityAllocationEntity(UUID.randomUUID().toString(), batch.id, cell.id, quantity, cu, now, now))
        }
    }

    companion object {
        const val WORK_A = "inventory-expiry-reminder-a"
        const val WORK_B = "inventory-expiry-reminder-b"
        const val KEY_NEXT_NAME = "next_work_name"
        const val WORK_NAME = "inventory-expiry-reminder-scan"
        const val PREFS_NAME = "inventory_expiry_reminder_runtime"
        const val KEY_LAST_SUCCESSFUL_SCAN = "last_successful_scan"
        const val KEY_LAST_SCAN_AT = "last_scan_at"
        const val KEY_LAST_SCAN_STATUS = "last_scan_status"
        const val KEY_LAST_SCAN_ERROR = "last_scan_error"
        private const val CHANNEL_ID = "inventory_expiry_reminder"
        private const val NOTIFICATION_ID = 4208

        private fun expiryDate(productionText: String?, explicitText: String?, value: Int?, unit: String?): LocalDate? {
            explicitText?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { date -> return date } }
            val production = productionText?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            if (value == null || value <= 0) return null
            return when (unit) {
                "DAY" -> production.plusDays(value.toLong())
                "MONTH" -> production.plusMonths(value.toLong())
                "YEAR" -> production.plusYears(value.toLong())
                else -> null
            }
        }

        private fun expiryZone(productionText: String?, expiry: LocalDate?, today: LocalDate): ExpiryCapacityZone {
            val production = productionText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (production == null || expiry == null) return ExpiryCapacityZone.UNCLASSIFIED
            val total = java.time.temporal.ChronoUnit.DAYS.between(production, expiry)
            if (total <= 0) return ExpiryCapacityZone.EXPIRED
            val remaining = java.time.temporal.ChronoUnit.DAYS.between(today, expiry)
            return ExpiryCapacityZone.fromRemainingRatio(BigDecimal(remaining).divide(BigDecimal(total), 8, java.math.RoundingMode.HALF_UP))
                ?: ExpiryCapacityZone.UNCLASSIFIED
        }


    }
}

object ExpiryReminderScheduler {
    private const val STARTUP_CATCH_UP_AFTER_MILLIS = 10 * 60 * 60 * 1000L

    private const val KEY_LAST_MISSED_CATCH_UP_TARGET = "last_missed_reminder_catch_up_target"

    private fun latestReminderTarget(nowMillis: Long): Long {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val todayMorning = now.toLocalDate().atTime(8, 0)
        val todayEvening = now.toLocalDate().atTime(20, 0)
        val target = when {
            !now.isBefore(todayEvening) -> todayEvening
            !now.isBefore(todayMorning) -> todayMorning
            else -> now.toLocalDate().minusDays(1).atTime(20, 0)
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun ensureScheduled(context: Context, trigger: String = "application_startup") {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val preferences = appContext.getSharedPreferences(ExpiryReminderWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyScheduledTarget = preferences.getLong(ExpiryReminderAlarmReceiver.KEY_NEXT_ALARM_AT, 0L)
        val lastSuccessfulScan = preferences.getLong(ExpiryReminderWorker.KEY_LAST_SUCCESSFUL_SCAN, 0L)
        ExpiryReminderDiagnostics.record(
            appContext,
            "SCHEDULER_CHECK",
            "trigger=$trigger; previousTarget=$previouslyScheduledTarget; lastSuccessfulScan=$lastSuccessfulScan",
        )
        runCatching { ExpiryReminderAlarmReceiver.restorePendingTestNotification(appContext, trigger) }
            .onFailure {
                ExpiryReminderDiagnostics.record(
                    appContext,
                    "TEST_NOTIFICATION_RESTORE_FAILED",
                    "${it.javaClass.simpleName}: ${it.message ?: "no message"}",
                )
            }

        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelUniqueWork(ExpiryReminderWorker.WORK_A)
        workManager.cancelUniqueWork(ExpiryReminderWorker.WORK_B)

        // Do not silently discard a reminder whose scheduled time has passed.
        // The old implementation overwrote the overdue target first, then only
        // ran catch-up after 10 hours, so opening the app shortly after 20:00
        // could both lose the reminder and produce no notification.
        // Derive the most recent 08:00/20:00 slot from wall-clock time as well as
        // the stored target. Older app versions overwrote an overdue target on launch,
        // so the stored target may already point to tomorrow even though today's slot
        // was never scanned.
        val missedReminderTarget = latestReminderTarget(now)
        if (
            lastSuccessfulScan < missedReminderTarget &&
            preferences.getLong(KEY_LAST_MISSED_CATCH_UP_TARGET, 0L) != missedReminderTarget
        ) {
            ExpiryReminderDiagnostics.record(
                appContext,
                "MISSED_REMINDER_CATCH_UP_ENQUEUED",
                "trigger=$trigger; missedTarget=$missedReminderTarget; storedNextTarget=$previouslyScheduledTarget; lastSuccessfulScan=$lastSuccessfulScan",
            )
            val missedRequest = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(
                ExpiryReminderWorker.WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                missedRequest,
            )
            // Persist the de-duplication marker only after WorkManager accepted the request.
            preferences.edit().putLong(KEY_LAST_MISSED_CATCH_UP_TARGET, missedReminderTarget).commit()
        }

        runCatching { ExpiryReminderAlarmReceiver.scheduleNextReminder(appContext) }
            .onFailure {
                ExpiryReminderDiagnostics.record(
                    appContext,
                    "REMINDER_RESCHEDULE_FAILED",
                    "${it.javaClass.simpleName}: ${it.message ?: "no message"}",
                )
            }
        if (lastSuccessfulScan == 0L || now - lastSuccessfulScan >= STARTUP_CATCH_UP_AFTER_MILLIS) {
            ExpiryReminderDiagnostics.record(
                appContext,
                "STARTUP_CATCH_UP_ENQUEUED",
                "trigger=$trigger; lastSuccessfulScan=$lastSuccessfulScan; now=$now",
            )
            val request = OneTimeWorkRequestBuilder<ExpiryReminderWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(
                ExpiryReminderWorker.WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
