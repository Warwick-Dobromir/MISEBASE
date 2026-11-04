package com.culinary.feature.scan

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.scan.ScanPreviousImport

/**
 * 导入确认页状态。
 *
 * [previous] 非空表示该条码已导入过：确认按钮会被禁用，避免"看起来成功但实际是重放"的误解。
 * [result] 是本次确认的结果（含幂等重放标记），一旦有结果就不再允许重复提交。
 */
data class ScanConfirmUiState(
    val capture: ScanCapture? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val selectedIngredientId: String? = null,
    val createNew: Boolean = false,
    val newIngredientName: String = "",
    val quantity: String = "1",
    val previous: ScanPreviousImport? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val result: ScanImportOutcome? = null,
) {
    /** 是否允许提交：有识别结果、未在保存、未导入过、也没有已完成的导入。 */
    val canConfirm: Boolean
        get() = capture != null && !saving && previous == null && result == null

    val hasTarget: Boolean
        get() = createNew || selectedIngredientId != null
}

/** 一次成功导入的展示数据。 */
data class ScanImportOutcome(
    val ingredientName: String,
    val amount: Double,
    val unitCode: String,
    val batchCode: String?,
    val replayed: Boolean,
)

/** 扫码导入的错误文案：说明发生了什么，不暴露堆栈与 SQL。 */
internal object ScanErrorMessages {
    fun describe(error: CulinaryError): String = "${friendly(error)}（${error.code.value}）"

    private fun friendly(error: CulinaryError): String =
        when (error) {
            is CulinaryError.Validation ->
                when (error.field) {
                    "quantity" -> "数量必须是大于 0 的数字。"
                    "ingredient" -> "请选择已有食材，或填写新食材名称。"
                    "ingredient.name" -> "食材名称不合法，请检查长度后重试。"
                    else -> "输入不合法，请检查后重试。"
                }

            is CulinaryError.NotFound -> "记录不存在或已被删除，请刷新后重试。"
            is CulinaryError.Conflict -> "该条码已有导入记录，请勿重复提交。"
            is CulinaryError.Domain -> "业务规则拒绝了本次导入。"
            is CulinaryError.Platform -> "相机或系统能力不可用，请改用手工输入。"
            else -> "导入失败，请重试。"
        }
}
