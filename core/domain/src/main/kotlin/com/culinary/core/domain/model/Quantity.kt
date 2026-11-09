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

    /** 两个数量是否可直接比较（同一计量维度）。 */
    fun isSameDimensionAs(other: Quantity): Boolean = unit.isSameDimensionAs(other.unit)

    /** 同一维度下比较基准量；调用方必须先确认 [isSameDimensionAs]。 */
    fun isAtMost(other: Quantity): Boolean {
        require(isSameDimensionAs(other)) { "quantities must share the same dimension" }
        return amount * unit.baseFactor <= other.amount * other.unit.baseFactor
    }
}
