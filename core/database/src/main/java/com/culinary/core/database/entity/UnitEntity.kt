package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.UnitDimensionDb

/**
 * 计量单位表（A06 §3.1）。
 *
 * `CHECK` 约束（dimension 取值、base_factor > 0、display_precision 0..6）由
 * `CulinarySchemaExtras` 的触发器实现：Room 不能把 CHECK 写进自动建表语句。
 */
@Entity(
    tableName = "unit",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["dimension", "is_active"]),
    ],
)
data class UnitEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "symbol")
    val symbol: String,
    @ColumnInfo(name = "dimension")
    val dimension: UnitDimensionDb,
    @ColumnInfo(name = "base_factor")
    val baseFactor: Double,
    @ColumnInfo(name = "is_base")
    val isBase: Boolean,
    @ColumnInfo(name = "is_approximate")
    val isApproximate: Boolean,
    @ColumnInfo(name = "display_precision")
    val displayPrecision: Int,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
