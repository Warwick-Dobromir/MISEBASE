package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 食材级换算（A06 §3.4）：`1 from = factor × to`。
 *
 * 只对同一食材有效；反向换算由应用用 `1/factor` 计算，不存两行。
 * `factor > 0` 与 `from != to` 由 `CulinarySchemaExtras` 的触发器兜底。
 */
@Entity(
    tableName = "ingredient_unit_conversion",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            value = ["ingredient_id", "from_unit_id", "to_unit_id"],
            unique = true,
        ),
        Index(value = ["ingredient_id"]),
        // 外键维护索引：单位被删除/更新时避免全表扫描（A06 §12 是"最低集合"，可增补）。
        Index(value = ["from_unit_id"]),
        Index(value = ["to_unit_id"]),
    ],
)
data class IngredientUnitConversionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "ingredient_id")
    val ingredientId: String,
    @ColumnInfo(name = "from_unit_id")
    val fromUnitId: String,
    @ColumnInfo(name = "to_unit_id")
    val toUnitId: String,
    @ColumnInfo(name = "factor")
    val factor: Double,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "source")
    val source: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
