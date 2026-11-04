package com.culinary.feature.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.usecase.inventory.GetInventoryProductDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 商品详情：批次、流水与关联菜谱。 */
@HiltViewModel
class InventoryDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getDetail: GetInventoryProductDetailUseCase,
    ) : ViewModel() {
        private val productId =
            ProductId(savedStateHandle.get<String>(InventoryRoutes.ARG_PRODUCT_ID).orEmpty())
        private val _uiState = MutableStateFlow(InventoryDetailUiState())
        val uiState: StateFlow<InventoryDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = getDetail(productId)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(detail = result.value, isLoading = false)
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
