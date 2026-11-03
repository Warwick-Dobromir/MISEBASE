package com.culinary.feature.ingredients

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.model.Ingredient

/** 食材列表界面状态（阶段 7 最小切片）。 */
data class IngredientsUiState(
    val items: List<Ingredient> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** 把领域错误转成面向用户的中文提示；未知错误不伪装成业务错误。 */
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
            else -> "输入不合法。"
        }
}
