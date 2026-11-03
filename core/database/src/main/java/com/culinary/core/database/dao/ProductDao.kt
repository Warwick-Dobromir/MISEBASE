package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProductEntity

/** 商品读写。 */
@Dao
interface ProductDao {
    @Insert
    suspend fun insert(entity: ProductEntity)

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun findById(id: String): ProductEntity?

    @Query("SELECT * FROM product WHERE ingredient_id = :ingredientId AND deleted_at IS NULL")
    suspend fun listByIngredient(ingredientId: String): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM product")
    suspend fun count(): Int

    @Query("DELETE FROM product WHERE id = :id")
    suspend fun deleteById(id: String)
}
