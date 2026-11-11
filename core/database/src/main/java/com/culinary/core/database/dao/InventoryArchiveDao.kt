package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.InventoryArchiveEntity

/** 库存档案读写（v9）。 */
@Dao
interface InventoryArchiveDao {
    @Insert
    suspend fun insert(entity: InventoryArchiveEntity)

    @Update
    suspend fun update(entity: InventoryArchiveEntity)

    @Query("SELECT * FROM inventory_archive WHERE id = :id")
    suspend fun findById(id: String): InventoryArchiveEntity?

    @Query("SELECT * FROM inventory_archive WHERE status = 'ACTIVE' AND deleted_at IS NULL ORDER BY name, id")
    suspend fun listActive(): List<InventoryArchiveEntity>

    @Query(
        "SELECT * FROM inventory_archive WHERE status = 'ACTIVE' AND deleted_at IS NULL " +
            "AND category_id = :categoryId ORDER BY name, id",
    )
    suspend fun listByCategory(categoryId: String): List<InventoryArchiveEntity>

    @Query(
        "SELECT * FROM inventory_archive WHERE status = 'ACTIVE' AND deleted_at IS NULL " +
            "AND category_id IS NULL ORDER BY name, id",
    )
    suspend fun listUncategorized(): List<InventoryArchiveEntity>

    @Query("SELECT * FROM inventory_archive WHERE sauce_id = :sauceId LIMIT 1")
    suspend fun findBySauce(sauceId: String): InventoryArchiveEntity?

    @Query("SELECT * FROM inventory_archive WHERE normalized_name = :normalizedName AND deleted_at IS NULL LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): InventoryArchiveEntity?

    @Query("SELECT COUNT(*) FROM inventory_archive WHERE status = 'ACTIVE' AND deleted_at IS NULL")
    suspend fun countActive(): Int
}
