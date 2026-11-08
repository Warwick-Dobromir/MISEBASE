package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.culinary.core.database.entity.ArchiveCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ArchiveCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ArchiveCategoryEntity)

    @Update
    suspend fun update(entity: ArchiveCategoryEntity)

    @Query("SELECT * FROM archive_category WHERE id = :id")
    suspend fun findById(id: String): ArchiveCategoryEntity?

    @Query("SELECT * FROM archive_category WHERE status = 'ACTIVE' ORDER BY library, sort_order, name")
    fun observeAllActive(): Flow<List<ArchiveCategoryEntity>>

    @Query("SELECT * FROM archive_category WHERE status = 'ACTIVE' AND library = :library ORDER BY sort_order, name")
    suspend fun listActive(library: String): List<ArchiveCategoryEntity>

    @Query("SELECT * FROM archive_category ORDER BY library, sort_order, name")
    suspend fun listAll(): List<ArchiveCategoryEntity>

    @Query("SELECT * FROM archive_category WHERE test_batch = :testBatch ORDER BY library, sort_order")
    suspend fun listByTestBatch(testBatch: String): List<ArchiveCategoryEntity>

    @Query("SELECT COUNT(*) FROM archive_category WHERE test_batch = :testBatch")
    suspend fun countTestBatch(testBatch: String): Int

    @Query("DELETE FROM archive_category WHERE test_batch = :testBatch")
    suspend fun deleteTestBatch(testBatch: String): Int
}
