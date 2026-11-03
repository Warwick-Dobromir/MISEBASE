package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.PlanItemStatusDb

/**
 * 计划明细（A06 §7.2）：**固定** `recipe_version_id`，不跟随菜谱当前版本漂移（D-007）。
 * `recipe_version.recipe_id = recipe_id` 及"版本已发布"由应用校验。
 */
@Entity(
    tableName = "production_plan_item",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipe_version_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "sort_order"]),
        Index(value = ["recipe_version_id"]),
        Index(value = ["recipe_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class ProductionPlanItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "plan_id")
    val planId: String,
    @ColumnInfo(name = "recipe_id")
    val recipeId: String,
    @ColumnInfo(name = "recipe_version_id")
    val recipeVersionId: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "status")
    val status: PlanItemStatusDb = PlanItemStatusDb.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
