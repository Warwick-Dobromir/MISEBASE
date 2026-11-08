package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 烹饪技术/制作标准/储存标准的专业内容行。
 *
 * 单字段内容用 section=字段 key、value=内容；可重复条目用 section=步骤/检查项，
 * label=标题、value=说明、sort_order=顺序。这样三类库可以各自扩展字段，
 * 又不需要把大量业务字段塞进主表。
 */
@Entity(
    tableName = "kitchen_reference_content",
    foreignKeys = [
        ForeignKey(
            entity = KitchenReferenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["reference_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["reference_id", "section", "sort_order"]),
        Index(value = ["reference_id"]),
    ],
)
data class KitchenReferenceContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "reference_id")
    val referenceId: String,
    @ColumnInfo(name = "section")
    val section: String,
    @ColumnInfo(name = "label")
    val label: String? = null,
    @ColumnInfo(name = "value")
    val value: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
)
