package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.culinary.core.database.entity.TemporaryBarcodeEntity
import com.culinary.core.database.entity.TemporaryBarcodeHistoryEntity

@Dao
interface TemporaryBarcodeDao {
    @Query("SELECT * FROM temporary_barcode WHERE batch_id = :batchId AND status = 'ALLOCATED' LIMIT 1")
    suspend fun findAllocatedForBatch(batchId: String): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode WHERE barcode = :barcode AND status = 'ALLOCATED' LIMIT 1")
    suspend fun findAllocatedByBarcode(barcode: String): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode_history WHERE barcode = :barcode ORDER BY occurred_at DESC LIMIT 1")
    suspend fun findHistoricalBarcode(barcode: String): TemporaryBarcodeHistoryEntity?

    @Query("SELECT * FROM temporary_barcode WHERE status = 'FREE' AND barcode NOT IN (SELECT barcode FROM inventory_archive WHERE barcode IS NOT NULL AND TRIM(barcode) != '') ORDER BY updated_at, barcode LIMIT 1")
    suspend fun findFree(): TemporaryBarcodeEntity?

    @Insert
    suspend fun insert(entity: TemporaryBarcodeEntity)

    @Insert
    suspend fun insertHistory(entity: TemporaryBarcodeHistoryEntity)

    @Query("UPDATE temporary_barcode SET status = 'ALLOCATED', batch_id = :batchId, allocated_at = :at, released_at = NULL, updated_at = :at WHERE id = :id AND status = 'FREE'")
    suspend fun allocate(id: String, batchId: String, at: Long): Int

    @Query("UPDATE temporary_barcode SET barcode = :barcode, status = 'ALLOCATED', batch_id = :batchId, allocated_at = :at, released_at = NULL, updated_at = :at WHERE id = :id AND status = 'FREE'")
    suspend fun allocateWithBarcode(id: String, barcode: String, batchId: String, at: Long): Int

    @Query("UPDATE temporary_barcode SET status = 'PENDING_CLEAN', batch_id = NULL, released_at = :at, updated_at = :at WHERE id = :id AND status = 'ALLOCATED'")
    suspend fun release(id: String, at: Long): Int

    @Transaction
    suspend fun allocateAndLog(
        id: String,
        batchId: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        val changed = allocate(id, batchId, at)
        if (changed == 1) insertHistory(history)
        return changed
    }

    @Transaction
    suspend fun allocateWithBarcodeAndLog(
        id: String,
        barcode: String,
        batchId: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        val changed = allocateWithBarcode(id, barcode, batchId, at)
        if (changed == 1) insertHistory(history)
        return changed
    }

    @Transaction
    suspend fun releaseAndLog(
        id: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        val changed = release(id, at)
        if (changed == 1) insertHistory(history)
        return changed
    }

    @Query("SELECT * FROM temporary_barcode ORDER BY status, updated_at DESC, barcode")
    suspend fun listAll(): List<TemporaryBarcodeEntity>

    @Query(
        "SELECT temporary_barcode.* FROM temporary_barcode " +
            "INNER JOIN inventory_batch ON temporary_barcode.batch_id = inventory_batch.id " +
            "WHERE temporary_barcode.status = 'ALLOCATED' " +
            "AND inventory_batch.status IN ('EXPIRED', 'DISCARDED')",
    )
    suspend fun listTerminalBatchAllocations(): List<TemporaryBarcodeEntity>

    @Transaction
    suspend fun releaseTerminalBatchAllocations(at: Long) {
        listTerminalBatchAllocations().forEach { item ->
            val batchId = item.batchId ?: return@forEach
            releaseAndLog(
                item.id,
                at,
                TemporaryBarcodeHistoryEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    barcodeId = item.id,
                    barcode = item.barcode,
                    batchId = batchId,
                    action = "PENDING_CLEAN",
                    occurredAt = at,
                ),
            )
        }
    }

    @Query("SELECT * FROM temporary_barcode WHERE status = 'PENDING_CLEAN' ORDER BY released_at, barcode")
    suspend fun listPendingClean(): List<TemporaryBarcodeEntity>

    @Query("UPDATE temporary_barcode SET status = 'FREE', updated_at = :at WHERE id = :id AND status = 'PENDING_CLEAN'")
    suspend fun markClean(id: String, at: Long): Int

    @Transaction
    suspend fun cleanAndLog(
        id: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        val changed = markClean(id, at)
        if (changed == 1) insertHistory(history)
        return changed
    }

    @Transaction
    suspend fun insertAndAllocate(
        entity: TemporaryBarcodeEntity,
        batchId: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        insert(entity)
        return allocateAndLog(entity.id, batchId, at, history)
    }

    @Query("SELECT batch_id FROM temporary_barcode_history WHERE barcode_id = :barcodeId AND action = 'ALLOCATED' ORDER BY occurred_at DESC LIMIT 1")
    suspend fun latestAllocatedBatchId(barcodeId: String): String?

    @Query("SELECT COUNT(*) FROM temporary_barcode_history WHERE barcode_id = :barcodeId AND action = 'ALLOCATED'")
    suspend fun allocationGeneration(barcodeId: String): Int

    @Query("SELECT * FROM temporary_barcode_history WHERE batch_id = :batchId ORDER BY occurred_at DESC")
    suspend fun historyForBatch(batchId: String): List<TemporaryBarcodeHistoryEntity>
}