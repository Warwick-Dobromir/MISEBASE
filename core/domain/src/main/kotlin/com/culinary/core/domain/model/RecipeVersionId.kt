package com.culinary.core.domain.model

/**
 * 菜谱版本标识。计划项与配方引用固定版本号，不跟随"当前版本"漂移（D-006）。
 */
@JvmInline
value class RecipeVersionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "recipe version id must not be blank" }
    }

    override fun toString(): String = value
}
