package com.culinary.core.domain.model

/**
 * 计量单位（A06 §3.1）。
 *
 * [baseFactor] 是到同维度基准单位的因子；COUNT 维度固定为 1，且不通过该因子互转。
 * [isApproximate] 表示换算本身是近似值（如 cup/tbsp），UI 必须显示"近似"标记。
 */
data class Unit(
    val id: UnitId,
    val code: String,
    val name: String,
    val symbol: String,
    val dimension: UnitDimension,
    val baseFactor: Double,
    val isBase: Boolean,
    val isApproximate: Boolean,
    val displayPrecision: Int,
) {
    init {
        require(code.isNotBlank()) { "unit code must not be blank" }
        require(baseFactor > 0.0) { "unit baseFactor must be > 0, was $baseFactor" }
        require(displayPrecision in 0..6) { "displayPrecision must be in 0..6, was $displayPrecision" }
    }

    fun isSameDimensionAs(other: Unit): Boolean = dimension == other.dimension
}
