package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ProductionPlanEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM production_plan ORDER BY planned_date, created_at DESC")
    suspend fun findAll(): List<ProductionPlanEntity>

    @Query("SELECT * FROM production_plan WHERE planned_date = :date ORDER BY created_at DESC")
    suspend fun findByPlannedDate(date: String): List<ProductionPlanEntity>

    @Query("UPDATE production_plan SET status = :status, completed_at = :completedAt, updated_at = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun updateStatus(id: String, status: com.culinary.core.database.model.PlanStatusDb, completedAt: Long?, updatedAt: Long)

    @Query("SELECT * FROM production_plan ORDER BY planned_date, created_at DESC")
    fun observeAll(): Flow<List<ProductionPlanEntity>>

    @Query("UPDATE production_plan SET title = :title, planned_date = :plannedDate, notes = :notes, updated_at = :updatedAt, revision = revision + 1 WHERE id = :id AND status != 'COMPLETED'")
    suspend fun updateOpen(id: String, title: String, plannedDate: String?, notes: String?, updatedAt: Long): Int

    @Query("UPDATE production_plan SET status = 'COMPLETED', completed_at = :completedAt, updated_at = :completedAt, revision = revision + 1 WHERE id = :id AND status != 'COMPLETED'")
    suspend fun complete(id: String, completedAt: Long): Int

    @Query("UPDATE production_plan SET status = 'DRAFT', completed_at = NULL, updated_at = :updatedAt, revision = revision + 1 WHERE id = :id AND status = 'COMPLETED'")
    suspend fun restoreCompleted(id: String, updatedAt: Long): Int

    @Query("DELETE FROM production_plan WHERE id = :id AND status != 'COMPLETED'")
    suspend fun deleteOpen(id: String): Int

    @Query("DELETE FROM production_plan WHERE id = :id AND status = 'COMPLETED'")
    suspend fun permanentlyDeleteCompleted(id: String): Int

    @Query("SELECT COUNT(*) FROM production_plan WHERE seed_version = :version")
    suspend fun countSeed(version: String): Int

    @Query("DELETE FROM production_plan WHERE seed_version = :version")
    suspend fun deleteSeed(version: String): Int

    @Query("SELECT COUNT(*) FROM production_plan")
    suspend fun count(): Int

    @Query("DELETE FROM production_plan WHERE id = :id")
    suspend fun deleteById(id: String)
}
