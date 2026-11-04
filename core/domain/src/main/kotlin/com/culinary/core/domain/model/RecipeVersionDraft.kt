package com.culinary.core.domain.model

/**
 * 菜谱草稿版本（MB-D-013）。
 *
 * 草稿可以引用"当前版本"；[com.culinary.core.domain.rule.recipe.RecipeVersionPinRule]
 * 在发布时把引用解析为明确版本 ID，生成不可变的 [RecipeVersion]。
 */
data class RecipeVersionDraft(
    val id: RecipeVersionId,
    val recipeId: RecipeId,
    val versionNo: Int,
    val yield: Quantity,
    val components: List<RecipeComponentDraft>,
    val servings: Int = 1,
) {
    init {
        require(versionNo > 0) { "draft versionNo must be > 0, was $versionNo" }
        require(servings > 0) { "draft servings must be > 0, was $servings" }
        require(yield.amount > 0.0) { "draft yield must be > 0, was ${yield.amount}" }
        require(components.map { it.lineNo }.toSet().size == components.size) {
            "draft version ${id.value} contains duplicate component lineNo"
        }
    }
}
