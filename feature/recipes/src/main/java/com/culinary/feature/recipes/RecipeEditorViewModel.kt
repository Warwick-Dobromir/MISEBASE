package com.culinary.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenStep
import com.culinary.core.domain.model.KitchenStepCodec
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.usecase.archive.AssignArchiveCategoryUseCase
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.archive.ListArchiveCategoriesUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelationDraft
import com.culinary.core.domain.model.PendingRecipeImage
import com.culinary.core.domain.model.RecipeImage
import com.culinary.core.domain.model.RecipeImageOwner
import com.culinary.core.domain.model.RecipeImageRole
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.culinary.core.domain.usecase.recipe.PublishStoredRecipeVersionUseCase
import com.culinary.core.domain.usecase.recipe.RecipeLineInput
import com.culinary.core.domain.usecase.recipe.RecipeLineTarget
import com.culinary.core.domain.usecase.recipe.RecipeImageUseCase
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.recipe.RecipeVersionInput
import com.culinary.core.domain.usecase.recipe.SaveRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeInput
import com.culinary.core.domain.usecase.recipe.SaveRecipeRelationsUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeUseCase
import com.culinary.core.domain.usecase.sauce.ListSauceOptionsUseCase
import com.culinary.core.domain.usecase.sauce.SauceOption
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

/**
 * 菜谱编辑器 ViewModel：档案 + 版本组成。
 *
 * 编辑已发布菜谱时，保存写入"可继续编辑的草稿"而不是覆盖发布版本；
 * 再次保存更新同一份草稿，只有用户明确点击"保存并发布"才切换当前版本。
 */
