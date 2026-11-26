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
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.culinary.core.database.DatabaseFactory
import com.culinary.core.database.entity.InventoryBatchCapacityAllocationEntity
import com.culinary.core.database.entity.InventoryCapacityCellEntity
import com.culinary.core.domain.model.CapacityCellSnapshot
import com.culinary.core.domain.model.CapacityUnitAllocator
import com.culinary.core.domain.model.ExpiryCapacityZone
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ExpiryReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val writeExecutor = Executors.newSingleThreadExecutor()
        val readExecutor = Executors.newFixedThreadPool(2)
        val db = DatabaseFactory.create(applicationContext, writeExecutor, readExecutor)
        val result = try {
            val archives = db.inventoryArchiveDao().listActive().associateBy { it.id }
            val batches = db.inventoryBatchDao().listLive().filter { it.quantityOnHand > 0.0 }
            val today = LocalDate.now()
            batches.forEach { batch ->
                val archive = batch.archiveId?.let(archives::get) ?: return@forEach
                val expiry = expiryDate(batch.productionDate, batch.expiryDate, archive.defaultShelfLifeValue ?: archive.shelfLifeDays, archive.defaultShelfLifeUnit)
                val zone = expiryZone(batch.productionDate, expiry, today)
                db.withTransaction {
                    reclassifyCapacity(db, batch, archive.capacityUnitPerStockUnit, zone)
                }
            }
            val counts = mutableMapOf("过期" to 0, "临期" to 0, "关注" to 0)
            val productIds = mutableMapOf("过期" to mutableSetOf<String>(), "临期" to mutableSetOf<String>(), "关注" to mutableSetOf<String>())
            batches.forEach { batch ->
                val archive = batch.archiveId?.let(archives::get) ?: return@forEach
                val expiry = expiryDate(batch.productionDate, batch.expiryDate, archive.defaultShelfLifeValue ?: archive.shelfLifeDays, archive.defaultShelfLifeUnit) ?: return@forEach
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
        val workManager = WorkManager.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val names = listOf(ExpiryReminderWorker.WORK_A, ExpiryReminderWorker.WORK_B)
        executor.execute {
            try {
                val workInfos = names.flatMap { name ->
                    workManager.getWorkInfosForUniqueWork(name).get()
                }
                val hasPendingWork = workInfos.any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
                if (!hasPendingWork) {
                    ExpiryReminderWorker.scheduleNext(context.applicationContext, ExpiryReminderWorker.WORK_A)
                }
            } catch (_: Exception) {
                // A later app start retries the scheduling health check.
            } finally {
                executor.shutdown()
            }
        }
    }
}
