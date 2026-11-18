package com.culinary.feature.ingredients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.model.PendingIngredientImage
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.usecase.archive.ArchiveImageUseCase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.culinary.core.domain.model.Unit as UnitModel

/** 食材编辑器 ViewModel：名称、分类、默认单位、描述。 */
@HiltViewModel
class IngredientEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getIngredientDetail: GetIngredientDetailUseCase,
        private val saveIngredient: SaveIngredientUseCase,
        private val listUnits: ListUnitsUseCase,
        private val archiveImages: ArchiveImageUseCase,
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
                            val categoryPath = buildIngredientCategoryPath(assignedCategory?.id, categories)
                            val images = archiveImages.list(ArchiveImageOwner.INGREDIENT, routeId)
                            val primary = images.firstOrNull { it.role == "PRIMARY" }
                            val primaryBytes = primary?.let { archiveImages.read(it.id) }
                            val referenceImages = images.filter { it.role == "REFERENCE" }.mapNotNull { image ->
                                archiveImages.read(image.id)?.let { IngredientImageDraft(image.id, it) }
                            }
                            _uiState.update {
                                it.copy(
                                    name = result.value.ingredient.name,
                                    category = assignedCategory?.name ?: result.value.ingredient.category.orEmpty(),
                                    categoryPathIds = categoryPath,
                                    description =
                                        result.value.ingredient.description
                                            .orEmpty(),
                                    englishName = result.value.ingredient.englishName.orEmpty(),
                                    scientificName = result.value.ingredient.scientificName.orEmpty(),
                                    flavorProfile = result.value.ingredient.flavorProfile.orEmpty(),
                                    seasonality = result.value.ingredient.seasonality.orEmpty(),
                                    origin = result.value.ingredient.origin.orEmpty(),
                                    selectionNotes = result.value.ingredient.selectionNotes.orEmpty(),
                                    prepNotes = result.value.ingredient.prepNotes.orEmpty(),
                                    exampleProducts =
                                        result.value.ingredient.exampleProducts
                                            .orEmpty(),
                                    storageNotes =
                                        result.value.ingredient.storageNotes
                                            .orEmpty(),
                                    allergenNotes =
                                        result.value.ingredient.allergenNotes
                                            .orEmpty(),
                                    defaultUnit = result.value.defaultUnit ?: it.defaultUnit,
                                    primaryImageId = primary?.id,
                                    primaryImageBytes = primaryBytes,
                                    referenceImages = referenceImages,
                                    isLoading = false,
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
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value, error = null) }
        }

        fun onCategoryChange(value: String) {
            _uiState.update { it.copy(category = value, categoryPathIds = emptyList()) }
        }

        fun onCategorySelect(level: Int, categoryId: String?) {
            _uiState.update { state ->
                val base = state.categoryPathIds.take(level.coerceAtLeast(0))
                val next = if (categoryId == null) base else base + categoryId
                val leafName = next.lastOrNull()?.let { id -> state.categories.firstOrNull { it.id == id }?.name }.orEmpty()
                state.copy(category = leafName, categoryPathIds = next, error = null)
            }
        }

        fun onDescriptionChange(value: String) {
            _uiState.update { it.copy(description = value) }
        }

        fun onEnglishNameChange(value: String) {
            _uiState.update { it.copy(englishName = value) }
        }

        fun onScientificNameChange(value: String) {
            _uiState.update { it.copy(scientificName = value) }
        }

        fun onFlavorProfileChange(value: String) {
            _uiState.update { it.copy(flavorProfile = value) }
        }

        fun onSeasonalityChange(value: String) {
            _uiState.update { it.copy(seasonality = value) }
        }

        fun onOriginChange(value: String) {
            _uiState.update { it.copy(origin = value) }
        }

        fun onSelectionNotesChange(value: String) {
            _uiState.update { it.copy(selectionNotes = value) }
        }

        fun onPrepNotesChange(value: String) {
            _uiState.update { it.copy(prepNotes = value) }
        }

        fun onExampleProductsChange(value: String) {
            _uiState.update { it.copy(exampleProducts = value) }
        }

        fun onStorageNotesChange(value: String) {
            _uiState.update { it.copy(storageNotes = value) }
        }

        fun onAllergenNotesChange(value: String) {
            _uiState.update { it.copy(allergenNotes = value) }
        }

        fun onUnitChange(unit: UnitModel) {
            _uiState.update { it.copy(defaultUnit = unit) }
        }

        fun onPrimaryImagePicked(image: PendingIngredientImage) {
            _uiState.update { state ->
                val removed = state.primaryImageId?.let { state.removedImageIds + it } ?: state.removedImageIds
                state.copy(
                    primaryImageId = null,
                    primaryImageBytes = image.bytes,
                    pendingPrimaryImage = image,
                    removedImageIds = removed,
                )
            }
        }

        fun onPrimaryImageRemoved() {
            _uiState.update { state ->
                val removed = state.primaryImageId?.let { state.removedImageIds + it } ?: state.removedImageIds
                state.copy(primaryImageId = null, primaryImageBytes = null, pendingPrimaryImage = null, removedImageIds = removed)
            }
        }

        fun onReferenceImagePicked(image: PendingIngredientImage) {
            _uiState.update { it.copy(referenceImages = it.referenceImages + IngredientImageDraft(null, image.bytes)) }
        }

        fun onReferenceImageRemoved(index: Int) {
            _uiState.update { state ->
                val image = state.referenceImages.getOrNull(index) ?: return@update state
                val removed = image.id?.let { state.removedImageIds + it } ?: state.removedImageIds
                state.copy(referenceImages = state.referenceImages.filterIndexed { i, _ -> i != index }, removedImageIds = removed)
            }
        }

        private suspend fun persistImages(state: IngredientEditorUiState, ingredientId: String): Outcome<Unit> {
            state.removedImageIds.distinct().forEach { imageId ->
                when (val result = archiveImages.delete(imageId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            state.pendingPrimaryImage?.let { image ->
                when (val result = archiveImages.addIngredient(ingredientId, "PRIMARY", image.bytes, image.mimeType)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            state.referenceImages.filter { it.id == null }.forEach { image ->
                when (val result = archiveImages.addIngredient(ingredientId, "REFERENCE", image.bytes, "image/jpeg")) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            return Outcome.success(Unit)
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
                            flavorProfile = state.flavorProfile,
                            seasonality = state.seasonality,
                            origin = state.origin,
                            selectionNotes = state.selectionNotes,
                            prepNotes = state.prepNotes,
                            exampleProducts = state.exampleProducts,
                            storageNotes = state.storageNotes,
                            allergenNotes = state.allergenNotes,
                        ),
                    )
                when (result) {
                    is Outcome.Success -> {
                        when (val imageResult = persistImages(state, result.value.value)) {
                            is Outcome.Failure -> {
                                _uiState.update { it.copy(isSaving = false, error = IngredientErrorMessages.describe(imageResult.error)) }
                                return@launch
                            }
                            is Outcome.Success -> Unit
                        }
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
                        _uiState.update { it.copy(isSaving = false, saved = true) }
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
