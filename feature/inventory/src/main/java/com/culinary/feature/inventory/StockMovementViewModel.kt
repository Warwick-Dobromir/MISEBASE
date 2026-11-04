package com.culinary.feature.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.usecase.inventory.AdjustStockUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockIssueUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import com.culinary.core.domain.usecase.inventory.StockAdjustmentInput
import com.culinary.core.domain.usecase.inventory.StockIssueInput
import com.culinary.core.domain.usecase.inventory.StockIssueReason
import com.culinary.core.domain.usecase.inventory.StockReceiptInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 入库 / 出库 / 盘点调整共用 ViewModel；Domain 负责规则与流水。 */
@HiltViewModel
class StockMovementViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listOptions: ListInventoryFormOptionsUseCase,
        private val recordReceipt: RecordStockReceiptUseCase,
        private val recordIssue: RecordStockIssueUseCase,
        private val adjustStock: AdjustStockUseCase,
    ) : ViewModel() {
        private val mode =
            when (savedStateHandle.get<String>(InventoryRoutes.ARG_MODE)) {
                InventoryRoutes.MODE_ISSUE -> StockMovementMode.ISSUE
                InventoryRoutes.MODE_ADJUST -> StockMovementMode.ADJUST
                else -> StockMovementMode.RECEIPT
            }
        private val routeBatchId = savedStateHandle.get<String>(InventoryRoutes.ARG_BATCH_ID)
        private val _uiState = MutableStateFlow(StockMovementUiState(mode = mode))
        val uiState: StateFlow<StockMovementUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = listOptions()) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        val options = result.value
                        val batch =
                            options.batches.firstOrNull { it.item.batchId.value == routeBatchId }
                        val product =
                            batch?.let { b -> options.products.firstOrNull { it.id == b.item.productId } }
                                ?: options.products.firstOrNull()
                        val unit =
                            product?.stockUnit
                                ?: options.units.firstOrNull()
                        _uiState.update {
                            it.copy(
                                options = options,
                                selectedBatchId = batch?.item?.batchId?.value,
                                selectedProductId = product?.id,
                                unitCode = unit?.code.orEmpty(),
                                locationId =
                                    options.locations
                                        .firstOrNull()
                                        ?.id
                                        ?.value,
                                isLoading = false,
                            )
                        }
                    }
                }
            }
        }

        fun onSourceChange(source: StockSource) {
            _uiState.update { it.copy(source = source, error = null) }
        }

        fun onProductSelected(productId: ProductId) {
            _uiState.update { state ->
                val product = state.options.products.firstOrNull { it.id == productId }
                state.copy(
                    selectedProductId = productId,
                    selectedIngredientId = null,
                    unitCode = product?.stockUnit?.code ?: state.unitCode,
                    error = null,
                )
            }
        }

        fun onIngredientSelected(ingredientId: String) {
            _uiState.update { state ->
                val ingredient = state.options.ingredients.firstOrNull { it.id.value == ingredientId }
                val unit = state.options.units.firstOrNull { it.id == ingredient?.defaultUnitId }
                state.copy(
                    selectedIngredientId = ingredientId,
                    selectedProductId = null,
                    unitCode = unit?.code ?: state.unitCode,
                    error = null,
                )
            }
        }

        fun onBatchSelected(batchId: String) {
            _uiState.update { state ->
                val batch = state.options.batches.firstOrNull { it.item.batchId.value == batchId }
                val product = batch?.let { b -> state.options.products.firstOrNull { it.id == b.item.productId } }
                state.copy(
                    selectedBatchId = batchId,
                    selectedProductId = product?.id ?: state.selectedProductId,
                    unitCode = batch?.item?.stockUnit?.code ?: state.unitCode,
                    error = null,
                )
            }
        }

        fun onQuantityChange(value: String) {
            _uiState.update { it.copy(quantity = value, error = null) }
        }

        fun onUnitChange(code: String) {
            _uiState.update { it.copy(unitCode = code) }
        }

        fun onReasonChange(reason: StockIssueReason) {
            _uiState.update { it.copy(reason = reason) }
        }

        fun onNoteChange(value: String) {
            _uiState.update { it.copy(note = value) }
        }

        fun onBatchCodeChange(value: String) {
            _uiState.update { it.copy(batchCode = value) }
        }

        fun onExpiryDateChange(value: String) {
            _uiState.update { it.copy(expiryDate = value) }
        }

        fun onLocationSelected(locationId: String) {
            _uiState.update { it.copy(locationId = locationId) }
        }

        fun submit() {
            val state = _uiState.value
            val amount = state.quantity.toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                _uiState.update { it.copy(error = "数量必须是大于 0 的数字。") }
                return
            }
            val unit = state.options.units.firstOrNull { it.code == state.unitCode }
            if (unit == null) {
                _uiState.update { it.copy(error = "请选择单位。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                val result =
                    when (state.mode) {
                        StockMovementMode.RECEIPT -> submitReceipt(state, amount, unit)
                        StockMovementMode.ISSUE -> submitIssue(state, amount, unit)
                        StockMovementMode.ADJUST -> submitAdjustment(state, amount, unit)
                    }
                when (result) {
                    is Outcome.Success ->
                        _uiState.update { it.copy(isSaving = false, saved = true) }
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                error = InventoryErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        private suspend fun submitReceipt(
            state: StockMovementUiState,
            amount: Double,
            unit: com.culinary.core.domain.model.Unit,
        ): Outcome<*> {
            val expiryText = state.expiryDate.trim()
            val expiry = parseDate(expiryText)
            if (expiryText.isNotEmpty() && expiry == null) {
                return Outcome.failure(
                    com.culinary.core.common.CulinaryError.Validation(
                        field = "expiryDate",
                        rule = "must be yyyy-MM-dd",
                    ),
                )
            }
            return recordReceipt(
                StockReceiptInput(
                    quantity = Quantity(amount, unit),
                    productId = state.selectedProductId.takeIf { state.source == StockSource.PRODUCT },
                    ingredientId =
                        state.selectedIngredientId
                            ?.takeIf { state.source == StockSource.INGREDIENT }
                            ?.let { IngredientId(it) },
                    batchCode = state.batchCode,
                    locationId = state.locationId?.let(::LocationId),
                    expiresOn = expiry,
                    note = state.note,
                ),
            )
        }

        private suspend fun submitIssue(
            state: StockMovementUiState,
            amount: Double,
            unit: com.culinary.core.domain.model.Unit,
        ): Outcome<*> {
            val batchId = state.selectedBatchId ?: return missingBatch()
            return recordIssue(
                StockIssueInput(
                    batchId = BatchId(batchId),
                    quantity = Quantity(amount, unit),
                    reason = state.reason,
                    note = state.note,
                ),
            )
        }

        private suspend fun submitAdjustment(
            state: StockMovementUiState,
            amount: Double,
            unit: com.culinary.core.domain.model.Unit,
        ): Outcome<*> {
            val batchId = state.selectedBatchId ?: return missingBatch()
            return adjustStock(
                StockAdjustmentInput(
                    batchId = BatchId(batchId),
                    countedQuantity = Quantity(amount, unit),
                    note = state.note,
                ),
            )
        }

        private fun missingBatch(): Outcome<Nothing> =
            Outcome.failure(
                com.culinary.core.common.CulinaryError.Validation(
                    field = "batchId",
                    rule = "must select a batch",
                ),
            )

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