@Suppress("TooManyFunctions", "LargeClass") // 编辑器集中管理档案字段、组成、步骤与保存
@HiltViewModel
class RecipeEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getRecipeDetail: GetRecipeDetailUseCase,
        private val saveRecipe: SaveRecipeUseCase,
        private val saveRecipeDraft: SaveRecipeDraftUseCase,
        private val publishStoredRecipeVersion: PublishStoredRecipeVersionUseCase,
        private val observeRelations: ObserveKitchenRelationsUseCase,
        private val saveRecipeRelations: SaveRecipeRelationsUseCase,
        private val recipeImages: RecipeImageUseCase,
        private val listArchiveCategories: ListArchiveCategoriesUseCase = ListArchiveCategoriesUseCase(),
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase =
            GetArchiveCategoryForRecordUseCase(),
        private val assignArchiveCategory: AssignArchiveCategoryUseCase = AssignArchiveCategoryUseCase(),
        private val listUnits: ListUnitsUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listRecipeOptions: ListRecipeOptionsUseCase,
        private val listSauceOptions: ListSauceOptionsUseCase,
    ) : ViewModel() {
        private val routeId: String? =
            savedStateHandle
                .get<String>(RecipesRoutes.ARG_RECIPE_ID)
                ?.takeUnless { it == RecipesRoutes.NEW_ID }

        private val _uiState = MutableStateFlow(RecipeEditorUiState(recipeId = routeId))
        val uiState: StateFlow<RecipeEditorUiState> = _uiState.asStateFlow()

        private var lineKey = 0
        private var stepKey = 0

        init {
            load()
            if (routeId != null) {
                viewModelScope.launch {
                    observeRelations(routeId).collect { records ->
                        val relations =
                            records
                                .filter { it.targetKind == KitchenRecordKind.SAUCE || it.targetKind == KitchenRecordKind.RECIPE }
                                .map { relation ->
                                    RecipeRelationEditor(
                                        targetKind = relation.targetKind,
                                        targetId = relation.targetRecordId,
                                        name = relation.targetLabel,
                                        relationLabel = relation.relationLabel,
                                    )
                                }
                        _uiState.update { it.copy(relations = relations) }
                    }
                }
            }
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val units = runCatching { listUnits() }.getOrDefault(emptyList())
                val ingredients = runCatching { listIngredients().first() }.getOrDefault(emptyList())
                val options = runCatching { listRecipeOptions() }.getOrDefault(emptyList())
                val sauceOptions = runCatching { listSauceOptions() }.getOrDefault(emptyList())
                val categories =
                    runCatching { listArchiveCategories(ArchiveLibrary.RECIPE).first() }
                        .getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        units = units,
                        ingredients = ingredients,
                        recipeOptions = options,
                        sauceOptions = sauceOptions,
                        categories = categories,
                        yieldUnit = it.yieldUnit ?: units.firstOrNull(),
                    )
                }
                if (routeId != null) {
                    when (val result = getRecipeDetail(RecipeId(routeId))) {
                        is Outcome.Success -> {
                            applyDetail(result.value)
                            loadImages(result.value)
                        }
                        is Outcome.Failure ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = RecipeErrorMessages.describe(result.error),
                                )
                            }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, editingVersionNo = 1) }
                }
            }
        }

        private suspend fun applyDetail(detail: RecipeDetail) {
            val draft = detail.versions.firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
            val version = draft ?: detail.currentVersion ?: detail.versions.firstOrNull()
            val assignedCategory =
                runCatching { getArchiveCategoryForRecord(KitchenRecordKind.RECIPE, detail.recipe.id.value) }
                    .getOrNull()
            var decodedKeys = 0
            val steps =
                runCatching {
                    KitchenStepCodec.decode(version?.stepsJson, version?.instructions) {
                        "step-${decodedKeys++}"
                    }
                }.getOrDefault(emptyList())
            val lines =
                detail.components.mapNotNull { component ->
                    val unit = component.quantity.unit
                    RecipeLineEditor(
                        key = "line-${lineKey++}",
                        kind = component.kind,
                        name = component.label,
                        amount = formatAmount(component.quantity.amount),
                        unit = unit,
                        isOptional = component.isOptional,
                        ingredientId = component.ingredientId,
                        recipeId = component.recipeId,
                        recipeVersionId = component.recipeVersionId,
                        recipeVersionNo = component.referencedVersionNo,
                        sauceId = component.sauceId,
                        sauceVersionId = component.sauceVersionId,
                        sauceVersionNo = component.referencedVersionNo,
                    )
                }
            _uiState.update {
                it.copy(
                    name = detail.recipe.name,
                    englishName = detail.recipe.englishName.orEmpty(),
                    description = detail.recipe.description.orEmpty(),
                    category = detail.recipe.category,
                    minimumCapacity = detail.recipe.difficulty.orEmpty(),
                    cuisine = detail.recipe.cuisine.orEmpty(),
                    prepMinutes = detail.recipe.prepMinutes?.toString().orEmpty(),
                    cookMinutes = detail.recipe.cookMinutes?.toString().orEmpty(),
                    allergenNotes = detail.recipe.allergenNotes.orEmpty(),
                    notes = detail.recipe.notes.orEmpty(),
                    seedNote = detail.recipe.seedNote.orEmpty(),
                    yieldAmount =
                        version
                            ?.yield
                            ?.amount
                            ?.let(::formatAmount)
                            .orEmpty(),
                    yieldUnit = version?.yield?.unit ?: it.units.firstOrNull(),
                    minRangeAmount =
                        version
                            ?.minRange
                            ?.amount
                            ?.let(::formatAmount)
                            .orEmpty(),
                    minRangeUnit = version?.minRange?.unit ?: version?.yield?.unit ?: it.units.firstOrNull(),
                    servings = (version?.servings ?: detail.baseServings).toString(),
                    lines = lines,
                    steps =
                        steps.map { step ->
                            RecipeStepEditor(
                                key = "step-${stepKey++}",
                                title = step.title,
                                body = step.body,
                                timeSeconds = step.timeSeconds,
                            )
                        },
                    isLoading = false,
                    archiveCategoryId = assignedCategory?.id,
                    editingVersionNo = draft?.versionNo ?: ((detail.versions.maxOfOrNull { v -> v.versionNo } ?: 0) + 1),
                    editingDraftId = draft?.id?.value,
                    hasPublishedVersion = detail.hasPublishedVersion,
                    updatedAtEpochMillis = detail.recipe.updatedAtEpochMillis,
                    isDirty = false,
                    versionDirty = false,
                )
            }
        }

        private suspend fun loadImages(detail: RecipeDetail) {
            val recipeId = detail.recipe.id.value
            val version =
                detail.versions.firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
                    ?: detail.currentVersion
                    ?: detail.versions.firstOrNull()
            val recipeImageList = runCatching { recipeImages.listForRecipe(recipeId) }.getOrDefault(emptyList())
            val versionImages =
                version
                    ?.let { shown ->
                        runCatching { recipeImages.listForVersion(shown.id.value) }.getOrDefault(emptyList())
                    }.orEmpty()
            val all = recipeImageList + versionImages
            val bytes =
                all.mapNotNull { image ->
                    runCatching { recipeImages.read(image.id) }.getOrNull()?.let { image.id to it }
                }.toMap()
            val hero = recipeImageList.firstOrNull { it.role == RecipeImageRole.PRIMARY }
            val notes = recipeImageList.filter { it.role == RecipeImageRole.REFERENCE }
            val imagesByStep =
                versionImages
                    .filter { it.role == RecipeImageRole.PROCESS && it.stepNo != null }
                    .associateBy { it.stepNo ?: 0 }
            _uiState.update { state ->
                state.copy(
                    heroImage = hero,
                    noteImages = notes,
                    imageBytes = bytes,
                    steps =
                        state.steps.mapIndexed { index, step ->
                            step.copy(image = imagesByStep[index + 1])
                        },
                )
            }
        }

        private fun mutateArchive(transform: (RecipeEditorUiState) -> RecipeEditorUiState) {
            _uiState.update { state ->
                transform(state).copy(isDirty = true, error = null)
            }
        }

        private fun mutateVersion(transform: (RecipeEditorUiState) -> RecipeEditorUiState) {
            _uiState.update { state ->
                transform(state).copy(isDirty = true, versionDirty = true, error = null)
            }
        }

        fun onNameChange(value: String) = mutateArchive { it.copy(name = value) }

        fun onEnglishNameChange(value: String) = mutateArchive { it.copy(englishName = value) }

        fun onDescriptionChange(value: String) = mutateArchive { it.copy(description = value) }

        fun onCategoryChange(category: RecipeCategory) = mutateArchive { it.copy(category = category) }

        /**
         * "最小制作容量"沿用既有 `recipe.difficulty` 文本列；
         * 本次只改显示名称，不改数据库结构，也不参与计时或库存计算。
         */
        fun onMinimumCapacityChange(value: String) = mutateArchive { it.copy(minimumCapacity = value) }

        fun onCuisineChange(value: String) = mutateArchive { it.copy(cuisine = value) }

        fun onPrepMinutesChange(value: String) = mutateArchive { it.copy(prepMinutes = value) }

        fun onCookMinutesChange(value: String) = mutateArchive { it.copy(cookMinutes = value) }

        fun onAllergenNotesChange(value: String) = mutateArchive { it.copy(allergenNotes = value) }

        fun onNotesChange(value: String) = mutateArchive { it.copy(notes = value) }

        fun onArchiveCategorySelect(categoryId: String) = mutateArchive { it.copy(archiveCategoryId = categoryId) }

        fun addSauceRelation(option: SauceOption) {
            mutateArchive { state ->
                if (state.relations.any { it.targetKind == KitchenRecordKind.SAUCE && it.targetId == option.sauce.id.value }) {
                    state
                } else {
                    state.copy(
                        relations =
                            state.relations +
                                RecipeRelationEditor(
                                    targetKind = KitchenRecordKind.SAUCE,
                                    targetId = option.sauce.id.value,
                                    name = option.sauce.name,
                                    relationLabel = "关联酱汁",
                                ),
                    )
                }
            }
        }

        fun addRecipeRelation(option: RecipeOption) {
            mutateArchive { state ->
                if (state.relations.any { it.targetKind == KitchenRecordKind.RECIPE && it.targetId == option.recipe.id.value }) {
                    state
                } else {
                    state.copy(
                        relations =
                            state.relations +
                                RecipeRelationEditor(
                                    targetKind = KitchenRecordKind.RECIPE,
                                    targetId = option.recipe.id.value,
                                    name = option.recipe.name,
                                    relationLabel = "关联菜谱",
                                ),
                    )
                }
            }
        }

        fun removeRelation(targetId: String) {
            mutateArchive { state -> state.copy(relations = state.relations.filterNot { it.targetId == targetId }) }
        }

        fun moveRelation(from: Int, to: Int) {
            mutateArchive { state ->
                if (from == to || from !in state.relations.indices || to !in state.relations.indices) {
                    state
                } else {
                    state.copy(
                        relations = state.relations.toMutableList().apply { add(to, removeAt(from)) },
                    )
                }
            }
        }

        fun setHeroImage(image: PendingRecipeImage) {
            mutateArchive { it.copy(heroImagePending = image) }
        }

        fun clearHeroImage() {
            mutateArchive { state ->
                val removed = state.heroImage?.let { state.removedImageIds + it.id } ?: state.removedImageIds
                state.copy(heroImage = null, heroImagePending = null, removedImageIds = removed)
            }
        }

        fun setStepImage(
            key: String,
            image: PendingRecipeImage,
        ) {
            mutateVersion { state ->
                state.copy(steps = state.steps.map { if (it.key == key) it.copy(pendingImage = image) else it })
            }
        }

        fun clearStepImage(key: String) {
            mutateVersion { state ->
                val step = state.steps.firstOrNull { it.key == key }
                val removed = step?.image?.let { state.removedImageIds + it.id } ?: state.removedImageIds
                state.copy(
                    steps = state.steps.map { if (it.key == key) it.copy(image = null, pendingImage = null) else it },
                    removedImageIds = removed,
                )
            }
        }

        fun addNoteImage(image: PendingRecipeImage) {
            mutateArchive { it.copy(noteImagesPending = it.noteImagesPending + image) }
        }

        fun removeNoteImageAt(index: Int) {
            mutateArchive { state ->
                if (index < state.noteImages.size) {
                    val existing = state.noteImages[index]
                    state.copy(
                        noteImages = state.noteImages.filterIndexed { i, _ -> i != index },
                        removedImageIds = state.removedImageIds + existing.id,
                    )
                } else {
                    val pendingIndex = index - state.noteImages.size
                    state.copy(
                        noteImagesPending = state.noteImagesPending.filterIndexed { i, _ -> i != pendingIndex },
                    )
                }
            }
        }

        fun onYieldAmountChange(value: String) = mutateVersion { it.copy(yieldAmount = value) }

        fun onYieldUnitChange(unit: UnitModel) = mutateVersion { it.copy(yieldUnit = unit) }

        fun onMinRangeAmountChange(value: String) = mutateVersion { it.copy(minRangeAmount = value) }

        fun onMinRangeUnitChange(unit: UnitModel) = mutateVersion { it.copy(minRangeUnit = unit) }

        fun onServingsChange(value: String) = mutateVersion { it.copy(servings = value) }

        fun addIngredient(ingredient: Ingredient) {
            mutateVersion { state ->
                val unit =
                    state.units.firstOrNull { it.id == ingredient.defaultUnitId }
                        ?: state.units.firstOrNull()
                        ?: return@mutateVersion state
                state.copy(
                    lines =
                        state.lines +
                            RecipeLineEditor(
                                key = "line-${lineKey++}",
                                kind = RecipeComponentKind.INGREDIENT,
                                name = ingredient.name,
                                amount = "",
                                unit = unit,
                                ingredientId = ingredient.id,
                            ),
                )
            }
        }

        fun addRecipeOption(option: RecipeOption) {
            val current = option.currentVersion ?: return
            mutateVersion { state ->
                val unit = state.units.firstOrNull() ?: return@mutateVersion state
                state.copy(
                    lines =
                        state.lines +
                            RecipeLineEditor(
                                key = "line-${lineKey++}",
                                kind = RecipeComponentKind.RECIPE,
                                name = option.recipe.name,
                                amount = "",
                                unit = unit,
                                recipeId = option.recipe.id,
                                recipeVersionNo = current.versionNo,
                            ),
                )
            }
        }

        fun addSauceOption(option: SauceOption) {
            val current = option.currentVersion ?: return
            mutateVersion { state ->
                val unit = state.units.firstOrNull() ?: return@mutateVersion state
                state.copy(
                    lines =
                        state.lines +
                            RecipeLineEditor(
                                key = "line-${lineKey++}",
                                kind = RecipeComponentKind.SAUCE,
                                name = option.sauce.name,
                                amount = "",
                                unit = unit,
                                sauceId = option.sauce.id,
                                sauceVersionId = current.id,
                                sauceVersionNo = current.versionNo,
                            ),
                )
            }
        }

        fun onLineAmountChange(
            key: String,
            value: String,
        ) {
            mutateVersion { state ->
                state.copy(lines = state.lines.map { if (it.key == key) it.copy(amount = value) else it })
            }
        }

        fun onLineUnitChange(
            key: String,
            unit: UnitModel,
        ) {
            mutateVersion { state ->
                state.copy(lines = state.lines.map { if (it.key == key) it.copy(unit = unit) else it })
            }
        }

        fun moveLine(
            from: Int,
            to: Int,
        ) {
            mutateVersion { state ->
                if (from !in state.lines.indices || to !in state.lines.indices || from == to) {
                    state
                } else {
                    state.copy(
                        lines = state.lines.toMutableList().apply { add(to, removeAt(from)) },
                    )
                }
            }
        }

        fun toggleOptional(key: String) {
            mutateVersion { state ->
                state.copy(
                    lines = state.lines.map { if (it.key == key) it.copy(isOptional = !it.isOptional) else it },
                )
            }
        }

        fun removeLine(key: String) {
            mutateVersion { state -> state.copy(lines = state.lines.filterNot { it.key == key }) }
        }

        fun addStep() {
            mutateVersion { state ->
                state.copy(steps = state.steps + RecipeStepEditor(key = "step-${stepKey++}"))
            }
        }

        fun onStepTitleChange(
            key: String,
            value: String,
        ) {
            mutateVersion { state ->
                state.copy(steps = state.steps.map { if (it.key == key) it.copy(title = value) else it })
            }
        }

        fun onStepBodyChange(
            key: String,
            value: String,
        ) {
            mutateVersion { state ->
                state.copy(steps = state.steps.map { if (it.key == key) it.copy(body = value) else it })
            }
        }

        fun onStepTimeChange(
            key: String,
            seconds: Int,
        ) {
            mutateVersion { state ->
                state.copy(steps = state.steps.map { if (it.key == key) it.copy(timeSeconds = seconds) else it })
            }
        }

        fun removeStep(key: String) {
            mutateVersion { state -> state.copy(steps = state.steps.filterNot { it.key == key }) }
        }

        fun moveStep(from: Int, to: Int) {
            mutateVersion { state ->
                if (from == to || from !in state.steps.indices || to !in state.steps.indices) {
                    state
                } else {
                    state.copy(
                        steps = state.steps.toMutableList().apply { add(to, removeAt(from)) },
                    )
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        /** 统一保存：新档案自动发布首版；已有正式版本只保存待发布修改。 */
        fun save() {
            val state = _uiState.value
            save(publish = state.isNew || !state.hasPublishedVersion)
        }

        fun save(publish: Boolean) {
            val submission = buildSubmission() ?: return
            viewModelScope.launch { persist(submission, publish) }
        }

        private suspend fun persist(
            submission: Submission,
            publish: Boolean,
        ) {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val state = _uiState.value
            val recipeId =
                when (
                    val saved =
                        saveRecipe(
                            SaveRecipeInput(
                                name = submission.name,
                                category = submission.category,
                                id = submission.recipeId,
                                englishName = submission.englishName,
                                description = submission.description,
                                cuisine = submission.cuisine,
                                prepMinutes = submission.prepMinutes,
                                cookMinutes = submission.cookMinutes,
                                minimumCapacity = submission.minimumCapacity,
                                allergenNotes = submission.allergenNotes,
                                notes = submission.notes,
                            ),
                        )
                ) {
                    is Outcome.Success -> saved.value
                    is Outcome.Failure -> return fail(saved.error)
                }
            if (!persistCategory(recipeId)) return
            if (!persistRelations(recipeId, state.relations)) return
            val needsVersion =
                state.isNew ||
                    publish ||
                    state.versionDirty ||
                    (!state.hasPublishedVersion && state.editingDraftId == null)
            if (!needsVersion) {
                when (val imageResult = persistImages(state, recipeId.value, null)) {
                    is Outcome.Failure -> return fail(imageResult.error)
                    is Outcome.Success -> Unit
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        recipeId = recipeId.value,
                        isDirty = false,
                        savedRecipeId = recipeId.value,
                    )
                }
                return
            }
            val input =
                submission.toVersionInput(
                    recipeId = recipeId,
                    versionId = state.editingDraftId?.let(::RecipeVersionId),
                )
            val versionId =
                when (val saved = saveRecipeDraft(input)) {
                    is Outcome.Success -> saved.value
                    is Outcome.Failure -> return fail(saved.error)
                }
            when (val imageResult = persistImages(state, recipeId.value, versionId.value)) {
                is Outcome.Failure -> return fail(imageResult.error)
                is Outcome.Success -> Unit
            }
            var hasPublished = state.hasPublishedVersion
            if (publish) {
                when (val result = publishStoredRecipeVersion(versionId)) {
                    is Outcome.Success -> hasPublished = true
                    is Outcome.Failure -> return fail(result.error)
                }
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    recipeId = recipeId.value,
                    hasPublishedVersion = hasPublished,
                    editingDraftId = if (publish) null else versionId.value,
                    isDirty = false,
                    versionDirty = false,
                    savedRecipeId = recipeId.value,
                    error = null,
                )
            }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
        private suspend fun persistImages(
            state: RecipeEditorUiState,
            recipeId: String,
            versionId: String?,
        ): Outcome<Unit> {
            state.removedImageIds.forEach { imageId ->
                when (val result = recipeImages.delete(imageId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            state.heroImagePending?.let { pending ->
                state.heroImage?.let { old ->
                    when (val result = recipeImages.delete(old.id)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> Unit
                    }
                }
                when (
                    val result =
                        recipeImages.add(
                            owner = RecipeImageOwner.RECIPE,
                            ownerId = recipeId,
                            role = RecipeImageRole.PRIMARY,
                            stepNo = null,
                            bytes = pending.bytes,
                            mimeType = pending.mimeType,
                        )
                ) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            state.noteImagesPending.forEach { pending ->
                when (
                    val result =
                        recipeImages.add(
                            owner = RecipeImageOwner.RECIPE,
                            ownerId = recipeId,
                            role = RecipeImageRole.REFERENCE,
                            stepNo = null,
                            bytes = pending.bytes,
                            mimeType = pending.mimeType,
                        )
                ) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            if (versionId == null) {
                if (state.steps.any { it.pendingImage != null }) {
                    return Outcome.failure(
                        com.culinary.core.common.CulinaryError.Validation(
                            field = "recipe.version",
                            rule = "step image requires a recipe version",
                        ),
                    )
                }
                return Outcome.success(Unit)
            }
            val stepNumberUpdates = mutableListOf<Pair<String, Int>>()
            state.steps.forEachIndexed { index, step ->
                val pending = step.pendingImage
                if (pending != null) {
                    step.image?.let { old ->
                        when (val result = recipeImages.delete(old.id)) {
                            is Outcome.Failure -> return result
                            is Outcome.Success -> Unit
                        }
                    }
                    when (
                        val result =
                            recipeImages.add(
                                owner = RecipeImageOwner.RECIPE_VERSION,
                                ownerId = versionId,
                                role = RecipeImageRole.PROCESS,
                                stepNo = index + 1,
                                bytes = pending.bytes,
                                mimeType = pending.mimeType,
                            )
                    ) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> Unit
                    }
                } else if (step.image != null && step.image.stepNo != index + 1) {
                    stepNumberUpdates += step.image.id to (index + 1)
                }
            }
            if (stepNumberUpdates.isNotEmpty()) {
                when (val result = recipeImages.updateStepNumbers(stepNumberUpdates)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            return Outcome.success(Unit)
        }

        private suspend fun persistCategory(recipeId: RecipeId): Boolean {
            val categoryId = _uiState.value.archiveCategoryId ?: return true
            return when (val result = assignArchiveCategory(KitchenRecordKind.RECIPE, recipeId.value, categoryId)) {
                is Outcome.Success -> true
                is Outcome.Failure -> {
                    fail(result.error)
                    false
                }
            }
        }

        private suspend fun persistRelations(
            recipeId: RecipeId,
            relations: List<RecipeRelationEditor>,
        ): Boolean {
            val drafts =
                relations.mapIndexed { index, relation ->
                    KitchenRelationDraft(
                        targetRecordId = relation.targetId,
                        targetKind = relation.targetKind,
                        targetLabel = relation.name,
                        relationLabel = relation.relationLabel,
                        sortOrder = index,
                    )
                }
            val sauces = drafts.filter { it.targetKind == KitchenRecordKind.SAUCE }
            val recipes = drafts.filter { it.targetKind == KitchenRecordKind.RECIPE }
            return when (val result = saveRecipeRelations(recipeId.value, sauces, recipes)) {
                is Outcome.Success -> true
                is Outcome.Failure -> {
                    fail(result.error)
                    false
                }
            }
        }

        private fun fail(error: com.culinary.core.common.CulinaryError) {
            _uiState.update { it.copy(isSaving = false, error = RecipeErrorMessages.describe(error)) }
        }

        @Suppress("ReturnCount") // 逐项校验并返回首个可定位字段错误
        private fun buildSubmission(): Submission? {
            val state = _uiState.value
            val yieldAmount = state.yieldAmount.toDoubleOrNull()
            val yieldUnit = state.yieldUnit ?: state.units.firstOrNull()
            val minAmount =
                state.minRangeAmount.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val minUnit = state.minRangeUnit ?: yieldUnit
            val message =
                when {
                    state.name.trim().isEmpty() -> "名称不能为空。"
                    yieldAmount == null || !yieldAmount.isFinite() || yieldAmount <= 0.0 ->
                        "标准产量必须是大于 0 的数字。"
                    state.servings.toIntOrNull()?.takeIf { it > 0 } == null ->
                        "基准份数必须是大于 0 的整数。"
                    yieldUnit == null -> "缺少可用单位，无法保存。"
                    state.minRangeAmount.isNotBlank() &&
                        (minAmount == null || !minAmount.isFinite() || minAmount <= 0.0) ->
                        "最小制作范围必须是大于 0 的数字。"
                    minAmount != null && minUnit == null -> "请选择最小制作范围单位。"
                    minAmount != null &&
                        minUnit != null &&
                        yieldUnit != null &&
                        !minUnit.isSameDimensionAs(yieldUnit) ->
                        "最小制作范围必须与默认产量使用同一计量维度。"
                    minAmount != null &&
                        minUnit != null &&
                        yieldUnit != null &&
                        yieldAmount != null &&
                        minAmount * minUnit.baseFactor > yieldAmount * yieldUnit.baseFactor ->
                        "最小制作范围不能大于默认产量。"
                    else -> null
                }
            if (message != null) {
                _uiState.update { it.copy(error = message) }
                return null
            }
            val prepMinutes = parseMinutes(state.prepMinutes, "准备时间")
            if (prepMinutes == INVALID_MINUTES) return null
            val cookMinutes = parseMinutes(state.cookMinutes, "烹饪时间")
            if (cookMinutes == INVALID_MINUTES) return null
            val lines = parseLines(state) ?: return null
            return Submission(
                recipeId = state.recipeId?.let(::RecipeId),
                name = state.name.trim(),
                englishName = state.englishName,
                description = state.description,
                category = state.category,
                minimumCapacity = state.minimumCapacity,
                cuisine = state.cuisine,
                prepMinutes = prepMinutes.takeIf { it >= 0 },
                cookMinutes = cookMinutes.takeIf { it >= 0 },
                allergenNotes = state.allergenNotes,
                notes = state.notes,
                yieldAmount = requireNotNull(yieldAmount),
                yieldUnit = requireNotNull(yieldUnit),
                minRangeAmount = minAmount,
                minRangeUnit = minAmount?.let { minUnit },
                servings = state.servings.toInt(),
                lines = lines,
                steps = state.steps,
            )
        }

        private fun parseMinutes(
            raw: String,
            label: String,
        ): Int {
            if (raw.isBlank()) return -1
            val value = raw.trim().toIntOrNull()
            if (value == null || value < 0) {
                _uiState.update { it.copy(error = "$label 必须是非负整数分钟。") }
                return INVALID_MINUTES
            }
            return value
        }

        private fun parseLines(state: RecipeEditorUiState): List<RecipeLineInput>? {
            val result = mutableListOf<RecipeLineInput>()
            state.lines.forEachIndexed { index, line ->
                val amount = line.amount.toDoubleOrNull()
                if (amount == null || !amount.isFinite() || amount <= 0.0) {
                    _uiState.update { it.copy(error = "第 ${index + 1} 行数量必须是大于 0 的数字。") }
                    return null
                }
                val target =
                    when (line.kind) {
                        RecipeComponentKind.INGREDIENT ->
                            line.ingredientId?.let { RecipeLineTarget.Ingredient(it) }

                        RecipeComponentKind.RECIPE -> {
                            val recipeId = line.recipeId
                            if (recipeId == null) {
                                null
                            } else if (line.recipeVersionId != null) {
                                RecipeLineTarget.RecipePinned(recipeId, line.recipeVersionId)
                            } else {
                                RecipeLineTarget.RecipeCurrent(recipeId)
                            }
                        }

                        RecipeComponentKind.SAUCE -> {
                            val sauceId = line.sauceId
                            val versionId = line.sauceVersionId
                            if (sauceId == null || versionId == null) {
                                null
                            } else {
                                RecipeLineTarget.SaucePinned(sauceId, versionId)
                            }
                        }
                    }
                if (target == null) {
                    _uiState.update { it.copy(error = "第 ${index + 1} 行的目标已失效。") }
                    return null
                }
                result +=
                    RecipeLineInput(
                        target = target,
                        amount = amount,
                        unit = line.unit,
                        isOptional = line.isOptional,
                    )
            }
            return result
        }

        private fun formatAmount(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        private data class Submission(
            val recipeId: RecipeId?,
            val name: String,
            val englishName: String,
            val description: String,
            val category: RecipeCategory,
            val minimumCapacity: String,
            val cuisine: String,
            val prepMinutes: Int?,
            val cookMinutes: Int?,
            val allergenNotes: String,
            val notes: String,
            val yieldAmount: Double,
            val yieldUnit: UnitModel,
            val minRangeAmount: Double?,
            val minRangeUnit: UnitModel?,
            val servings: Int,
            val lines: List<RecipeLineInput>,
            val steps: List<RecipeStepEditor>,
        ) {
            fun toVersionInput(
                recipeId: RecipeId,
                versionId: RecipeVersionId?,
            ): RecipeVersionInput =
                RecipeVersionInput(
                    recipeId = recipeId,
                    yieldAmount = yieldAmount,
                    yieldUnit = yieldUnit,
                    servings = servings,
                    components = lines,
                    instructions =
                        KitchenStepCodec.toInstructions(
                            steps.map { KitchenStep(it.key, it.title, it.body, it.timeSeconds) },
                        ),
                    stepsJson =
                        KitchenStepCodec.encode(
                            steps.map { KitchenStep(it.key, it.title, it.body, it.timeSeconds) },
                        ),
                    versionId = versionId,
                    minRangeAmount = minRangeAmount,
                    minRangeUnit = minRangeUnit,
                )
        }

        private companion object {
            const val INVALID_MINUTES = Int.MIN_VALUE
        }
    }
