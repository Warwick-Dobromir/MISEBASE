package com.culinary.core.domain.model

/**
 * 草稿阶段的组成目标（MB-D-013）。
 *
 * 草稿允许指向子菜谱的"当前版本"；**发布时必须解析成明确的版本 ID**，
 * 写入后即为 [ComponentTarget.Recipe]，不再跟随新版本。
 */
sealed interface ComponentTargetDraft {
    data class Ingredient(
        val id: IngredientId,
    ) : ComponentTargetDraft

    /** 草稿指向"当前版本"：发布时解析，不落库为这种形态。 */
    data class RecipeCurrent(
        val recipeId: RecipeId,
    ) : ComponentTargetDraft

    /** 已固定版本的引用（重新发布/复制草稿时可能出现）。 */
    data class RecipePinned(
        val recipeId: RecipeId,
        val versionId: RecipeVersionId,
    ) : ComponentTargetDraft
}
