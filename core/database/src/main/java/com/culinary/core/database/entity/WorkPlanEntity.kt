package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 工作计划独立于制作计划保存。完成记录保留在本表中，供专属计划回收站查询。
 */
@Entity(
    tableName = "work_plan",
    indices = [Index(value = ["status", "planned_date"]), Index(value = ["seed_version"])],
)
data class WorkPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "planned_date")
    val plannedDate: String? = null,
    @ColumnInfo(name = "status")
    val status: String = STATUS_OPEN,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "seed_id")
    val seedId: String? = null,
    @ColumnInfo(name = "seed_version")
    val seedVersion: String? = null,
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}
