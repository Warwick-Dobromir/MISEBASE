package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProductionPlanEntity

/** 制作计划读写。 */
@Dao
interface ProductionPlanDao {
    @Insert
    suspend fun insert(entity: ProductionPlanEntity)

    @Query("SELECT * FROM production_plan WHERE id = :id")
    suspend fun findById(id: String): ProductionPlanEntity?

    @Query("UPDATE production_plan SET current_snapshot_id = :snapshotId WHERE id = :id")
    suspend fun setCurrentSnapshot(
        id: String,
        snapshotId: String?,
    )

    @Query("SELECT COUNT(*) FROM production_plan")
    suspend fun count(): Int

    @Query("DELETE FROM production_plan WHERE id = :id")
    suspend fun deleteById(id: String)
}
