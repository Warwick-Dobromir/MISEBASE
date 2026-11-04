package com.culinary.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.recipe.PublishStoredRecipeVersionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 菜谱详情 ViewModel：加载详情、按份数展开、发布草稿。 */
@HiltViewModel
class RecipeDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getRecipeDetail: GetRecipeDetailUseCase,
        private val expandRecipe: ExpandRecipeUseCase,
        private val publishStoredVersion: PublishStoredRecipeVersionUseCase,
    ) : ViewModel() {
        private val recipeId = RecipeId(savedStateHandle.get<String>(RecipesRoutes.ARG_RECIPE_ID).orEmpty())
        private val _uiState = MutableStateFlow(RecipeDetailUiState())
        val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = getRecipeDetail(recipeId)) {
                    is Outcome.Success -> {
                        val detail = result.value
                        _uiState.update {
                            it.copy(
                                detail = detail,
                                isLoading = false,
                                isPublishing = false,
                                targetServings = detail.baseServings.toString(),
                            )
                        }
                        runExpand(detail, detail.baseServings.toString())
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isPublishing = false,
                                error = RecipeErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        fun onTargetServingsChange(value: String) {
            _uiState.update { it.copy(targetServings = value) }
        }

        fun expand() {
            val detail = _uiState.value.detail ?: return
            viewModelScope.launch { runExpand(detail, _uiState.value.targetServings) }
        }

        private suspend fun runExpand(
            detail: RecipeDetail,
            target: String,
        ) {
            val versionId = (detail.currentVersion ?: detail.versions.firstOrNull())?.id
            if (versionId == null) {
                _uiState.update { it.copy(expanded = null, expansionError = "没有可展开的版本。") }
                return
            }
            val servings = target.toDoubleOrNull()
            if (servings == null || servings <= 0.0) {
                _uiState.update { it.copy(expansionError = "目标份数必须是大于 0 的数字。") }
                return
            }
            _uiState.update { it.copy(isExpanding = true, expansionError = null) }
            when (val result = expandRecipe(versionId, servings)) {
                is Outcome.Success ->
                    _uiState.update { it.copy(isExpanding = false, expanded = result.value) }
                is Outcome.Failure ->
                    _uiState.update {
                        it.copy(
                            isExpanding = false,
                            expanded = null,
                            expansionError = RecipeErrorMessages.describe(result.error),
                        )
                    }
            }
        }

        fun publish(versionId: RecipeVersionId) {
            viewModelScope.launch {
                _uiState.update { it.copy(isPublishing = true, error = null) }
                when (val result = publishStoredVersion(versionId)) {
                    is Outcome.Success -> load()
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isPublishing = false, error = RecipeErrorMessages.describe(result.error))
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
