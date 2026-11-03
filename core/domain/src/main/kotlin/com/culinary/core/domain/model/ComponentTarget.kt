package com.culinary.core.domain.model

/**
 * 配方组成的目标（A06 §4.6，`component_type` 的三选一）。
 *
 * 阶段 2 只建模 `INGREDIENT` 与 `RECIPE`；`SAUCE` 目标需要酱汁版本模型，
 * 随酱汁功能（阶段 8 前后）一起加入，避免现在定义一个无法校验的半成品。
 */
sealed interface ComponentTarget {
    data class Ingredient(
        val id: IngredientId,
    ) : ComponentTarget

    /** 子菜谱固定引用具体版本，不跟随"当前版本"漂移（D-006）。 */
    data class Recipe(
        val recipeId: RecipeId,
        val versionId: RecipeVersionId,
    ) : ComponentTarget
}
