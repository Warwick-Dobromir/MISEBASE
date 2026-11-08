package com.culinary.feature.ingredients

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.model.IngredientDetail
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.Unit as UnitModel

/** 食材列表行：已解析默认单位标签，UI 不直接查仓库。 */
data class IngredientRow(
    val id: String,
    val name: String,
    val category: String?,
    val unitLabel: String?,
    val isActive: Boolean,
)

/** 食材列表状态。 */
data class IngredientsListUiState(
    val rows: List<IngredientRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 食材详情状态。 */
data class IngredientDetailUiState(
    val detail: IngredientDetail? = null,
    val isLoading: Boolean = true,
    val isArchiving: Boolean = false,
    val archived: Boolean = false,
    val error: String? = null,
    val relations: List<KitchenRelatedRecord> = emptyList(),
)

/** 食材编辑器状态。 */
data class IngredientEditorUiState(
    val ingredientId: String? = null,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val englishName: String = "",
    val exampleProducts: String = "",
    val storageNotes: String = "",
    val allergenNotes: String = "",
    val defaultUnit: UnitModel? = null,
    val units: List<UnitModel> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val isNew: Boolean get() = ingredientId == null
}

/** 把领域错误转成面向用户的中文提示；未知错误保留错误码。 */
object IngredientErrorMessages {
    fun describe(error: CulinaryError): String = withCode(error, friendlyMessage(error))

    private fun friendlyMessage(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation -> validationMessage(error)
            is CulinaryError.Conflict -> "名称已存在，请换一个名称。"
            is CulinaryError.NotFound -> "食材不存在，可能已被删除。"
            else -> "操作失败，请重试。"
        }

    private fun withCode(
        error: CulinaryError,
        message: String,
    ): String = "$message（${error.code.value}）"

    private fun validationMessage(error: CulinaryError.Validation): String =
        when (error.field) {
            "ingredient.name" ->
                when {
                    error.rule.contains("blank") -> "名称不能为空。"
                    error.rule.contains("characters") -> "名称过长，请控制在 120 个字符以内。"
                    else -> "名称不合法。"
                }

            "ingredient.category" -> "分类过长，请控制在 60 个字符以内。"
            "ingredient.description" -> "描述过长，请控制在 500 个字符以内。"
            else -> "输入不合法。"
        }
}
