package com.culinary.core.domain.model

/** 草稿组成行；发布后转换为 [RecipeComponent]（目标版本已固定）。 */
data class RecipeComponentDraft(
    val lineNo: Int,
    val target: ComponentTargetDraft,
    val quantity: Quantity,
    val isOptional: Boolean = false,
) {
    init {
        require(lineNo > 0) { "draft component lineNo must be > 0, was $lineNo" }
        require(quantity.amount > 0.0) { "draft component quantity must be > 0, was ${quantity.amount}" }
    }
}
