package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.culinary.core.database.entity.WorkPlanEntity
import kotlinx.coroutines.flow.Flow

/** 工作计划独立读写；完成记录不物理删除，专属回收站按状态查询。 */
@Dao
interface WorkPlanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WorkPlanEntity)

    @Query("UPDATE work_plan SET title = :title, description = :description, planned_date = :plannedDate, updated_at = :updatedAt WHERE id = :id AND status != 'COMPLETED'")
    suspend fun updateOpen(id: String, title: String, description: String?, plannedDate: String?, updatedAt: Long): Int

    @Query("SELECT * FROM work_plan WHERE id = :id")
    suspend fun findById(id: String): WorkPlanEntity?

    @Query("SELECT * FROM work_plan ORDER BY planned_date, created_at DESC")
    fun observeAll(): Flow<List<WorkPlanEntity>>

    @Query("SELECT * FROM work_plan WHERE status = 'OPEN' ORDER BY planned_date, created_at DESC")
    fun observeOpen(): Flow<List<WorkPlanEntity>>

    @Query("SELECT * FROM work_plan WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    fun observeCompleted(): Flow<List<WorkPlanEntity>>

    @Query("UPDATE work_plan SET status = 'IN_PROGRESS', updated_at = :updatedAt WHERE id = :id AND status = 'OPEN'")
    suspend fun start(id: String, updatedAt: Long): Int

    @Query("UPDATE work_plan SET status = 'COMPLETED', completed_at = :completedAt, updated_at = :completedAt WHERE id = :id AND status != 'COMPLETED'")
    suspend fun complete(id: String, completedAt: Long): Int

    @Query("UPDATE work_plan SET status = 'OPEN', completed_at = NULL, updated_at = :updatedAt WHERE id = :id AND status = 'COMPLETED'")
    suspend fun restore(id: String, updatedAt: Long): Int

    @Query("DELETE FROM work_plan WHERE id = :id AND status != 'COMPLETED'")
    suspend fun deleteOpen(id: String): Int

    @Query("DELETE FROM work_plan WHERE id = :id AND status = 'COMPLETED'")
    suspend fun permanentlyDeleteCompleted(id: String): Int

    @Query("SELECT COUNT(*) FROM work_plan WHERE seed_version = :version")
    suspend fun countSeed(version: String): Int

    @Query("DELETE FROM work_plan WHERE seed_version = :version")
    suspend fun deleteSeed(version: String): Int
}
