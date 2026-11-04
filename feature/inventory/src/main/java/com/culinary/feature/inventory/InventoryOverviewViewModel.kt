package com.culinary.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 库存总览 ViewModel：加载、搜索、筛选。 */
@HiltViewModel
class InventoryOverviewViewModel
    @Inject
    constructor(
        private val listOverview: ListInventoryOverviewUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(InventoryOverviewUiState())
        val uiState: StateFlow<InventoryOverviewUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = listOverview()) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(rows = result.value, isLoading = false)
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        fun onQueryChange(value: String) {
            _uiState.update { it.copy(query = value) }
        }

        fun toggleLowStock() {
            _uiState.update { it.copy(onlyLowStock = !it.onlyLowStock) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
