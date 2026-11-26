package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.InventoryBatchCapacityAllocationEntity
import com.culinary.core.database.entity.InventoryCapacityCellEntity

@Dao
interface InventoryCapacityDao {
    @Query("SELECT * FROM inventory_capacity_cell WHERE base_location_id = :locationId AND expiry_zone = :zone ORDER BY sequence")
    suspend fun cellsForScope(locationId: String, zone: String): List<InventoryCapacityCellEntity>
    @Query("SELECT * FROM inventory_capacity_cell WHERE id = :id")
    suspend fun findCell(id: String): InventoryCapacityCellEntity?
    @Insert suspend fun insertCell(cell: InventoryCapacityCellEntity)
    /** Conditional update is the final capacity guard under concurrent receipts. */
    @Query("UPDATE inventory_capacity_cell SET occupied_cu = occupied_cu + :delta, updated_at = :at WHERE id = :id AND occupied_cu + :delta >= -0.000001 AND occupied_cu + :delta <= capacity_cu + 0.000001")
    suspend fun changeOccupied(id: String, delta: Double, at: Long): Int
    @Query("SELECT * FROM inventory_batch_capacity_allocation WHERE batch_id = :batchId ORDER BY created_at, id")
    suspend fun allocationsForBatch(batchId: String): List<InventoryBatchCapacityAllocationEntity>
    @Insert suspend fun insertAllocation(allocation: InventoryBatchCapacityAllocationEntity)
    @Update suspend fun updateAllocation(allocation: InventoryBatchCapacityAllocationEntity): Int
    @Query("DELETE FROM inventory_batch_capacity_allocation WHERE id = :id")
    suspend fun deleteAllocation(id: String)
    @Query("UPDATE inventory_capacity_cell SET capacity_cu = :capacityCu, updated_at = :at WHERE id = :id AND occupied_cu <= :capacityCu")
    suspend fun updateCapacityIfFits(id: String, capacityCu: Double, at: Long): Int
    @Query("SELECT * FROM inventory_capacity_cell ORDER BY base_location_id, expiry_zone, sequence")
    suspend fun allCells(): List<InventoryCapacityCellEntity>
}
