package com.culinary.core.domain.model

/**
 * 食材级换算（A06 §3.4）：`1 from = factor × to`。
 *
 * 只对"同一食材"有效；反向换算由调用方用 `1/factor` 计算，不要求存两行。
 * [note] 记录依据（如品牌密度、实测），UI 必须与"近似"标记一起展示。
 */
data class IngredientUnitConversion(
    val fromUnit: UnitId,
    val toUnit: UnitId,
    val factor: Double,
    val note: String? = null,
) {
    init {
        require(fromUnit != toUnit) { "conversion requires distinct units" }
        require(factor > 0.0) { "conversion factor must be > 0, was $factor" }
    }
}
