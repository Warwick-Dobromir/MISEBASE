package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.PlanStatusDb

/**
 * 制作计划（A06 §7.1）。
 *
 * `current_snapshot_id` 指向最近一次确认/刷新的快照；已确认计划的明细不可直接改，
 * 刷新生成新快照（D-007 / O-05）。
 */
@Entity(
    tableName = "production_plan",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["current_snapshot_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["status", "planned_date"]),
        Index(value = ["current_snapshot_id"]),
    ],
)
data class ProductionPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "planned_date")
    val plannedDate: String? = null,
    @ColumnInfo(name = "timezone")
    val timezone: String = "Asia/Hong_Kong",
    @ColumnInfo(name = "status")
    val status: PlanStatusDb = PlanStatusDb.DRAFT,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "current_snapshot_id")
    val currentSnapshotId: String? = null,
    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long? = null,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
