package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.culinary.core.database.entity.KitchenRelationEntity
import kotlinx.coroutines.flow.Flow

/** 厨房档案关系读写。 */
@Dao
interface KitchenRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relations: List<KitchenRelationEntity>)

    @Query(
        "SELECT * FROM kitchen_relation WHERE source_record_id = :recordId " +
            "ORDER BY sort_order, relation_label, target_label",
    )
    fun observeBySource(recordId: String): Flow<List<KitchenRelationEntity>>

    @Query(
        "SELECT * FROM kitchen_relation WHERE source_record_id = :recordId " +
            "ORDER BY sort_order, relation_label, target_label",
    )
    suspend fun listBySource(recordId: String): List<KitchenRelationEntity>

    @Query(
        "DELETE FROM kitchen_relation WHERE source_record_id = :sourceId " +
            "AND target_kind = :targetKind",
    )
    suspend fun deleteBySourceAndTargetKind(
        sourceId: String,
        targetKind: String,
    )

    @Query("DELETE FROM kitchen_relation WHERE seed_version = :seedVersion")
    suspend fun deleteBySeedVersion(seedVersion: String): Int

    @Query("DELETE FROM kitchen_relation WHERE seed_version IS NOT NULL")
    suspend fun deleteAllSeed(): Int

    @Query("SELECT COUNT(*) FROM kitchen_relation WHERE seed_version IS NOT NULL")
    suspend fun countSeed(): Int

    @Query("SELECT COUNT(*) FROM kitchen_relation")
    suspend fun count(): Int
}
