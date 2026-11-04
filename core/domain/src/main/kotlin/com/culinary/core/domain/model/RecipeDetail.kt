package com.culinary.core.domain.model

/**
 * 菜谱详情投影：档案 + 当前版本 + 版本历史 + 已解析的组成行标签。
 *
 * [servings] 是当前版本的基准份数；展开按"目标份数 ÷ 基准份数"缩放。
 */
data class RecipeDetail(
    val recipe: Recipe,
    val currentVersion: RecipeVersion?,
    val versions: List<RecipeVersion>,
    val components: List<RecipeComponentView>,
    val baseServings: Int,
) {
    val hasPublishedVersion: Boolean get() = versions.any { it.isPublished }
    val hasDraftVersion: Boolean get() = versions.any { it.status == VersionStatus.DRAFT }
}

/** 组成行展示模型；引用子菜谱/酱汁时保留目标 id 与版本号，便于核对版本固定。 */
data class RecipeComponentView(
    val lineNo: Int,
    val label: String,
    val kind: RecipeComponentKind,
    val quantity: Quantity,
    val isOptional: Boolean,
    val referencedVersionNo: Int? = null,
    val ingredientId: IngredientId? = null,
    val recipeId: RecipeId? = null,
    val recipeVersionId: RecipeVersionId? = null,
    val sauceId: SauceId? = null,
    val sauceVersionId: SauceVersionId? = null,
)

/** 组成行目标种类；UI 用于区分"食材 / 子菜谱 / 子酱汁"。 */
enum class RecipeComponentKind {
    INGREDIENT,
    RECIPE,
    SAUCE,
}

/**
 * 展开结果：只做展示，不做库存对比或采购清单（Stage 4）。
 * 可选配料单独标记，默认不参与缺口计算（MB-D-015）。
 */
data class ExpandedRecipe(
    val baseServings: Int,
    val targetServings: Int,
    val lines: List<ExpandedRecipeLine>,
)

data class ExpandedRecipeLine(
    val label: String,
    val quantity: Quantity,
    val isOptional: Boolean,
)
