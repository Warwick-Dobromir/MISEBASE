package com.culinary.core.domain.model

/** 菜谱档案（A06 §4.4 的领域视图）；版本由 [RecipeVersion] 表达。 */
data class Recipe(
    val id: RecipeId,
    val name: String,
    val normalizedName: String,
    val category: RecipeCategory,
    val status: EntityStatus,
    val englishName: String? = null,
    val cuisine: String? = null,
    val description: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val difficulty: String? = null,
    val allergenNotes: String? = null,
    val ingredientSummary: String? = null,
    val seedNote: String? = null,
    val seedId: String? = null,
    val seedVersion: String? = null,
) {
    init {
        require(name.isNotBlank()) { "recipe name must not be blank" }
        require(normalizedName.isNotBlank()) { "recipe normalizedName must not be blank" }
    }

    val isSeed: Boolean get() = seedId != null
}
