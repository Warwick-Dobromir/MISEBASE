package com.culinary.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.ingredient.SaveIngredientInput
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 食材列表 ViewModel：只调用领域用例，不直接访问 Repository/Room。
 */
@HiltViewModel
class IngredientsViewModel
    @Inject
    constructor(
        listIngredients: ListIngredientsUseCase,
        private val saveIngredient: SaveIngredientUseCase,
        private val archiveIngredient: ArchiveIngredientUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(IngredientsUiState())
        val uiState: StateFlow<IngredientsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                listIngredients().collect { items ->
                    _uiState.update { it.copy(items = items, isLoading = false) }
                }
            }
        }

        fun save(
            name: String,
            id: IngredientId? = null,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                when (val result = saveIngredient(SaveIngredientInput(name = name, id = id))) {
                    is Outcome.Success -> Unit
                    is Outcome.Failure ->
                        _uiState.update { it.copy(error = IngredientErrorMessages.describe(result.error)) }
                }
                _uiState.update { it.copy(isSaving = false) }
            }
        }

        fun archive(id: IngredientId) {
            viewModelScope.launch {
                when (val result = archiveIngredient(id)) {
                    is Outcome.Success -> Unit
                    is Outcome.Failure ->
                        _uiState.update { it.copy(error = IngredientErrorMessages.describe(result.error)) }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
