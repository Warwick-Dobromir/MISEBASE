package com.culinary.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 菜谱列表 ViewModel。 */
@HiltViewModel
class RecipesListViewModel
    @Inject
    constructor(
        listRecipes: ListRecipesUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecipesListUiState())
        val uiState: StateFlow<RecipesListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                listRecipes()
                    .catch { error ->
                        _uiState.update {
                            it.copy(isLoading = false, error = "菜谱列表加载失败：${error.message.orEmpty()}")
                        }
                    }.collect { recipes ->
                        _uiState.update {
                            it.copy(
                                rows =
                                    recipes.map { recipe ->
                                        RecipeRow(
                                            id = recipe.id.value,
                                            name = recipe.name,
                                            category = recipe.category,
                                            isActive =
                                                recipe.status ==
                                                    com.culinary.core.domain.model.EntityStatus.ACTIVE,
                                        )
                                    },
                                isLoading = false,
                            )
                        }
                    }
            }
        }
    }
