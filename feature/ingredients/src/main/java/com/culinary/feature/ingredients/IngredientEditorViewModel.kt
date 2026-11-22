package com.culinary.feature.ingredients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ArchiveImageOwner
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
        private val archiveImages: ArchiveImageUseCase = ArchiveImageUseCase(),
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
                            val primaryImage = runCatching {
                                archiveImages.list(ArchiveImageOwner.INGREDIENT, routeId)
                                    .firstOrNull { it.role == "PRIMARY" }
                            }.getOrNull()
                            val primaryBytes = primaryImage?.let { runCatching { archiveImages.read(it.id) }.getOrNull() }
                            _uiState.update {
                                it.copy(
                                    name = result.value.ingredient.name,
                                    category = assignedCategory?.name ?: result.value.ingredient.category.orEmpty(),
                                    categoryPathIds = categoryPath,
                                    description =
                                        result.value.ingredient.description
                                            .orEmpty(),
                                    englishName =
                                        result.value.ingredient.englishName
                                            .orEmpty(),
                                    exampleProducts =
                                        result.value.ingredient.exampleProducts
                                            .orEmpty(),
                                    storageNotes =
                                        result.value.ingredient.storageNotes
                                            .orEmpty(),
                                    allergenNotes =
                                        result.value.ingredient.allergenNotes
                                            .orEmpty(),
                                    flavorProfile = result.value.ingredient.flavorProfile.orEmpty(),
                                    culinaryUse = result.value.ingredient.culinaryUse.orEmpty(),
                                    edibleParts = result.value.ingredient.edibleParts.orEmpty(),
                                    isActive = result.value.ingredient.isActive,
                                    defaultUnit = result.value.defaultUnit ?: it.defaultUnit,
                                    imageId = primaryImage?.id,
                                    imageBytes = primaryBytes,
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
            _uiState.update { it.copy(name = value, isDirty = true, error = null) }
        }

        fun onCategoryChange(value: String) {
            _uiState.update { it.copy(category = value, categoryPathIds = emptyList(), isDirty = true) }
        }

        fun onCategorySelect(level: Int, categoryId: String?) {
            _uiState.update { state ->
                val base = state.categoryPathIds.take(level.coerceAtLeast(0))
                val next = if (categoryId == null) base else base + categoryId
                val leafName = next.lastOrNull()?.let { id -> state.categories.firstOrNull { it.id == id }?.name }.orEmpty()
                state.copy(category = leafName, categoryPathIds = next, isDirty = true, error = null)
            }
        }

        fun onDescriptionChange(value: String) {
            _uiState.update { it.copy(description = value, isDirty = true) }
        }

        fun onEnglishNameChange(value: String) {
            _uiState.update { it.copy(englishName = value, isDirty = true) }
        }

        fun onExampleProductsChange(value: String) {
            _uiState.update { it.copy(exampleProducts = value, isDirty = true) }
        }

        fun onStorageNotesChange(value: String) {
            _uiState.update { it.copy(storageNotes = value, isDirty = true) }
        }

        fun onAllergenNotesChange(value: String) {
            _uiState.update { it.copy(allergenNotes = value, isDirty = true) }
        }

        fun onFlavorProfileChange(value: String) { _uiState.update { it.copy(flavorProfile = value, isDirty = true) } }
        fun onCulinaryUseChange(value: String) { _uiState.update { it.copy(culinaryUse = value, isDirty = true) } }
        fun onEdiblePartsChange(value: String) { _uiState.update { it.copy(edibleParts = value, isDirty = true) } }
        fun onActiveChange(value: Boolean) { _uiState.update { it.copy(isActive = value, isDirty = true) } }

        fun onUnitChange(unit: UnitModel) {
            _uiState.update { it.copy(defaultUnit = unit, isDirty = true) }
        }

        fun onImagePicked(image: PendingIngredientImage) {
            _uiState.update { it.copy(pendingImage = image, imageRemoved = false, isDirty = true, error = null) }
        }

        fun onImageRemoved() {
            _uiState.update {
                it.copy(
                    pendingImage = null,
                    imageBytes = null,
                    imageRemoved = it.imageId != null,
                    isDirty = true,
                    error = null,
                )
            }
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
                            exampleProducts = state.exampleProducts,
                            storageNotes = state.storageNotes,
                            allergenNotes = state.allergenNotes,
                            flavorProfile = state.flavorProfile,
                            culinaryUse = state.culinaryUse,
                            edibleParts = state.edibleParts,
                            isActive = state.isActive,
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
                        val imageState = _uiState.value
                        if (imageState.pendingImage != null) {
                            when (val imageResult = archiveImages.addPrimaryIngredientImage(
                                result.value.value,
                                imageState.pendingImage.bytes,
                                imageState.pendingImage.mimeType,
                            )) {
                                is Outcome.Failure -> {
                                    _uiState.update {
                                        it.copy(isSaving = false, error = IngredientErrorMessages.describe(imageResult.error))
                                    }
                                    return@launch
                                }
                                is Outcome.Success -> Unit
                            }
                        } else if (imageState.imageRemoved && imageState.imageId != null) {
                            when (val imageResult = archiveImages.delete(imageState.imageId)) {
                                is Outcome.Failure -> {
                                    _uiState.update {
                                        it.copy(isSaving = false, error = IngredientErrorMessages.describe(imageResult.error))
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
