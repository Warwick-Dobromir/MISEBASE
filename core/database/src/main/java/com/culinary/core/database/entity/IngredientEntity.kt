package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.EntityStatusDb

/**
 * 食材档案（A06 §3.2）。
 *
 * `normalized_name` 的唯一性只在未删除行上成立，Room 不支持部分索引，
 * 因此由 `CulinarySchemaExtras` 创建 `WHERE deleted_at IS NULL` 的部分唯一索引。
 * `status` 的 CHECK 同样由触发器实现。
 */
@Entity(
    tableName = "ingredient",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["default_unit_id"]),
    ],
)
data class IngredientEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "scientific_name")
    val scientificName: String? = null,
    @ColumnInfo(name = "category")
    val category: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "flavor_profile")
    val flavorProfile: String? = null,
    @ColumnInfo(name = "seasonality")
    val seasonality: String? = null,
    @ColumnInfo(name = "origin")
    val origin: String? = null,
    @ColumnInfo(name = "selection_notes")
    val selectionNotes: String? = null,
    @ColumnInfo(name = "storage_notes")
    val storageNotes: String? = null,
    @ColumnInfo(name = "prep_notes")
    val prepNotes: String? = null,
    @ColumnInfo(name = "allergen_notes")
    val allergenNotes: String? = null,
    @ColumnInfo(name = "default_unit_id")
    val defaultUnitId: String? = null,
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
