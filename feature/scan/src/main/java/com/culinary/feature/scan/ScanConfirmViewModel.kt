package com.culinary.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
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
 * 导入确认页 ViewModel（MB-D-004 接口编排）。
 *
 * 事务边界与补偿策略：
 * 1. **写前查重**：先按条码幂等键查历史导入，已导入则禁止再次提交；
 * 2. **食材**：交给 `feature:products` 的 [ScanIngredientGateway]（内部用 `SaveIngredientUseCase` 开事务）；
 * 3. **入库**：交给 `feature:inventory` 的 [ScanStockGateway]（批次 + 流水同一事务，幂等键由条码决定）；
 * 4. **明确补偿**：若本次新建了食材而入库失败，归档该食材（软删除），避免留下孤立食材；
 *    入库成功或命中重放则不做补偿。
 *
 * `feature:scan` 自身不写食材/库存表，只调用上述接口。
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

        /** 重新查询该条码是否已导入（页面进入或失败后重试）。 */
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
                        _uiState.update { it.copy(loading = false, previous = result.value) }
                }
            }
        }

        fun confirm() {
            when (val check = evaluate(_uiState.value)) {
                is ConfirmCheck.Ready ->
                    viewModelScope.launch {
                        importCapture(check.capture, check.request, check.amount)
                    }

                is ConfirmCheck.Rejected ->
                    check.message?.let { message ->
                        _uiState.update { it.copy(error = message) }
                    }

                ConfirmCheck.Ignored -> Unit
            }
        }

        /** 提交前校验：已有结果/保存中忽略，已导入/数量非法/缺目标拒绝。 */
        private fun evaluate(state: ScanConfirmUiState): ConfirmCheck {
            val capture = state.capture ?: return ConfirmCheck.Ignored
            val amount = state.quantity.trim().toDoubleOrNull()
            return when {
                state.saving || state.result != null -> ConfirmCheck.Ignored
                state.previous != null -> ConfirmCheck.Rejected(ALREADY_IMPORTED)
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
            capture: com.culinary.core.domain.barcode.ScanCapture,
            request: ScanIngredientRequest,
            amount: Double,
        ) {
            _uiState.update { it.copy(saving = true, error = null) }
            when (val targetResult = ingredientGateway.resolve(request)) {
                is Outcome.Failure ->
                    _uiState.update {
                        it.copy(saving = false, error = ScanErrorMessages.describe(targetResult.error))
                    }

                is Outcome.Success -> receive(capture, targetResult.value, amount)
            }
        }

        private suspend fun receive(
            capture: com.culinary.core.domain.barcode.ScanCapture,
            target: ScanIngredientTarget,
            amount: Double,
        ) {
            val result =
                stockGateway.receive(
                    ScanStockRequest(capture = capture, target = target, amount = amount),
                )
            when (result) {
                is Outcome.Failure -> {
                    val compensated = target.created && compensate(target)
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error =
                                buildString {
                                    append(ScanErrorMessages.describe(result.error))
                                    if (compensated) append("（已撤回本次新建的食材）")
                                },
                        )
                    }
                }

                is Outcome.Success -> {
                    val receipt = result.value
                    _uiState.update {
                        it.copy(
                            saving = false,
                            previous =
                                ScanPreviousImport(entry = receipt.entry, item = receipt.item),
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
            const val ALREADY_IMPORTED = "该条码已导入过，本次不会重复入库。"
        }
    }

/** 提交前的判定结果；把分支集中在一处，避免 `confirm()` 里堆 `return`。 */
private sealed interface ConfirmCheck {
    data class Ready(
        val capture: com.culinary.core.domain.barcode.ScanCapture,
        val request: ScanIngredientRequest,
        val amount: Double,
    ) : ConfirmCheck

    /** [message] 为空表示没有可展示的提示（例如缺少识别结果）。 */
    data class Rejected(
        val message: String?,
    ) : ConfirmCheck

    /** 保存中或已有结果：静默忽略重复提交。 */
    data object Ignored : ConfirmCheck
}
