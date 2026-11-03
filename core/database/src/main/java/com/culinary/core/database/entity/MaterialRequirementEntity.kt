package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.ConversionStatusDb
import com.culinary.core.database.model.UnitDimensionDb

/**
 * 快照内需求汇总（A06 §7.4）。
 *
 * `available_qty_snapshot` 与 `shortage_qty` 是确认时刻的值，后续库存变化不回写；
 * 可选配料仍显示需求，但默认不计入缺口（O-02）。
 */
@Entity(
    tableName = "material_requirement",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshot_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
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
        Index(value = ["snapshot_id", "aggregation_key"], unique = true),
        Index(value = ["snapshot_id"]),
        Index(value = ["ingredient_id"]),
        Index(value = ["unit_id"]),
    ],
)
data class MaterialRequirementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "required_qty")
    val requiredQty: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "dimension")
    val dimension: UnitDimensionDb,
    @ColumnInfo(name = "aggregation_key")
    val aggregationKey: String,
    @ColumnInfo(name = "available_qty_snapshot")
    val availableQtySnapshot: Double = 0.0,
    @ColumnInfo(name = "shortage_qty")
    val shortageQty: Double = 0.0,
    @ColumnInfo(name = "is_optional")
    val isOptional: Boolean = false,
    @ColumnInfo(name = "conversion_status")
    val conversionStatus: ConversionStatusDb = ConversionStatusDb.OK,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
