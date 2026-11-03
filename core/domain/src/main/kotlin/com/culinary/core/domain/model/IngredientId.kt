package com.culinary.core.domain.model

/**
 * 食材标识。只承诺非空不透明字符串；格式与生成规则属 D-005（仍为 `PROPOSED`）。
 */
@JvmInline
value class IngredientId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ingredient id must not be blank" }
    }

    override fun toString(): String = value
}
