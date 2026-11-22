package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 食材档案的不可变历史快照；不与酱汁/菜谱版本表共用。 */
@Entity(
    tableName = "ingredient_version",
    indices = [Index(value = ["ingredient_id", "version_no"], unique = true)],
)
data class IngredientVersionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "ingredient_id") val ingredientId: String,
    @ColumnInfo(name = "version_no") val versionNo: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "scientific_name") val scientificName: String?,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "flavor_profile") val flavorProfile: String?,
    @ColumnInfo(name = "culinary_use") val culinaryUse: String?,
    @ColumnInfo(name = "edible_parts") val edibleParts: String?,
    @ColumnInfo(name = "seasonality") val seasonality: String?,
    @ColumnInfo(name = "origin") val origin: String?,
    @ColumnInfo(name = "selection_notes") val selectionNotes: String?,
    @ColumnInfo(name = "storage_notes") val storageNotes: String?,
    @ColumnInfo(name = "prep_notes") val prepNotes: String?,
    @ColumnInfo(name = "allergen_notes") val allergenNotes: String?,
    @ColumnInfo(name = "default_unit_id") val defaultUnitId: String?,
    @ColumnInfo(name = "english_name") val englishName: String?,
    @ColumnInfo(name = "example_products") val exampleProducts: String?,
    @ColumnInfo(name = "seed_note") val seedNote: String?,
    @ColumnInfo(name = "seed_id") val seedId: String?,
    @ColumnInfo(name = "seed_version") val seedVersion: String?,
    @ColumnInfo(name = "ingredient_status") val ingredientStatus: String,
    @ColumnInfo(name = "source_created_at") val sourceCreatedAt: Long,
    @ColumnInfo(name = "source_updated_at") val sourceUpdatedAt: Long,
    @ColumnInfo(name = "source_deleted_at") val sourceDeletedAt: Long?,
    @ColumnInfo(name = "source_revision") val sourceRevision: Int,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
