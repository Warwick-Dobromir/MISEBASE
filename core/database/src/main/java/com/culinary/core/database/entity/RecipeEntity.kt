package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.RecipeCategoryDb

/**
 * 菜谱档案（A06 §4.4）。高汤/预制件用分类表达，不建独立表。
 * `normalized_name` 的部分唯一索引由 `CulinarySchemaExtras` 创建。
 */
@Entity(
    tableName = "recipe",
    indices = [
        Index(value = ["category", "status"]),
    ],
)
data class RecipeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "category")
    val category: RecipeCategoryDb = RecipeCategoryDb.OTHER,
    @ColumnInfo(name = "cuisine")
    val cuisine: String? = null,
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "prep_minutes")
    val prepMinutes: Int? = null,
    @ColumnInfo(name = "cook_minutes")
    val cookMinutes: Int? = null,
    @ColumnInfo(name = "difficulty")
    val difficulty: String? = null,
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
