package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 厨房档案之间的显式关联。
 *
 * 目标标签在导入时快照，避免跨六张表做多态 join；目标 ID 仍用于跳转。
 * 用户手工关系 `seed_version` 为 null，测试种子关系按版本单独清理。
 */
@Entity(
    tableName = "kitchen_relation",
    indices = [
        Index(value = ["source_record_id"]),
        Index(value = ["target_record_id"]),
        Index(value = ["seed_version"]),
    ],
)
data class KitchenRelationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "source_record_id")
    val sourceRecordId: String,
    @ColumnInfo(name = "source_kind")
    val sourceKind: String,
    @ColumnInfo(name = "target_record_id")
    val targetRecordId: String,
    @ColumnInfo(name = "target_kind")
    val targetKind: String,
    @ColumnInfo(name = "target_label")
    val targetLabel: String,
    @ColumnInfo(name = "relation_label")
    val relationLabel: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "seed_version")
    val seedVersion: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
