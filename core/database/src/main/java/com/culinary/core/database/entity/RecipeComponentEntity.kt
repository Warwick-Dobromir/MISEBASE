package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ComponentTypeDb

/**
 * 菜谱组成行（A06 §4.6），与 `sauce_component` 对称。
 * 三选一目标与数量检查由触发器实现；同一版本内 `line_no` 唯一。
 */
@Entity(
    tableName = "recipe_component",
    foreignKeys = [
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_version_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
        ),
        ForeignKey(
            entity = SauceEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenced_sauce_id"],
        ),
        ForeignKey(
            entity = SauceVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenced_sauce_version_id"],
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenced_recipe_id"],
        ),
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenced_recipe_version_id"],
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["recipe_version_id", "line_no"], unique = true),
        Index(value = ["ingredient_id"]),
        Index(value = ["referenced_sauce_id"]),
        Index(value = ["referenced_sauce_version_id"]),
        Index(value = ["referenced_recipe_id"]),
        Index(value = ["referenced_recipe_version_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class RecipeComponentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "recipe_version_id")
    val recipeVersionId: String,
    @ColumnInfo(name = "line_no")
    val lineNo: Int,
    @ColumnInfo(name = "component_type")
    val componentType: ComponentTypeDb,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String? = null,
    @ColumnInfo(name = "referenced_sauce_id")
    val referencedSauceId: String? = null,
    @ColumnInfo(name = "referenced_sauce_version_id")
    val referencedSauceVersionId: String? = null,
    @ColumnInfo(name = "referenced_recipe_id")
    val referencedRecipeId: String? = null,
    @ColumnInfo(name = "referenced_recipe_version_id")
    val referencedRecipeVersionId: String? = null,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "preparation_note")
    val preparationNote: String? = null,
    @ColumnInfo(name = "is_optional")
    val isOptional: Boolean = false,
    @ColumnInfo(name = "group_label")
    val groupLabel: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
