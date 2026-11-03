package com.culinary.core.domain.model

/** 菜谱档案（A06 §4.4 的领域视图）；版本由 [RecipeVersion] 表达。 */
data class Recipe(
    val id: RecipeId,
    val name: String,
    val normalizedName: String,
    val category: RecipeCategory,
    val status: EntityStatus,
) {
    init {
        require(name.isNotBlank()) { "recipe name must not be blank" }
        require(normalizedName.isNotBlank()) { "recipe normalizedName must not be blank" }
    }
}
