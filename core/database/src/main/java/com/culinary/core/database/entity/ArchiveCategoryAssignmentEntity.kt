package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 档案与分类节点的一对一归属；record_id 为多态档案 ID。 */
@Entity(
    tableName = "archive_category_assignment",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["record_kind", "record_id"], unique = true),
        Index(value = ["category_id"]),
    ],
)
data class ArchiveCategoryAssignmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "record_kind")
    val recordKind: String,
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
