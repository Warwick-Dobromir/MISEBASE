package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveCategoryAssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ArchiveCategoryAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArchiveCategoryAssignmentEntity)

    @Update
    suspend fun update(entity: ArchiveCategoryAssignmentEntity)

    @Query("SELECT * FROM archive_category_assignment")
    fun observeAll(): Flow<List<ArchiveCategoryAssignmentEntity>>

    @Query("SELECT * FROM archive_category_assignment")
    suspend fun listAll(): List<ArchiveCategoryAssignmentEntity>

    @Query("SELECT * FROM archive_category_assignment WHERE record_kind = :kind AND record_id = :recordId")
    suspend fun findByRecord(
        kind: String,
        recordId: String,
    ): ArchiveCategoryAssignmentEntity?

    @Query("SELECT * FROM archive_category_assignment WHERE category_id = :categoryId")
    suspend fun listByCategory(categoryId: String): List<ArchiveCategoryAssignmentEntity>

    @Query("DELETE FROM archive_category_assignment WHERE record_kind = :kind AND record_id = :recordId")
    suspend fun deleteByRecord(
        kind: String,
        recordId: String,
    ): Int

    @Query("DELETE FROM archive_category_assignment WHERE category_id = :categoryId")
    suspend fun deleteByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM archive_category_assignment")
    suspend fun count(): Int
}
