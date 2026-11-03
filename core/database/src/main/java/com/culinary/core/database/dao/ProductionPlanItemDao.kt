package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProductionPlanItemEntity

/** 计划明细读写。 */
@Dao
interface ProductionPlanItemDao {
    @Insert
    suspend fun insert(entity: ProductionPlanItemEntity)

    @Query("SELECT * FROM production_plan_item WHERE plan_id = :planId ORDER BY sort_order")
    suspend fun listByPlan(planId: String): List<ProductionPlanItemEntity>

    @Query("SELECT COUNT(*) FROM production_plan_item")
    suspend fun count(): Int
}
