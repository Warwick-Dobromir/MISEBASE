package com.culinary.core.domain.model

/**
 * 缺口行（需求 − 可用）。
 *
 * [shortfall] 为 0 表示满足；不可换算导致的"按 0 可用"也会体现为整行缺口，
 * 但调用方必须结合单位判断，不得跨维度直接比较数量。
 */
data class ShortfallLine(
    val ingredientId: IngredientId,
    val required: Quantity,
    val available: Quantity,
    val shortfall: Quantity,
    val isOptional: Boolean = false,
) {
    init {
        require(required.unit.id == available.unit.id) {
            "required and available must share a unit"
        }
        require(shortfall.unit.id == required.unit.id) {
            "shortfall must use the requirement unit"
        }
        require(shortfall.amount >= 0.0) { "shortfall must be >= 0, was ${shortfall.amount}" }
    }

    val isCovered: Boolean get() = shortfall.amount <= 0.0
}
