package com.culinary.feature.sauces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 酱汁列表 ViewModel：只调用领域用例，不直接访问 Repository/Room。 */
@HiltViewModel
class SaucesListViewModel
    @Inject
    constructor(
        listSauces: ListSaucesUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SaucesListUiState())
        val uiState: StateFlow<SaucesListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                listSauces()
                    .catch { error ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "酱汁列表加载失败：${error.message.orEmpty()}")
                        }
                    }.collect { items ->
                        _uiState.update { it.copy(items = items, isLoading = false) }
                    }
            }
        }
    }
