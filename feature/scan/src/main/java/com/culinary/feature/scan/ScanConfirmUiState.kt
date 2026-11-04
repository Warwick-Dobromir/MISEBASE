package com.culinary.feature.scan

import com.culinary.core.common.CulinaryError
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.scan.ScanPreviousImport

/**
 * 导入确认页状态（MB-S5-002 事件级幂等）。
 *
 * 状态语义：
 * - [previous] / [isDuplicate]：该条码此前导入过（只用于提示，**不再阻止**再次入库）；
 * - [isSubmitting]：一次提交进行中，重复点击被忽略（体验层防抖，不是幂等手段）；
 * - [currentOperationId]：本次确认的事件级幂等键，首次点击时生成，失败重试复用，成功后清空；
 * - [lastError]：提交失败原因，配合「重试」入口；[error] 用于即时校验/查询错误。
 * - [result]：本次确认的结果（含幂等重放标记），有结果后不再允许提交。
 */
data class ScanConfirmUiState(
    val capture: ScanCapture? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val selectedIngredientId: String? = null,
    val createNew: Boolean = false,
    val newIngredientName: String = "",
    val quantity: String = "1",
    val previous: ScanPreviousImport? = null,
    val isDuplicate: Boolean = false,
    val loading: Boolean = true,
    val isSubmitting: Boolean = false,
    val currentOperationId: String? = null,
    val lastError: String? = null,
    val error: String? = null,
    val result: ScanImportOutcome? = null,
) {
    /** 是否允许提交：有识别结果、未在提交、查询未完成、也没有已完成的导入。 */
    val canConfirm: Boolean
        get() = capture != null && !isSubmitting && !loading && result == null

    /** 失败后是否可重试：必须已有缓存的 operation id，重试才能复用同一个键。 */
    val canRetry: Boolean
        get() = lastError != null && currentOperationId != null && canConfirm

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
                    "operationId" -> "缺少本次导入的操作标识，请返回扫码页重试。"
                    else -> "输入不合法，请检查后重试。"
                }

            is CulinaryError.NotFound -> "记录不存在或已被删除，请刷新后重试。"
            is CulinaryError.Conflict -> "本次导入已被处理，请刷新后确认结果。"
            is CulinaryError.Domain -> "业务规则拒绝了本次导入。"
            is CulinaryError.Platform -> "相机或系统能力不可用，请改用手工输入。"
            else -> "导入失败，请重试。"
        }
}
