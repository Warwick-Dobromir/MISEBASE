package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ComponentTypeDb

/**
 * 需求来源（A06 §7.5）：回答"这个需求从哪来"，来源明细随需求或计划项删除级联。
 */
@Entity(
    tableName = "material_requirement_source",
    foreignKeys = [
        ForeignKey(
            entity = MaterialRequirementEntity::class,
            parentColumns = ["id"],
            childColumns = ["requirement_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductionPlanItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["requirement_id"]),
        Index(value = ["plan_item_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class MaterialRequirementSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "requirement_id")
    val requirementId: String,
    @ColumnInfo(name = "plan_item_id")
    val planItemId: String,
    @ColumnInfo(name = "source_path")
    val sourcePath: String,
    @ColumnInfo(name = "component_type")
    val componentType: ComponentTypeDb,
    @ColumnInfo(name = "component_id")
    val componentId: String,
    @ColumnInfo(name = "component_version_id")
    val componentVersionId: String? = null,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "depth")
    val depth: Int = 0,
    @ColumnInfo(name = "is_optional")
    val isOptional: Boolean = false,
    @ColumnInfo(name = "note")
    val note: String? = null,
)
