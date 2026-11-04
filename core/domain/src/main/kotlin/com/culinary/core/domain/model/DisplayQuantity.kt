package com.culinary.core.domain.model

/**
 * 显示用数量：按目标单位精度四舍五入后的结果。
 *
 * [isApproximate] 表示换算链中存在近似单位（如 cup/tbsp），UI 必须显示"近似"标记。
 * 计算与持久化仍使用未舍入的 [Quantity]；本类型只用于展示。
 */
data class DisplayQuantity(
    val amount: Double,
    val unit: Unit,
    val isApproximate: Boolean,
) {
    init {
        require(amount.isFinite()) { "display amount must be finite, was $amount" }
        require(amount >= 0.0) { "display amount must be >= 0, was $amount" }
    }
}
