package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.MaterialRequirementSourceEntity

/** 需求来源明细读写。 */
@Dao
interface MaterialRequirementSourceDao {
    @Insert
    suspend fun insert(entity: MaterialRequirementSourceEntity)

    @Query("SELECT * FROM material_requirement_source WHERE requirement_id = :requirementId")
    suspend fun listByRequirement(requirementId: String): List<MaterialRequirementSourceEntity>

    @Query("SELECT COUNT(*) FROM material_requirement_source")
    suspend fun count(): Int
}
