package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProcurementRequirementEntity

/** 采购需求读写。 */
@Dao
interface ProcurementRequirementDao {
    @Insert
    suspend fun insert(entity: ProcurementRequirementEntity)

    @Query("SELECT * FROM procurement_requirement WHERE plan_id = :planId")
    suspend fun listByPlan(planId: String): List<ProcurementRequirementEntity>

    @Query("SELECT COUNT(*) FROM procurement_requirement")
    suspend fun count(): Int
}
