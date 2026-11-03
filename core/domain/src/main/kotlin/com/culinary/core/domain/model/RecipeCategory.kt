package com.culinary.core.domain.model

/** 菜谱分类（A06 §2）；高汤/预制件通过分类表达，不建独立库。 */
enum class RecipeCategory {
    STOCK,
    PREP,
    COMPONENT,
    DISH,
    OTHER,
}
