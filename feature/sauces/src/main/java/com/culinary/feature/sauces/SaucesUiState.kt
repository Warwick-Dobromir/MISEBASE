package com.culinary.feature.sauces

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.Unit as UnitModel

/** 酱汁列表状态。 */
data class SaucesListUiState(
    val items: List<Sauce> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 酱汁详情状态。 */
data class SauceDetailUiState(
    val detail: SauceDetail? = null,
    val isLoading: Boolean = true,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val relations: List<KitchenRelatedRecord> = emptyList(),
)

/** 编辑器中的组成行；数量保留用户输入的字符串，提交时才解析。 */
data class SauceLineEditor(
    val key: String,
    val target: ComponentTarget,
    val name: String,
    val amount: String,
    val unit: UnitModel,
    val isOptional: Boolean = false,
) {
    /** Stage 3 编辑入口只支持食材行；其他目标只读保留，保存时原样回写。 */
    val isReadOnly: Boolean get() = target !is ComponentTarget.Ingredient

    val kindLabel: String
        get() =
            when (target) {
                is ComponentTarget.Ingredient -> "食材"
                is ComponentTarget.Recipe -> "子菜谱"
                is ComponentTarget.Sauce -> "子酱汁"
            }
}

/** 酱汁编辑器状态。 */
data class SauceEditorUiState(
    val sauceId: String? = null,
    val name: String = "",
    val family: String = "",
    val englishName: String = "",
    val usageNotes: String = "",
    val mainIngredients: String = "",
    val preparationSummary: String = "",
    val keyParameters: String = "",
    val storageNotes: String = "",
    val flavorProfile: String = "",
    val adjustableNotes: String = "",
    val yieldAmount: String = "",
    val yieldUnit: UnitModel? = null,
    val units: List<UnitModel> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val lines: List<SauceLineEditor> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSauceId: String? = null,
    val editingVersionNo: Int? = null,
    val hasPublishedVersion: Boolean = false,
) {
    val isNew: Boolean get() = sauceId == null
}

/** 把领域错误转成面向用户的中文提示；未知错误保留错误码，不伪装成业务错误。 */
object SauceErrorMessages {
    fun describe(error: CulinaryError): String = withCode(error, friendlyMessage(error))

    private fun friendlyMessage(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation -> validationMessage(error)
            is CulinaryError.Conflict -> "该版本已发布，不能重复发布。"
            is CulinaryError.NotFound -> "酱汁或版本不存在，可能已被删除。"
            else -> "操作失败，请重试。"
        }

    private fun withCode(
        error: CulinaryError,
        message: String,
    ): String = "$message（${error.code.value}）"

    private fun validationMessage(error: CulinaryError.Validation): String =
        when {
            error.field == "sauce.name" && error.rule.contains("blank") -> "名称不能为空。"
            error.field == "sauce.name" -> "名称过长，请控制在 120 个字符以内。"
            error.field == "sauce.yield" -> "标准产量必须是大于 0 的数字。"
            error.field.startsWith("sauce.component") -> "组成行数量必须是大于 0 的数字。"
            else -> "输入不合法。"
        }
}
