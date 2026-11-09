package com.culinary.core.domain.usecase.recipe

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit

/** 菜谱组成行输入目标；[RecipeCurrent] 在保存/发布时解析为明确版本。 */
sealed interface RecipeLineTarget {
    data class Ingredient(
        val id: IngredientId,
    ) : RecipeLineTarget

    /** 草稿指向子菜谱的当前版本；写入时由 [RecipeVersionPinRule] 固定。 */
    data class RecipeCurrent(
        val recipeId: RecipeId,
    ) : RecipeLineTarget

    data class RecipePinned(
        val recipeId: RecipeId,
        val versionId: RecipeVersionId,
    ) : RecipeLineTarget

    /** 子酱汁固定版本引用（Stage 3 打通读取/映射，编辑入口在归档边界提交接入）。 */
    data class SaucePinned(
        val sauceId: SauceId,
        val versionId: SauceVersionId,
    ) : RecipeLineTarget
}

/** 一行组成：数量 + 单位 + 可选标记。 */
data class RecipeLineInput(
    val target: RecipeLineTarget,
    val amount: Double,
    val unit: Unit,
    val isOptional: Boolean = false,
)

/** 草稿版本输入（不含档案名称/分类，档案由 SaveRecipeUseCase 维护）。 */
data class RecipeVersionInput(
    val recipeId: RecipeId,
    val yieldAmount: Double,
    val yieldUnit: Unit,
    val servings: Int,
    val components: List<RecipeLineInput>,
    val instructions: String? = null,
    val stepsJson: String? = null,
    /** 非空表示更新同一份未发布草稿；为空表示创建新草稿。 */
    val versionId: RecipeVersionId? = null,
)
