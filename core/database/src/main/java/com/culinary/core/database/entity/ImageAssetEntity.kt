package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ImageRoleDb

/**
 * 图片元数据（A06 §9.1）。文件本体在应用私有目录，数据库只存相对路径与哈希。
 * "每个归属至多一张 PRIMARY 图"由 `CulinarySchemaExtras` 的部分唯一索引保证。
 */
@Entity(
    tableName = "image_asset",
    indices = [
        Index(value = ["relative_path"], unique = true),
        Index(value = ["owner_type", "owner_id", "sort_order"]),
    ],
)
data class ImageAssetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "owner_type")
    val ownerType: ImageOwnerTypeDb,
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "role")
    val role: ImageRoleDb,
    @ColumnInfo(name = "step_no")
    val stepNo: Int? = null,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "width")
    val width: Int? = null,
    @ColumnInfo(name = "height")
    val height: Int? = null,
    @ColumnInfo(name = "byte_size")
    val byteSize: Long,
    @ColumnInfo(name = "sha256")
    val sha256: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
