package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.culinary.core.database.entity.TemporaryBarcodeEntity
import com.culinary.core.database.entity.TemporaryBarcodeHistoryEntity

@Dao
interface TemporaryBarcodeDao {
    @Query("SELECT * FROM temporary_barcode WHERE status = 'AVAILABLE' ORDER BY created_at, id LIMIT 1")
    suspend fun nextAvailable(): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode WHERE current_batch_id = :batchId LIMIT 1")
    suspend fun findForBatch(batchId: String): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): TemporaryBarcodeEntity?

    @Query("SELECT * FROM temporary_barcode ORDER BY created_at DESC")
    suspend fun listAll(): List<TemporaryBarcodeEntity>

    @Insert
    suspend fun insertBarcode(entity: TemporaryBarcodeEntity)

    @Insert
    suspend fun insertHistory(entity: TemporaryBarcodeHistoryEntity)

    @Query("UPDATE temporary_barcode SET status = 'ASSIGNED', current_batch_id = :batchId, updated_at = :at WHERE id = :id AND status = 'AVAILABLE'")
    suspend fun assign(id: String, batchId: String, at: Long): Int

    @Query("UPDATE temporary_barcode SET status = 'AVAILABLE', current_batch_id = NULL, updated_at = :at WHERE id = :id AND current_batch_id = :batchId AND status = 'ASSIGNED'")
    suspend fun release(id: String, batchId: String, at: Long): Int

    @Transaction
    suspend fun recordAssignment(
        barcode: TemporaryBarcodeEntity,
        batchId: String,
        history: TemporaryBarcodeHistoryEntity,
        at: Long,
    ): Boolean {
        val current = findForBatch(batchId)
        if (current != null) return false
        if (barcode.status == "AVAILABLE") {
            if (assign(barcode.id, batchId, at) != 1) return false
        } else {
            insertBarcode(barcode.copy(status = "ASSIGNED", currentBatchId = batchId, updatedAt = at))
        }
        insertHistory(history)
        return true
    }

    @Transaction
    suspend fun recordRelease(
        barcodeId: String,
        batchId: String,
        at: Long,
        history: TemporaryBarcodeHistoryEntity,
    ): Boolean {
        if (release(barcodeId, batchId, at) != 1) return false
        insertHistory(history)
        return true
    }

    @Query("SELECT COUNT(*) FROM temporary_barcode WHERE status = 'AVAILABLE'")
    suspend fun countAvailable(): Int
}
