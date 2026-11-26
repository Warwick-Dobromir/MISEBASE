package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.culinary.core.database.entity.TemporaryBatchBarcodeEntity
import com.culinary.core.database.entity.TemporaryBatchBarcodeHistoryEntity

@Dao
interface TemporaryBatchBarcodeDao {
    @Query("SELECT * FROM temporary_batch_barcode WHERE batch_id = :batchId AND state = 'ASSIGNED' LIMIT 1")
    suspend fun findAssignedToBatch(batchId: String): TemporaryBatchBarcodeEntity?

    @Query("SELECT * FROM temporary_batch_barcode WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): TemporaryBatchBarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: TemporaryBatchBarcodeEntity): Long

    @Query("UPDATE temporary_batch_barcode SET batch_id = :batchId, state = 'ASSIGNED', assigned_at = :at, released_at = NULL WHERE barcode = :barcode AND state = 'FREE'")
    suspend fun claim(barcode: String, batchId: String, at: Long): Int

    @Query("UPDATE temporary_batch_barcode SET batch_id = NULL, state = 'FREE', released_at = :at WHERE batch_id = :batchId AND state = 'ASSIGNED'")
    suspend fun releaseBatch(batchId: String, at: Long): Int

    @Insert
    suspend fun appendHistory(entity: TemporaryBatchBarcodeHistoryEntity)

    @Query("SELECT * FROM temporary_batch_barcode_history WHERE batch_id = :batchId ORDER BY occurred_at DESC")
    suspend fun historyForBatch(batchId: String): List<TemporaryBatchBarcodeHistoryEntity>

    @Query("SELECT * FROM temporary_batch_barcode WHERE state = 'FREE' ORDER BY created_at, barcode")
    suspend fun listFree(): List<TemporaryBatchBarcodeEntity>

    @Query("UPDATE temporary_batch_barcode SET state = 'RETIRED' WHERE barcode = :barcode AND state = 'FREE'")
    suspend fun retireFree(barcode: String): Int

    @Transaction
    suspend fun allocateFree(barcode: String, batchId: String, at: Long, history: TemporaryBatchBarcodeHistoryEntity): Boolean {
        if (claim(barcode, batchId, at) != 1) return false
        appendHistory(history)
        return true
    }

    @Query("SELECT COUNT(*) FROM temporary_batch_barcode WHERE state = 'FREE'")
    suspend fun freeCount(): Int

    @Transaction
    suspend fun allocate(barcode: String, batchId: String, at: Long, history: TemporaryBatchBarcodeHistoryEntity): Boolean {
        val existing = findAssignedToBatch(batchId)
        if (existing != null) return true
        insertIfAbsent(TemporaryBatchBarcodeEntity(barcode = barcode, createdAt = at))
        if (claim(barcode, batchId, at) != 1) return false
        appendHistory(history)
        return true
    }
}
