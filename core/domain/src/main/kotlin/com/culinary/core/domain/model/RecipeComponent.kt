package com.culinary.core.domain.model

/** 配方组成行（A06 §4.6）。[lineNo] 在同一个版本内唯一。 */
data class RecipeComponent(
    val lineNo: Int,
    val target: ComponentTarget,
    val quantity: Quantity,
    val isOptional: Boolean = false,
) {
    init {
        require(lineNo > 0) { "component lineNo must be > 0, was $lineNo" }
        require(quantity.amount > 0.0) { "component quantity must be > 0, was ${quantity.amount}" }
    }
}
