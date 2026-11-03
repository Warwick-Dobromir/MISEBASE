package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.EntityStatusDb

/**
 * 酱汁档案（A06 §4.1）。组成在版本与 `sauce_component`，不在本表。
 * `normalized_name` 的部分唯一索引由 `CulinarySchemaExtras` 创建。
 */
@Entity(
    tableName = "sauce",
    indices = [
        Index(value = ["status"]),
        Index(value = ["family"]),
    ],
)
data class SauceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "family")
    val family: String? = null,
    @ColumnInfo(name = "texture")
    val texture: String? = null,
    @ColumnInfo(name = "flavor_profile")
    val flavorProfile: String? = null,
    @ColumnInfo(name = "usage_notes")
    val usageNotes: String? = null,
    @ColumnInfo(name = "storage_notes")
    val storageNotes: String? = null,
    @ColumnInfo(name = "shelf_life_days")
    val shelfLifeDays: Int? = null,
    @ColumnInfo(name = "status")
    val status: EntityStatusDb = EntityStatusDb.ACTIVE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
