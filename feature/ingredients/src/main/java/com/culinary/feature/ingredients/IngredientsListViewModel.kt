package com.culinary.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.unit.ListUnitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 食材列表 ViewModel：组合食材与单位，解析每行的默认单位标签。 */
@HiltViewModel
class IngredientsListViewModel
    @Inject
    constructor(
        private val listIngredients: ListIngredientsUseCase,
        private val listUnits: ListUnitsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(IngredientsListUiState())
        val uiState: StateFlow<IngredientsListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val unitLabels =
                    runCatching { listUnits().associate { it.id.value to it.symbol } }
                        .getOrDefault(emptyMap())
                listIngredients()
                    .catch { error ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "食材列表加载失败：${error.message.orEmpty()}")
                        }
                    }.collect { ingredients ->
                        _uiState.update {
                            it.copy(
                                rows =
                                    ingredients.map { ingredient ->
                                        IngredientRow(
                                            id = ingredient.id.value,
                                            name = ingredient.name,
                                            category = ingredient.category,
                                            unitLabel = ingredient.defaultUnitId?.value?.let(unitLabels::get),
                                            isActive = ingredient.isActive,
                                        )
                                    },
                                isLoading = false,
                            )
                        }
                    }
            }
        }
    }
