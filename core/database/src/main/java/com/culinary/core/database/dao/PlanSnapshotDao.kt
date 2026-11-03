package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProductionPlanSnapshotEntity

/**
 * 计划快照读写：只有 `insert` 与查询。
 * UPDATE/DELETE 在数据库层被触发器拒绝（D-007），这里不提供方法。
 */
@Dao
interface PlanSnapshotDao {
    @Insert
    suspend fun insert(entity: ProductionPlanSnapshotEntity)

    @Query("SELECT * FROM production_plan_snapshot WHERE id = :id")
    suspend fun findById(id: String): ProductionPlanSnapshotEntity?

    @Query("SELECT * FROM production_plan_snapshot WHERE plan_id = :planId ORDER BY revision_no")
    suspend fun listByPlan(planId: String): List<ProductionPlanSnapshotEntity>

    @Query("SELECT COUNT(*) FROM production_plan_snapshot")
    suspend fun count(): Int
}
