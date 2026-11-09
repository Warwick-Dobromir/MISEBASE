package com.culinary.feature.recipes

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.model.ExpandedRecipe
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.sauce.SauceOption
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱列表行。 */
data class RecipeRow(
    val id: String,
    val name: String,
    val category: RecipeCategory,
    val isActive: Boolean,
)

/** 菜谱列表状态。 */
data class RecipesListUiState(
    val rows: List<RecipeRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 菜谱详情状态：详情 + 按份数展开结果。 */
data class RecipeDetailUiState(
    val detail: RecipeDetail? = null,
    val categoryPath: List<String> = emptyList(),
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val isExpanding: Boolean = false,
    val expanded: ExpandedRecipe? = null,
    val expansionError: String? = null,
    val targetServings: String = "1",
    val error: String? = null,
    val relations: List<KitchenRelatedRecord> = emptyList(),
    /** 当前版本的结构化步骤（含每步时间）。 */
    val steps: List<KitchenStep> = emptyList(),
    /** 步骤序号（1 起）→ 步骤图片。 */
    val stepImages: Map<Int, ByteArray> = emptyMap(),
    /** 研发照片。 */
    val noteImages: List<ByteArray> = emptyList(),
)

/** 编辑器中的组成行；保留目标 id 以支持重新编辑与版本固定。 */
data class RecipeLineEditor(
    val key: String,
    val kind: RecipeComponentKind,
    val name: String,
    val amount: String,
    val unit: UnitModel,
    val isOptional: Boolean = false,
    val ingredientId: IngredientId? = null,
    val recipeId: RecipeId? = null,
    val recipeVersionId: RecipeVersionId? = null,
    val recipeVersionNo: Int? = null,
    val sauceId: SauceId? = null,
    val sauceVersionId: SauceVersionId? = null,
    val sauceVersionNo: Int? = null,
)

/** 制作步骤的编辑状态（与酱汁同构：编号 / 标题 / 说明 / 时间）。 */
data class RecipeStepEditor(
    val key: String,
    val title: String = "",
    val body: String = "",
    /** 步骤时间（秒）；0 表示没有有效计时。 */
    val timeSeconds: Int = 0,
)

/** 菜谱编辑器状态。 */
data class RecipeEditorUiState(
    val recipeId: String? = null,
    val name: String = "",
    val englishName: String = "",
    val description: String = "",
    val category: RecipeCategory = RecipeCategory.DISH,
    /** 对应既有 `recipe.difficulty` 列，界面名称已统一为"最小制作容量"。 */
    val minimumCapacity: String = "",
    val cuisine: String = "",
    val prepMinutes: String = "",
    val cookMinutes: String = "",
    val allergenNotes: String = "",
    val seedNote: String = "",
    val yieldAmount: String = "",
    val yieldUnit: UnitModel? = null,
    val servings: String = "1",
    val units: List<UnitModel> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val recipeOptions: List<RecipeOption> = emptyList(),
    val sauceOptions: List<SauceOption> = emptyList(),
    val lines: List<RecipeLineEditor> = emptyList(),
    val steps: List<RecipeStepEditor> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedRecipeId: String? = null,
    val editingVersionNo: Int? = null,
    /** 已存在的未发布草稿；再次保存时更新它，避免重复创建草稿。 */
    val editingDraftId: String? = null,
    val hasPublishedVersion: Boolean = false,
    val updatedAtEpochMillis: Long? = null,
    val isDirty: Boolean = false,
    /** 只有版本内容（产量/份数/组成/步骤）变化时才需要新建或更新草稿。 */
    val versionDirty: Boolean = false,
) {
    val isNew: Boolean get() = recipeId == null
    val isEditingPublished: Boolean get() = hasPublishedVersion
}

/**
 * 既有 `recipe.difficulty` 列的显示名。
 *
 * 数据库 CHECK 仍只允许 EASY/MEDIUM/HARD；本次只把字段名称改为"最小制作容量"，
 * 不改存储值，也不删除既有数据。
 */
fun recipeMinimumCapacityLabel(value: String?): String =
    when (value?.trim()?.uppercase()) {
        "EASY" -> "简单"
        "MEDIUM" -> "中等"
        "HARD" -> "困难"
        null, "" -> "—"
        else -> value
    }

/** 把菜谱领域错误转成面向用户的中文提示。 */
object RecipeErrorMessages {
    fun describe(error: CulinaryError): String = withCode(error, friendlyMessage(error))

    private fun friendlyMessage(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation -> validationMessage(error)
            is CulinaryError.Conflict -> "该版本已发布，不能重复发布。"
            is CulinaryError.NotFound -> "菜谱或版本不存在，可能已被删除。"
            is CulinaryError.Domain ->
                when (error.code.value) {
                    "CUL-REP-001" -> "检测到子配方循环引用，无法发布。"
                    "CUL-REP-004" -> "该版本引用了暂不支持展开的目标（如子酱汁）。"
                    else -> "配方规则拒绝该操作。"
                }

            else -> "操作失败，请重试。"
        }

    private fun withCode(
        error: CulinaryError,
        message: String,
    ): String = "$message（${error.code.value}）"

    private fun validationMessage(error: CulinaryError.Validation): String =
        when {
            error.field == "recipe.name" && error.rule.contains("blank") -> "名称不能为空。"
            error.field == "recipe.name" -> "名称过长，请控制在 120 个字符以内。"
            error.field == "recipe.yield" -> "标准产量必须是大于 0 的数字。"
            error.field == "recipe.servings" -> "基准份数必须是大于 0 的整数。"
            error.field == "recipe.prepMinutes" || error.field == "recipe.cookMinutes" ->
                "准备时间/烹饪时间必须是非负整数分钟。"
            error.field == "recipe.version" -> "草稿状态已变化，请重新打开编辑。"
            error.field == "servings" -> "目标份数必须是大于 0 的数字。"
            error.field.startsWith("recipe.component") -> "组成行数量必须是大于 0 的数字。"
            else -> "输入不合法。"
        }
}
