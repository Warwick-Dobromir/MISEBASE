package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.KitchenReferenceEntity
import com.culinary.core.database.model.KitchenReferenceKindDb
import kotlinx.coroutines.flow.Flow

/** 厨房新增参考资料读写。 */
@Dao
interface KitchenReferenceDao {
    @Insert
    suspend fun insert(entity: KitchenReferenceEntity)

    @Update
    suspend fun update(entity: KitchenReferenceEntity)

    @Query("SELECT * FROM kitchen_reference WHERE id = :id")
    suspend fun findById(id: String): KitchenReferenceEntity?

    @Query(
        "SELECT * FROM kitchen_reference WHERE kind = :kind AND deleted_at IS NULL " +
            "AND status = 'ACTIVE' ORDER BY normalized_name, id",
    )
    fun observeActive(kind: KitchenReferenceKindDb): Flow<List<KitchenReferenceEntity>>

    @Query(
        "SELECT * FROM kitchen_reference WHERE deleted_at IS NULL AND status = 'ACTIVE' " +
            "ORDER BY kind, normalized_name, id",
    )
    fun observeAllActive(): Flow<List<KitchenReferenceEntity>>

    @Query(
        "SELECT * FROM kitchen_reference WHERE kind = :kind AND normalized_name = :normalizedName " +
            "AND deleted_at IS NULL",
    )
    suspend fun findActiveByNormalizedName(
        kind: KitchenReferenceKindDb,
        normalizedName: String,
    ): KitchenReferenceEntity?

    @Query(
        "UPDATE kitchen_reference SET status = 'ARCHIVED', updated_at = :at, revision = revision + 1 " +
            "WHERE id = :id",
    )
    suspend fun archive(
        id: String,
        at: Long,
    ): Int

    @Query("DELETE FROM kitchen_reference WHERE seed_version = :seedVersion")
    suspend fun deleteSeed(seedVersion: String): Int

    @Query("DELETE FROM kitchen_reference WHERE seed_version IS NOT NULL")
    suspend fun deleteAllSeed(): Int

    @Query("SELECT COUNT(*) FROM kitchen_reference WHERE seed_version = :seedVersion")
    suspend fun countSeed(seedVersion: String): Int

    @Query("SELECT COUNT(*) FROM kitchen_reference")
    suspend fun count(): Int
}
