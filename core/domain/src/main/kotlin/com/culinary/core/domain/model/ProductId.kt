package com.culinary.core.domain.model

/**
 * 商品标识（采购 SKU）。库存与流水按商品记账，需求按食材汇总；
 * 二者通过 `product.ingredient_id` 关联，领域层不在此处展开。
 */
@JvmInline
value class ProductId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "product id must not be blank" }
    }

    override fun toString(): String = value
}
