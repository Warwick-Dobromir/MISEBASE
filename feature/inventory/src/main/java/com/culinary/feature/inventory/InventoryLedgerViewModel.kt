package com.culinary.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.usecase.inventory.ListInventoryLedgerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 库存流水 ViewModel：类型筛选与关键字筛选。 */
@HiltViewModel
class InventoryLedgerViewModel
    @Inject
    constructor(
        private val listLedger: ListInventoryLedgerUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(InventoryLedgerUiState())
        val uiState: StateFlow<InventoryLedgerUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = listLedger()) {
                    is Outcome.Success ->
                        _uiState.update { it.copy(rows = result.value, isLoading = false) }

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

        fun onTypeFilterChange(value: LedgerTypeFilter) {
            _uiState.update { it.copy(typeFilter = value) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
