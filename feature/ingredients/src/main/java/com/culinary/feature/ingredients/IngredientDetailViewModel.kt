package com.culinary.feature.ingredients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.usecase.archive.ArchiveImageUseCase
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.GetIngredientDetailUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 食材详情 ViewModel：加载档案与换算，归档（软删除）。 */
@HiltViewModel
class IngredientDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getIngredientDetail: GetIngredientDetailUseCase,
        private val archiveIngredient: ArchiveIngredientUseCase,
        private val getRelations: ObserveKitchenRelationsUseCase,
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase =
            GetArchiveCategoryForRecordUseCase(),
        private val archiveImages: ArchiveImageUseCase = ArchiveImageUseCase(),
    ) : ViewModel() {
        private val ingredientId =
            IngredientId(savedStateHandle.get<String>(IngredientsRoutes.ARG_INGREDIENT_ID).orEmpty())
        private val _uiState = MutableStateFlow(IngredientDetailUiState())
        val uiState: StateFlow<IngredientDetailUiState> = _uiState.asStateFlow()

        init {
            load()
            viewModelScope.launch {
                getRelations(ingredientId.value).collect { relations ->
                    _uiState.update { it.copy(relations = relations) }
                }
            }
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = getIngredientDetail(ingredientId)) {
                    is Outcome.Success -> {
                        val category =
                            runCatching {
                                getArchiveCategoryForRecord(KitchenRecordKind.INGREDIENT, ingredientId.value)
                            }.getOrNull()
                        val image =
                            runCatching {
                                archiveImages.primary(ArchiveImageOwner.INGREDIENT, ingredientId.value)
                            }.getOrNull()
                        _uiState.update {
                            it.copy(
                                detail = result.value,
                                categoryPath = category?.path.orEmpty(),
                                imageBytes = image,
                                isLoading = false,
                                isArchiving = false,
                            )
                        }
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isArchiving = false,
                                error = IngredientErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        fun archive() {
            viewModelScope.launch {
                _uiState.update { it.copy(isArchiving = true, error = null) }
                when (val result = archiveIngredient(ingredientId)) {
                    is Outcome.Success -> _uiState.update { it.copy(isArchiving = false, archived = true) }
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isArchiving = false,
                                error = IngredientErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
