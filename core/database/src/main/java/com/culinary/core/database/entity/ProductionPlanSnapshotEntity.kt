package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划快照（A06 §7.3，D-007）：确认或刷新时追加一行，**只追加、不更新、不删除**。
 * `payload_json` 保存完整展开树与输入参数，`payload_hash` 用于防篡改与差异对比。
 */
@Entity(
    tableName = "production_plan_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "revision_no"], unique = true),
        Index(value = ["plan_id", "created_at"]),
    ],
)
data class ProductionPlanSnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "revision_no")
    val revisionNo: Int,
    @ColumnInfo(name = "engine_version")
    val engineVersion: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "payload_hash")
    val payloadHash: String,
    @ColumnInfo(name = "requirement_count")
    val requirementCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
