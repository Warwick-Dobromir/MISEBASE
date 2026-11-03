package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.MaterialRequirementEntity

/** 快照需求汇总读写。 */
@Dao
interface MaterialRequirementDao {
    @Insert
    suspend fun insert(entity: MaterialRequirementEntity)

    @Query("SELECT * FROM material_requirement WHERE snapshot_id = :snapshotId")
    suspend fun listBySnapshot(snapshotId: String): List<MaterialRequirementEntity>

    @Query("SELECT COUNT(*) FROM material_requirement")
    suspend fun count(): Int
}
