package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.InventoryArchiveProductEntity

/** 库存档案 ↔ 采购商品（v9）。 */
@Dao
interface InventoryArchiveProductDao {
    @Insert
    suspend fun insert(entity: InventoryArchiveProductEntity)

    @Update
    suspend fun update(entity: InventoryArchiveProductEntity)

    @Query("SELECT * FROM inventory_archive_product WHERE archive_id = :archiveId ORDER BY is_default DESC, created_at")
    suspend fun listByArchive(archiveId: String): List<InventoryArchiveProductEntity>

    @Query("SELECT * FROM inventory_archive_product WHERE product_id = :productId LIMIT 1")
    suspend fun findByProduct(productId: String): InventoryArchiveProductEntity?

    @Query("SELECT * FROM inventory_archive_product WHERE archive_id = :archiveId AND product_id = :productId")
    suspend fun find(archiveId: String, productId: String): InventoryArchiveProductEntity?

    /** 按食材反查档案：一个商品只归属一个档案，优先取默认商品。 */
    @Query(
        "SELECT ap.archive_id FROM inventory_archive_product ap " +
            "INNER JOIN product p ON p.id = ap.product_id " +
            "WHERE p.ingredient_id = :ingredientId " +
            "ORDER BY ap.is_default DESC, ap.created_at LIMIT 1",
    )
    suspend fun findArchiveIdByIngredient(ingredientId: String): String?

    @Query("SELECT * FROM inventory_archive_product WHERE product_id = :productId")
    suspend fun listByProduct(productId: String): List<InventoryArchiveProductEntity>

    @Query("SELECT COUNT(*) FROM inventory_archive_product WHERE archive_id = :archiveId")
    suspend fun countByArchive(archiveId: String): Int

    @Query("DELETE FROM inventory_archive_product WHERE archive_id = :archiveId AND product_id = :productId")
    suspend fun delete(archiveId: String, productId: String)
}
