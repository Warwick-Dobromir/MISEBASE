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

    @Query("SELECT * FROM temporary_barcode WHERE status = 'FREE' ORDER BY updated_at, barcode LIMIT 1")
    suspend fun findFree(): TemporaryBarcodeEntity?

    @Insert
    suspend fun insert(entity: TemporaryBarcodeEntity)

    @Insert
    suspend fun insertHistory(entity: TemporaryBarcodeHistoryEntity)

    @Query("UPDATE temporary_barcode SET status = 'ALLOCATED', batch_id = :batchId, allocated_at = :at, released_at = NULL, updated_at = :at WHERE id = :id AND status = 'FREE'")
    suspend fun allocate(id: String, batchId: String, at: Long): Int

    @Query("UPDATE temporary_barcode SET status = 'FREE', batch_id = NULL, released_at = :at, updated_at = :at WHERE id = :id AND status = 'ALLOCATED'")
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
    suspend fun releaseAndLog(
        id: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Int {
        val changed = release(id, at)
        if (changed == 1) insertHistory(history)
        return changed
    }

    @Query("SELECT * FROM temporary_barcode_history WHERE batch_id = :batchId ORDER BY occurred_at DESC")
    suspend fun historyForBatch(batchId: String): List<TemporaryBarcodeHistoryEntity>
}