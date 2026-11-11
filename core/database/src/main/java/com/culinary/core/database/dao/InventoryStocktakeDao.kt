package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.InventoryStocktakeEntity
import com.culinary.core.database.entity.InventoryStocktakeLineEntity

/** 盘点单与盘点明细（v9）。 */
@Dao
interface InventoryStocktakeDao {
    @Insert
    suspend fun insertStocktake(entity: InventoryStocktakeEntity)

    @Update
    suspend fun updateStocktake(entity: InventoryStocktakeEntity)

    @Insert
    suspend fun insertLines(entities: List<InventoryStocktakeLineEntity>)

    @Query("SELECT * FROM inventory_stocktake WHERE id = :id")
    suspend fun findStocktake(id: String): InventoryStocktakeEntity?

    @Query("SELECT * FROM inventory_stocktake ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentStocktakes(limit: Int): List<InventoryStocktakeEntity>

    @Query("SELECT * FROM inventory_stocktake_line WHERE stocktake_id = :stocktakeId ORDER BY line_no")
    suspend fun linesOf(stocktakeId: String): List<InventoryStocktakeLineEntity>

    @Query("UPDATE inventory_stocktake_line SET counted_qty = :counted WHERE id = :id")
    suspend fun updateCounted(id: String, counted: Double)
}
