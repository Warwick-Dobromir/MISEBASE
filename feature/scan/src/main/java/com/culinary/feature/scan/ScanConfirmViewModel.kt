package com.culinary.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.scan.ScanIngredientGateway
import com.culinary.core.domain.scan.ScanIngredientRequest
import com.culinary.core.domain.scan.ScanIngredientTarget
import com.culinary.core.domain.scan.ScanPreviousImport
import com.culinary.core.domain.scan.ScanStockGateway
import com.culinary.core.domain.scan.ScanStockRequest
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.scan.LookupScanImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 导入确认页 ViewModel（MB-D-004 接口编排 / MB-S5-002 事件级幂等）。
 *
 * 幂等机制：
 * 1. 用户点击「确认导入 / 再次入库」时，若没有缓存的 [ScanConfirmUiState.currentOperationId]，
 *    立刻用 [IdGenerator] 生成一个，并**先写入状态**、再把 `isSubmitting` 置为真
 *    （同一帧内的第二次点击会读到 `isSubmitting = true` 而被忽略）；
 * 2. 幂等键 = `scan-import:<条码>:<operationId>`，由 `feature:inventory` 写进流水唯一键；
 * 3. 失败：保留 operationId，用户点「重试」复用同一个键 —— 即使上一次其实已经写库成功，
 *    重试也只会命中幂等重放，不会产生第二条流水；
 * 4. 成功：清空 operationId（下一次「再次入库」会生成新键，形成一条新的独立入库）；
 * 5. 取消/离开：清空未完成的 operationId，避免把上一页的键带进新的确认。
 *
 * 事务边界与补偿：食材创建走 `ScanIngredientGateway`（products），入库走 `ScanStockGateway`
 * （inventory，批次 + 流水同一事务）；本次新建食材后入库失败时归档该食材（明确补偿）。
 */
