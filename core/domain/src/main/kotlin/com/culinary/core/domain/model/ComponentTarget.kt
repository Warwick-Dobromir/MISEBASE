package com.culinary.core.domain.model

/**
 * 配方组成的目标（A06 §4.6，`component_type` 的三选一）。
 *
 * - [Ingredient]：直接食材；
 * - [Recipe]：子菜谱固定引用具体版本，不跟随"当前版本"漂移（D-006 / MB-D-013）；
 * - [Sauce]：子酱汁固定引用具体版本（Stage 3 打通读取与映射，编辑入口后续开放）。
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

    /** 子酱汁固定引用具体版本；与菜谱引用同样在发布时锁定。 */
    data class Sauce(
        val sauceId: SauceId,
        val versionId: SauceVersionId,
    ) : ComponentTarget
}
