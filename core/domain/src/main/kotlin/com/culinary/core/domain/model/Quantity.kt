package com.culinary.core.domain.model

/**
 * 数量 = 数值 + 单位，二者必须成对出现（A06 §3.1）。
 *
 * 只表达非负的物理量；库存调减、报废等方向由流水类型表达，不用负数数量。
 */
data class Quantity(
    val amount: Double,
    val unit: Unit,
) {
    init {
        require(amount.isFinite()) { "quantity amount must be finite, was $amount" }
        require(amount >= 0.0) { "quantity amount must be >= 0, was $amount" }
    }
}
