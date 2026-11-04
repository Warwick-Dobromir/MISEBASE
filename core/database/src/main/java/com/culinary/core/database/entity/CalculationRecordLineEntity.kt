package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计算记录需求行（MB-D-017 / Schema v2）。
 *
 * 每行保存食材、数量与单位；同一记录内 `line_no` 唯一，`(ingredient_id, unit_id)` 唯一。
 * 记录确认后，行内容不可新增、修改或删除。
 */
@Entity(
    tableName = "calculation_record_line",
    foreignKeys = [
        ForeignKey(
            entity = CalculationRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["calculation_record_id"],
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
        Index(value = ["calculation_record_id"]),
        Index(value = ["ingredient_id"]),
        Index(value = ["unit_id"]),
        Index(value = ["calculation_record_id", "line_no"], unique = true),
        Index(value = ["calculation_record_id", "ingredient_id", "unit_id"], unique = true),
    ],
)
data class CalculationRecordLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "calculation_record_id")
    val calculationRecordId: String,
    @ColumnInfo(name = "line_no")
    val lineNo: Int,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "is_optional")
    val isOptional: Boolean,
)
