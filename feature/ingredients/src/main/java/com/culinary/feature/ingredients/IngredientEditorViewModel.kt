package com.culinary.feature.ingredients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.usecase.archive.AssignArchiveCategoryUseCase
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.archive.ListArchiveCategoriesUseCase
import com.culinary.core.domain.usecase.ingredient.GetIngredientDetailUseCase
import com.culinary.core.domain.usecase.ingredient.SaveIngredientInput
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import com.culinary.core.domain.usecase.unit.ListUnitsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.culinary.core.domain.model.Unit as UnitModel

/** 食材档案编辑状态：新建与编辑使用同一套字段和保存逻辑。 */
@HiltViewModel
class IngredientEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getIngredientDetail: GetIngredientDetailUseCase,
        private val saveIngredient: SaveIngredientUseCase,
        private val listUnits: ListUnitsUseCase,
        private val listArchiveCategories: ListArchiveCategoriesUseCase = ListArchiveCategoriesUseCase(),
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase = GetArchiveCategoryForRecordUseCase(),
        private val assignArchiveCategory: AssignArchiveCategoryUseCase = AssignArchiveCategoryUseCase(),
    ) : ViewModel() {
        private val routeId: String? =
            savedStateHandle
                .get<String>(IngredientsRoutes.ARG_INGREDIENT_ID)
                ?.takeUnless { it == IngredientsRoutes.NEW_ID }

        private val _uiState = MutableStateFlow(IngredientEditorUiState(ingredientId = routeId))
        val uiState: StateFlow<IngredientEditorUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val units = runCatching { listUnits() }.getOrDefault(emptyList())
                val categories =
                    runCatching { listArchiveCategories(ArchiveLibrary.INGREDIENT).first() }
                        .getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        units = units,
                        categories = categories,
                        defaultUnit = it.defaultUnit ?: units.firstOrNull(),
                    )
                }
                if (routeId != null) {
                    when (val result = getIngredientDetail(IngredientId(routeId))) {
                        is Outcome.Success -> {
                            val assignedCategory =
                                runCatching {
                                    getArchiveCategoryForRecord(KitchenRecordKind.INGREDIENT, routeId)
                                }.getOrNull()
                            val ingredient = result.value.ingredient
                            val categoryPath = buildIngredientCategoryPath(assignedCategory?.id, categories)
                            _uiState.update {
                                it.copy(
                                    name = ingredient.name,
                                    category = assignedCategory?.name ?: ingredient.category.orEmpty(),
                                    categoryPathIds = categoryPath,
                                    description = ingredient.description.orEmpty(),
                                    englishName = ingredient.englishName.orEmpty(),
                                    scientificName = ingredient.scientificName.orEmpty(),
                                    exampleProducts = ingredient.exampleProducts.orEmpty(),
                                    flavorProfile = ingredient.flavorProfile.orEmpty(),
                                    seasonality = ingredient.seasonality.orEmpty(),
                                    origin = ingredient.origin.orEmpty(),
                                    selectionNotes = ingredient.selectionNotes.orEmpty(),
                                    prepNotes = ingredient.prepNotes.orEmpty(),
                                    storageNotes = ingredient.storageNotes.orEmpty(),
                                    allergenNotes = ingredient.allergenNotes.orEmpty(),
                                    defaultUnit = result.value.defaultUnit ?: it.defaultUnit,
                                    isLoading = false,
                                    isDirty = false,
                                )
                            }
                        }

                        is Outcome.Failure ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = IngredientErrorMessages.describe(result.error),
                                )
                            }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, isDirty = false) }
                }
            }
        }

        fun onNameChange(value: String) = change { copy(name = value) }
        fun onCategoryChange(value: String) = change { copy(category = value, categoryPathIds = emptyList()) }
        fun onDescriptionChange(value: String) = change { copy(description = value) }
        fun onEnglishNameChange(value: String) = change { copy(englishName = value) }
        fun onScientificNameChange(value: String) = change { copy(scientificName = value) }
        fun onExampleProductsChange(value: String) = change { copy(exampleProducts = value) }
        fun onFlavorProfileChange(value: String) = change { copy(flavorProfile = value) }
        fun onSeasonalityChange(value: String) = change { copy(seasonality = value) }
        fun onOriginChange(value: String) = change { copy(origin = value) }
        fun onSelectionNotesChange(value: String) = change { copy(selectionNotes = value) }
        fun onPrepNotesChange(value: String) = change { copy(prepNotes = value) }
        fun onStorageNotesChange(value: String) = change { copy(storageNotes = value) }
        fun onAllergenNotesChange(value: String) = change { copy(allergenNotes = value) }

        fun onCategorySelect(level: Int, categoryId: String?) {
            _uiState.update { state ->
                val base = state.categoryPathIds.take(level.coerceAtLeast(0))
                val next = if (categoryId == null) base else base + categoryId
                val leafName =
                    next.lastOrNull()?.let { id -> state.categories.firstOrNull { it.id == id }?.name }.orEmpty()
                state.copy(category = leafName, categoryPathIds = next, isDirty = true, error = null)
            }
        }

        fun onUnitChange(unit: UnitModel) {
            _uiState.update { it.copy(defaultUnit = unit, isDirty = true, error = null) }
        }

        private inline fun change(crossinline updateState: IngredientEditorUiState.() -> IngredientEditorUiState) {
            _uiState.update { updateState(it).copy(isDirty = true, error = null) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        fun save() {
            val state = _uiState.value
            val name = state.name.trim()
            if (name.isEmpty()) {
                _uiState.update { it.copy(error = "名称不能为空。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                val result =
                    saveIngredient(
                        SaveIngredientInput(
                            name = name,
                            id = state.ingredientId?.let { IngredientId(it) },
                            defaultUnitId = state.defaultUnit?.id,
                            category = state.category,
                            description = state.description,
                            englishName = state.englishName,
                            scientificName = state.scientificName,
                            exampleProducts = state.exampleProducts,
                            flavorProfile = state.flavorProfile,
                            seasonality = state.seasonality,
                            origin = state.origin,
                            selectionNotes = state.selectionNotes,
                            prepNotes = state.prepNotes,
                            storageNotes = state.storageNotes,
                            allergenNotes = state.allergenNotes,
                        ),
                    )
                when (result) {
                    is Outcome.Success -> {
                        val categoryId = state.categoryPathIds.lastOrNull()
                        if (categoryId != null) {
                            when (
                                val assignment =
                                    assignArchiveCategory(KitchenRecordKind.INGREDIENT, result.value.value, categoryId)
                            ) {
                                is Outcome.Failure -> {
                                    _uiState.update {
                                        it.copy(isSaving = false, error = IngredientErrorMessages.describe(assignment.error))
                                    }
                                    return@launch
                                }
                                is Outcome.Success -> Unit
                            }
                        }
                        _uiState.update { it.copy(isSaving = false, isDirty = false, saved = true) }
                    }
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                error = IngredientErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }
    }

private fun buildIngredientCategoryPath(
    categoryId: String?,
    categories: List<com.culinary.core.domain.model.ArchiveCategory>,
): List<String> {
    val byId = categories.associateBy { it.id }
    val path = mutableListOf<String>()
    var current = categoryId?.let(byId::get)
    var depth = 0
    while (current != null && depth++ < 32) {
        path += current.id
        current = current.parentId?.let(byId::get)
    }
    return path.asReversed()
}