@HiltViewModel
class ScanConfirmViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val lookup: LookupScanImportUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val ingredientGateway: ScanIngredientGateway,
        private val stockGateway: ScanStockGateway,
        private val archiveIngredient: ArchiveIngredientUseCase,
        private val idGenerator: IdGenerator,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ScanConfirmUiState())
        val uiState: StateFlow<ScanConfirmUiState> = _uiState.asStateFlow()

        init {
            val capture =
                ScanRoutes.captureFrom(
                    code = savedStateHandle[ScanRoutes.ARG_CODE],
                    format = savedStateHandle[ScanRoutes.ARG_FORMAT],
                    source = savedStateHandle[ScanRoutes.ARG_SOURCE],
                )
            if (capture == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "无法读取扫码结果，请返回扫码页重试。",
                    )
                }
            } else {
                _uiState.update { it.copy(capture = capture) }
                observeIngredients()
                refreshPrevious()
            }
        }

        fun onSelectIngredient(ingredientId: String) {
            _uiState.update {
                it.copy(
                    selectedIngredientId = ingredientId,
                    createNew = false,
                    error = null,
                )
            }
        }

        fun onCreateNewChange(enabled: Boolean) {
            _uiState.update {
                it.copy(
                    createNew = enabled,
                    selectedIngredientId = if (enabled) null else it.selectedIngredientId,
                    error = null,
                )
            }
        }

        fun onNewIngredientNameChange(value: String) {
            _uiState.update { it.copy(newIngredientName = value, error = null) }
        }

        fun onQuantityChange(value: String) {
            _uiState.update { it.copy(quantity = value, error = null) }
        }

        /**
         * 重新查询该条码此前的导入记录（进入页面时）。
         *
         * 命中只把 [ScanConfirmUiState.isDuplicate] 置真并展示提示，**不阻止**再次入库。
         */
        fun refreshPrevious() {
            val capture = _uiState.value.capture ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = null) }
                when (val result = lookup(capture)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(loading = false, error = ScanErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                loading = false,
                                previous = result.value,
                                isDuplicate = result.value != null,
                            )
                        }
                }
            }
        }

        /**
         * 提交（首次确认 / 再次入库 / 失败后重试共用）。
         *
         * 重试时状态里已有 `currentOperationId`，因此复用同一个幂等键。
         */
        fun confirm() {
            when (val check = evaluate(_uiState.value)) {
                is ConfirmCheck.Ready -> startSubmit(check)
                is ConfirmCheck.Rejected ->
                    check.message?.let { message ->
                        _uiState.update { it.copy(error = message) }
                    }

                ConfirmCheck.Ignored -> Unit
            }
        }

        /**
         * v9 扫码交接：只返回识别结果与建议档案名/数量，**不写库存**。
         *
         * 库存数量必须由库存模块的"建档 → 入库确认"流程写入；
         * 识别成功不等于入库成功。
         */
        fun handoff(): ScanHandoff? {
            val state = _uiState.value
            val capture = state.capture ?: return null
            val suggestedName =
                if (state.createNew) {
                    state.newIngredientName.trim().takeIf { it.isNotEmpty() }
                } else {
                    state.ingredients.firstOrNull { it.id.value == state.selectedIngredientId }?.name
                }
            if (!state.createNew && suggestedName == null) {
                _uiState.update { it.copy(error = "请选择已有食材，或填写新食材名称。") }
                return null
            }
            return ScanHandoff(
                barcode = capture.rawValue,
                suggestedName = suggestedName,
                quantity = state.quantity.trim().ifEmpty { "1" },
            )
        }

        /** 「取消」：清掉未完成的幂等键，避免下一次确认沿用它。 */
        fun onCancelImport() {
            _uiState.update {
                it.copy(
                    currentOperationId = null,
                    lastError = null,
                    error = null,
                )
            }
        }

        private fun startSubmit(check: ConfirmCheck.Ready) {
            val operationId = _uiState.value.currentOperationId ?: idGenerator.newId()
            // 先同步写入状态：同一帧内的第二次点击会看到 isSubmitting = true 而被忽略。
            _uiState.update {
                it.copy(
                    currentOperationId = operationId,
                    isSubmitting = true,
                    lastError = null,
                    error = null,
                )
            }
            viewModelScope.launch {
                importCapture(check.capture, check.request, check.amount, operationId)
            }
        }

        /** 提交前校验：已有结果/提交中忽略；数量非法或缺目标拒绝。 */
        private fun evaluate(state: ScanConfirmUiState): ConfirmCheck {
            val capture = state.capture ?: return ConfirmCheck.Ignored
            val amount = state.quantity.trim().toDoubleOrNull()
            return when {
                state.isSubmitting || state.result != null -> ConfirmCheck.Ignored
                amount == null || !amount.isFinite() || amount <= 0.0 ->
                    ConfirmCheck.Rejected(INVALID_QUANTITY)
                else ->
                    buildRequest(state)?.let { ConfirmCheck.Ready(capture, it, amount) }
                        ?: ConfirmCheck.Rejected(MISSING_TARGET)
            }
        }

        private fun buildRequest(state: ScanConfirmUiState): ScanIngredientRequest? {
            val capture = state.capture ?: return null
            return when {
                state.createNew -> {
                    val name = state.newIngredientName.trim()
                    if (name.isEmpty()) return null
                    ScanIngredientRequest(capture = capture, newIngredientName = name)
                }

                state.selectedIngredientId != null ->
                    ScanIngredientRequest(
                        capture = capture,
                        existingIngredientId = IngredientId(state.selectedIngredientId),
                    )

                else -> null
            }
        }

        private suspend fun importCapture(
            capture: ScanCapture,
            request: ScanIngredientRequest,
            amount: Double,
            operationId: String,
        ) {
            when (val targetResult = ingredientGateway.resolve(request)) {
                is Outcome.Failure -> failSubmission(ScanErrorMessages.describe(targetResult.error))
                is Outcome.Success -> receive(capture, targetResult.value, amount, operationId)
            }
        }

        private suspend fun receive(
            capture: ScanCapture,
            target: ScanIngredientTarget,
            amount: Double,
            operationId: String,
        ) {
            val result =
                stockGateway.receive(
                    ScanStockRequest(
                        capture = capture,
                        target = target,
                        amount = amount,
                        operationId = operationId,
                    ),
                )
            when (result) {
                is Outcome.Failure -> {
                    val compensated = target.created && compensate(target)
                    failSubmission(
                        buildString {
                            append(ScanErrorMessages.describe(result.error))
                            if (compensated) append("（已撤回本次新建的食材）")
                        },
                    )
                }

                is Outcome.Success -> succeed(target, result.value)
            }
        }

        private fun succeed(
            target: ScanIngredientTarget,
            receipt: com.culinary.core.domain.scan.ScanStockReceipt,
        ) {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    currentOperationId = null,
                    lastError = null,
                    previous = ScanPreviousImport(entry = receipt.entry, item = receipt.item),
                    isDuplicate = true,
                    result =
                        ScanImportOutcome(
                            ingredientName = target.ingredient.name,
                            amount = receipt.entry.onHandDelta,
                            unitCode = receipt.entry.stockUnit.code,
                            batchCode = receipt.item.batchCode,
                            replayed = receipt.idempotentReplay,
                        ),
                )
            }
        }

        /** 失败：解除提交态但**保留** operationId，供「重试」复用同一个幂等键。 */
        private fun failSubmission(message: String) {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    lastError = message,
                )
            }
        }

        /** 入库失败且本次新建了食材时归档它；归档失败不算致命，但会写进错误文案。 */
        private suspend fun compensate(target: ScanIngredientTarget): Boolean =
            when (archiveIngredient(target.ingredient.id)) {
                is Outcome.Success -> true
                is Outcome.Failure -> false
            }

        private fun observeIngredients() {
            viewModelScope.launch {
                listIngredients().collect { ingredients ->
                    _uiState.update { it.copy(ingredients = ingredients) }
                }
            }
        }

        private companion object {
            const val INVALID_QUANTITY = "数量必须是大于 0 的数字。"
            const val MISSING_TARGET = "请选择已有食材，或填写新食材名称。"
        }
    }

/** 提交前的判定结果；把分支集中在一处，避免 `confirm()` 里堆 `return`。 */
private sealed interface ConfirmCheck {
    data class Ready(
        val capture: ScanCapture,
        val request: ScanIngredientRequest,
        val amount: Double,
    ) : ConfirmCheck

    /** [message] 为空表示没有可展示的提示（例如缺少识别结果）。 */
    data class Rejected(
        val message: String?,
    ) : ConfirmCheck

    /** 提交中或已有结果：静默忽略重复提交。 */
    data object Ignored : ConfirmCheck
}
