package com.culinary.core.domain.model

/**
 * 需求行（展开/汇总后的一行食材需求）。
 *
 * 与 [com.culinary.core.domain.rule.recipe.ExplodedRequirement] 的区别：
 * 本类型是**计算记录**与采购清单的稳定契约，不携带展开过程信息；
 * 可选标记沿路径传播（A04 §3.3）。
 */
data class RequirementLine(
    val ingredientId: IngredientId,
    val quantity: Quantity,
    val isOptional: Boolean = false,
) {
    init {
        require(quantity.amount > 0.0) { "requirement quantity must be > 0, was ${quantity.amount}" }
    }
}
