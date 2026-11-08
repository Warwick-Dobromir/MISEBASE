package com.culinary.core.domain.model

/**
 * 酱汁组成行（A06 §4.6，与菜谱组成行对称）。
 *
 * 目标只能是食材或**已固定版本**的子菜谱；酱汁嵌套酱汁不在阶段 2 建模，
 * 避免定义无法通过图校验的半成品。
 */
data class SauceComponent(
    val lineNo: Int,
    val target: ComponentTarget,
    val quantity: Quantity,
    val isOptional: Boolean = false,
    val labelOverride: String? = null,
    val labelOverrideEn: String? = null,
) {
    init {
        require(lineNo > 0) { "sauce component lineNo must be > 0, was $lineNo" }
        require(quantity.amount > 0.0) { "sauce component quantity must be > 0, was ${quantity.amount}" }
    }
}
