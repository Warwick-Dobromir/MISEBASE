package com.culinary.core.domain.model

/**
 * 库存扣减需求（按商品）。
 *
 * 库存按商品记账，需求按食材汇总；两者的对应关系由商品选择在用例层完成，
 * 领域规则只接收已选定的商品数量。
 */
data class ProductRequirement(
    val productId: ProductId,
    val quantity: Quantity,
    val label: String? = null,
) {
    init {
        require(quantity.amount > 0.0) { "product requirement must be > 0, was ${quantity.amount}" }
    }
}
