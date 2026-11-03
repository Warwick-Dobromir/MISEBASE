package com.culinary.core.domain.model

/**
 * 菜谱标识（菜谱本身，不含版本）。只承诺非空不透明字符串；格式属 D-005。
 */
@JvmInline
value class RecipeId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "recipe id must not be blank" }
    }

    override fun toString(): String = value
}
