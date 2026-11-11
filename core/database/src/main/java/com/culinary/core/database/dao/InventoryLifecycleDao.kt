package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryFreezeRecordEntity
import com.culinary.core.database.entity.InventoryLocationTransferEntity

/** 冻结/解冻与库位转移记录（v9），均为追加式。 */
@Dao
interface InventoryLifecycleDao {
    @Insert
    suspend fun insertFreezeRecord(entity: InventoryFreezeRecordEntity)

    @Query("SELECT * FROM inventory_freeze_record WHERE batch_id = :batchId ORDER BY occurred_at")
    suspend fun freezeRecords(batchId: String): List<InventoryFreezeRecordEntity>

    @Query("SELECT * FROM inventory_freeze_record ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recentFreezeRecords(limit: Int): List<InventoryFreezeRecordEntity>

    @Insert
    suspend fun insertTransfer(entity: InventoryLocationTransferEntity)

    @Query("SELECT * FROM inventory_location_transfer WHERE batch_id = :batchId ORDER BY occurred_at")
    suspend fun transfers(batchId: String): List<InventoryLocationTransferEntity>

    @Query("SELECT * FROM inventory_location_transfer ORDER BY occurred_at DESC LIMIT :limit")
    suspend fun recentTransfers(limit: Int): List<InventoryLocationTransferEntity>
}
