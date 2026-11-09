package com.culinary.feature.sauces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelationDraft
import com.culinary.core.domain.model.PendingSauceImage
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceDifficulty
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceImageOwner
import com.culinary.core.domain.model.SauceImageRole
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.usecase.archive.AssignArchiveCategoryUseCase
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.archive.ListArchiveCategoriesUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.culinary.core.domain.usecase.sauce.ArchiveSauceUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import com.culinary.core.domain.usecase.sauce.PublishSauceVersionUseCase
import com.culinary.core.domain.usecase.sauce.SauceImageUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftInput
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceInput
import com.culinary.core.domain.usecase.sauce.SaveSauceRelationsUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceUseCase
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
 * 酱汁档案编辑器。
 *
 * 保存语义（任务书确认）：
 * - 新档案 / 从未发布过：保存并发布首个版本；
 * - 已有已发布版本：只创建或更新待发布草稿，发布入口留在详情页；
 * - 再次编辑已有草稿：更新同一草稿，不重复创建。
 */
@Suppress("TooManyFunctions", "LargeClass") // 编辑器集中管理原料/步骤/关系/图片/保存
@HiltViewModel
class SauceEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getSauceDetail: GetSauceDetailUseCase,
        private val saveSauce: SaveSauceUseCase,
        private val saveSauceDraft: SaveSauceDraftUseCase,
        private val publishSauceVersion: PublishSauceVersionUseCase,
        private val archiveSauce: ArchiveSauceUseCase,
        private val listUnits: ListUnitsUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listSauces: ListSaucesUseCase,
        private val observeRelations: ObserveKitchenRelationsUseCase,
        private val saveSauceRelations: SaveSauceRelationsUseCase,
        private val sauceImages: SauceImageUseCase,
        private val listArchiveCategories: ListArchiveCategoriesUseCase = ListArchiveCategoriesUseCase(),
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase =
            GetArchiveCategoryForRecordUseCase(),
        private val assignArchiveCategory: AssignArchiveCategoryUseCase = AssignArchiveCategoryUseCase(),
    ) : ViewModel() {
        private val routeSauceId: String? =
            savedStateHandle
                .get<String>(SaucesRoutes.ARG_SAUCE_ID)
                ?.takeUnless { it == SaucesRoutes.NEW_ID }

        private val _uiState = MutableStateFlow(SauceEditorUiState(sauceId = routeSauceId))
        val uiState: StateFlow<SauceEditorUiState> = _uiState.asStateFlow()

        private var keyCounter = 0

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val units = runCatching { listUnits() }.getOrDefault(emptyList())
                val ingredients = runCatching { listIngredients().first() }.getOrDefault(emptyList())
                val sauces = runCatching { listSauces().first() }.getOrDefault(emptyList())
                val categories =
                    runCatching { listArchiveCategories(ArchiveLibrary.SAUCE).first() }
                        .getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        units = units,
                        ingredients = ingredients,
                        sauceOptions = sauces,
                        categories = categories,
                        yieldUnit = it.yieldUnit ?: units.firstOrNull(),
                    )
                }
                if (routeSauceId != null) {
                    when (val result = getSauceDetail(SauceId(routeSauceId))) {
                        is Outcome.Success -> applyDetail(result.value)
                        is Outcome.Failure ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = SauceErrorMessages.describe(result.error),
                                )
                            }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, isDirty = false, editingVersionNo = 1) }
                }
            }
        }

        @Suppress("LongMethod") // 一次性映射档案、草稿、步骤、关系与图片
        private suspend fun applyDetail(detail: SauceDetail) {
            val version =
                detail.versions.firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
                    ?: detail.currentVersion
                    ?: detail.versions.firstOrNull()
            val labels = detail.lines.associateBy { it.lineNo }
            val ingredientById = _uiState.value.ingredients.associateBy { it.id.value }
            val lines =
                version?.components.orEmpty().map { component ->
                    val line = labels[component.lineNo]
                    val ingredient =
                        (component.target as? ComponentTarget.Ingredient)?.let {
                            ingredientById[it.id.value]
                        }
                    val originalName = ingredient?.name ?: line?.label ?: fallbackLabel(component.target)
                    SauceLineEditor(
                        key = "line-${keyCounter++}",
                        target = component.target,
                        name = component.labelOverride ?: originalName,
                        nameEn = component.labelOverrideEn ?: ingredient?.englishName.orEmpty(),
                        originalName = originalName,
                        originalNameEn = ingredient?.englishName.orEmpty(),
                        amount = formatAmount(component.quantity.amount),
                        unit = component.quantity.unit,
                        isOptional = component.isOptional,
                    )
                }
            val sauceImageList =
                runCatching { sauceImages.listForSauce(detail.sauce.id.value) }.getOrDefault(emptyList())
            val versionImages =
                version
                    ?.let {
                        runCatching { sauceImages.listForVersion(it.id.value) }.getOrDefault(emptyList())
                    }.orEmpty()
            val allImages = sauceImageList + versionImages
            val imageBytes =
                allImages
                    .mapNotNull { image ->
                        runCatching { sauceImages.read(image.id) }.getOrNull()?.let { image.id to it }
                    }.toMap()
            val steps =
                SauceStepCodec
                    .decode(version?.stepsJson, version?.instructions) { "step-${keyCounter++}" }
                    .mapIndexed { index, step ->
                        step.copy(image = versionImages.firstOrNull { it.stepNo == index + 1 })
                    }
            val relationRecords =
                runCatching { observeRelations(detail.sauce.id.value).first() }
                    .getOrDefault(emptyList())
                    .filter { it.targetKind == KitchenRecordKind.SAUCE }
            val relations =
                relationRecords.map { relation ->
                    val target =
                        _uiState.value.sauceOptions.firstOrNull {
                            it.id.value == relation.targetRecordId
                        }
                    SauceRelationEditor(
                        sauceId = relation.targetRecordId,
                        name = relation.targetLabel,
                        nameEn = target?.englishName.orEmpty(),
                        relationLabel = relation.relationLabel.ifBlank { "衍生酱汁" },
                    )
                }
            val assignedCategory =
                runCatching { getArchiveCategoryForRecord(KitchenRecordKind.SAUCE, detail.sauce.id.value) }
                    .getOrNull()
            val categoryPath =
                assignedCategory?.let { buildCategoryPathIds(it.id, _uiState.value.categories) }.orEmpty()
            _uiState.update {
                it.copy(
                    name = detail.sauce.name,
                    family = detail.sauce.family.orEmpty(),
                    englishName = detail.sauce.englishName.orEmpty(),
                    description = detail.sauce.description.orEmpty(),
                    difficulty = detail.sauce.difficulty,
                    yieldAmount =
                        version
                            ?.yield
                            ?.amount
                            ?.let(::formatAmount)
                            .orEmpty(),
                    yieldUnit = version?.yield?.unit ?: it.yieldUnit,
                    lines = lines,
                    steps = steps,
                    application = detail.sauce.usageNotes.orEmpty(),
                    notes = detail.sauce.adjustableNotes.orEmpty(),
                    heroImage = sauceImageList.firstOrNull { it.role == SauceImageRole.PRIMARY },
                    heroCaption = detail.sauce.heroCaption.orEmpty(),
                    noteImages = sauceImageList.filter { it.role == SauceImageRole.REFERENCE },
                    imageBytes = imageBytes,
                    relations = relations,
                    updatedAtEpochMillis = detail.sauce.updatedAtEpochMillis,
                    editingDraftId =
                        detail.versions
                            .firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
                            ?.id
                            ?.value,
                    editingVersionNo =
                        detail.versions
                            .firstOrNull { it.status == com.culinary.core.domain.model.VersionStatus.DRAFT }
                            ?.versionNo
                            ?: ((detail.versions.maxOfOrNull { v -> v.versionNo } ?: 0) + 1),
                    hasPublishedVersion = detail.hasPublishedVersion,
                    categoryPathIds = categoryPath,
                    isTestRecord =
                        detail.sauce.seedVersion == com.culinary.core.domain.model.ARCHIVE_CATEGORY_TEST_BATCH,
                    isLoading = false,
                    isDirty = false,
                )
            }
        }

        private fun fallbackLabel(target: ComponentTarget): String =
            when (target) {
                is ComponentTarget.Ingredient -> "食材"
                is ComponentTarget.Recipe -> "子菜谱（只读保留）"
                is ComponentTarget.Sauce -> "子酱汁（只读保留）"
            }

        private fun mutate(transform: (SauceEditorUiState) -> SauceEditorUiState) {
            _uiState.update { state ->
                transform(state).copy(isDirty = true, versionDirty = true, error = null)
            }
        }

        fun onNameChange(value: String) = mutate { it.copy(name = value) }

        fun onEnglishNameChange(value: String) = mutate { it.copy(englishName = value) }

        fun onFamilyChange(value: String) = mutate { it.copy(family = value) }

        fun onCategorySelect(
            level: Int,
            categoryId: String?,
        ) {
            _uiState.update { state ->
                val base = state.categoryPathIds.take(level.coerceAtLeast(0))
                val next =
                    if (categoryId == null) {
                        base
                    } else {
                        base + categoryId
                    }
                val leafName =
                    next
                        .lastOrNull()
                        ?.let { id -> state.categories.firstOrNull { it.id == id }?.name }
                        ?: state.family
                state.copy(
                    categoryPathIds = next,
                    family = leafName,
                    isDirty = true,
                    error = null,
                )
            }
        }

        fun onDescriptionChange(value: String) = mutate { it.copy(description = value) }

        fun onDifficultyChange(value: SauceDifficulty) = mutate { it.copy(difficulty = value) }

        fun onYieldAmountChange(value: String) = mutate { it.copy(yieldAmount = value) }

        fun onYieldUnitChange(unit: UnitModel) = mutate { it.copy(yieldUnit = unit) }

        fun onApplicationChange(value: String) = mutate { it.copy(application = value) }

        fun onNotesChange(value: String) = mutate { it.copy(notes = value) }

        fun onHeroCaptionChange(value: String) = mutate { it.copy(heroCaption = value) }

        fun addTextNote() =
            mutate { state ->
                val marks = if (state.notes.isBlank()) "" else "\n"
                state.copy(notes = "${state.notes}$marks· ")
            }

        fun onLineNameChange(
            key: String,
            value: String,
        ) = mutate { state ->
            state.copy(
                lines =
                    state.lines.map {
                        if (it.key == key && !it.isReadOnly) it.copy(name = value) else it
                    },
            )
        }

        fun onLineNameEnChange(
            key: String,
            value: String,
        ) = mutate { state ->
            state.copy(
                lines =
                    state.lines.map {
                        if (it.key == key && !it.isReadOnly) it.copy(nameEn = value) else it
                    },
            )
        }

        fun onLineAmountChange(
            key: String,
            value: String,
        ) = mutate { state ->
            state.copy(
                lines = state.lines.map { if (it.key == key) it.copy(amount = value) else it },
            )
        }

        fun onLineUnitChange(
            key: String,
            unit: UnitModel,
        ) = mutate { state ->
            state.copy(
                lines = state.lines.map { if (it.key == key) it.copy(unit = unit) else it },
            )
        }

        fun toggleOptional(key: String) =
            mutate { state ->
                state.copy(
                    lines = state.lines.map { if (it.key == key) it.copy(isOptional = !it.isOptional) else it },
                )
            }

        fun removeLine(key: String) =
            mutate { state ->
                state.copy(lines = state.lines.filterNot { it.key == key })
            }

        fun moveLine(
            from: Int,
            to: Int,
        ) = mutate { state -> state.copy(lines = state.lines.move(from, to)) }

        fun addIngredient(ingredient: Ingredient) {
            mutate { state ->
                val unit =
                    state.units.firstOrNull { it.id == ingredient.defaultUnitId }
                        ?: state.units.firstOrNull()
                        ?: return@mutate state
                state.copy(
                    lines =
                        state.lines +
                            SauceLineEditor(
                                key = "line-${keyCounter++}",
                                target = ComponentTarget.Ingredient(ingredient.id),
                                name = ingredient.name,
                                nameEn = ingredient.englishName.orEmpty(),
                                originalName = ingredient.name,
                                originalNameEn = ingredient.englishName.orEmpty(),
                                amount = "",
                                unit = unit,
                            ),
                )
            }
        }

        fun addStep() =
            mutate { state ->
                state.copy(steps = state.steps + SauceStepEditor(key = "step-${keyCounter++}"))
            }

        fun onStepTitleChange(
            key: String,
            value: String,
        ) = mutate { state ->
            state.copy(steps = state.steps.map { if (it.key == key) it.copy(title = value) else it })
        }

        fun onStepTimeChange(
            key: String,
            seconds: Int,
        ) = mutate { state ->
            state.copy(steps = state.steps.map { if (it.key == key) it.copy(timeSeconds = seconds) else it })
        }

        fun onStepBodyChange(
            key: String,
            value: String,
        ) = mutate { state ->
            state.copy(steps = state.steps.map { if (it.key == key) it.copy(body = value) else it })
        }

        fun removeStep(key: String) {
            mutate { state ->
                val step = state.steps.firstOrNull { it.key == key }
                val removed =
                    if (step?.image != null) state.removedImageIds + step.image.id else state.removedImageIds
                state.copy(
                    steps = state.steps.filterNot { it.key == key },
                    removedImageIds = removed,
                )
            }
        }

        fun moveStep(
            from: Int,
            to: Int,
        ) = mutate { state -> state.copy(steps = state.steps.move(from, to)) }

        fun setStepImage(
            key: String,
            image: PendingSauceImage,
        ) = mutate { state ->
            state.copy(
                steps = state.steps.map { if (it.key == key) it.copy(pendingImage = image) else it },
            )
        }

        fun clearStepImage(key: String) {
            mutate { state ->
                val step = state.steps.firstOrNull { it.key == key }
                val removed =
                    if (step?.image != null) state.removedImageIds + step.image.id else state.removedImageIds
                state.copy(
                    steps =
                        state.steps.map {
                            if (it.key == key) it.copy(image = null, pendingImage = null) else it
                        },
                    removedImageIds = removed,
                )
            }
        }

        fun setHeroImage(image: PendingSauceImage) = mutate { it.copy(heroImagePending = image) }

        fun clearHeroImage() {
            mutate { state ->
                val removed =
                    if (state.heroImage != null) state.removedImageIds + state.heroImage.id else state.removedImageIds
                state.copy(heroImage = null, heroImagePending = null, removedImageIds = removed)
            }
        }

        fun addNoteImage(image: PendingSauceImage) =
            mutate { state ->
                state.copy(noteImagesPending = state.noteImagesPending + image)
            }

        fun removeNoteImageAt(index: Int) {
            mutate { state ->
                if (index < state.noteImagesPending.size) {
                    state.copy(noteImagesPending = state.noteImagesPending.filterIndexed { i, _ -> i != index })
                } else {
                    val imageIndex = index - state.noteImagesPending.size
                    val image = state.noteImages.getOrNull(imageIndex)
                    state.copy(
                        noteImages = state.noteImages.filterIndexed { i, _ -> i != imageIndex },
                        removedImageIds =
                            if (image !=
                                null
                            ) {
                                state.removedImageIds + image.id
                            } else {
                                state.removedImageIds
                            },
                    )
                }
            }
        }

        fun addRelation(sauce: Sauce) {
            mutate { state ->
                if (state.relations.any { it.sauceId == sauce.id.value }) {
                    state
                } else {
                    state.copy(
                        relations =
                            state.relations +
                                SauceRelationEditor(
                                    sauceId = sauce.id.value,
                                    name = sauce.name,
                                    nameEn = sauce.englishName.orEmpty(),
                                ),
                    )
                }
            }
        }

        fun removeRelation(sauceId: String) =
            mutate { state ->
                state.copy(relations = state.relations.filterNot { it.sauceId == sauceId })
            }

        fun moveRelation(
            from: Int,
            to: Int,
        ) = mutate { state -> state.copy(relations = state.relations.move(from, to)) }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod") // 统一保存覆盖新建/已发布/草稿三态
        fun save() {
            val state = _uiState.value
            if (state.isSaving) return
            if (state.sauceId == null && state.categories.isNotEmpty() && state.categoryPathIds.isEmpty()) {
                _uiState.update { it.copy(error = "请选择分类归属。") }
                return
            }
            val name = state.name.trim()
            val englishName = state.englishName.trim()
            val amount = state.yieldAmount.toDoubleOrNull()
            val unit = state.yieldUnit ?: state.units.firstOrNull()
            val message =
                when {
                    name.isEmpty() -> "中文名称不能为空。"
                    englishName.isEmpty() -> "英文名称不能为空。"
                    amount == null || !amount.isFinite() || amount <= 0.0 -> "标准产量必须是大于 0 的数字。"
                    unit == null -> "缺少可用单位，无法保存。"
                    else -> null
                }
            if (message != null) {
                _uiState.update { it.copy(error = message) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                val sauceId =
                    when (
                        val saved =
                            saveSauce(
                                SaveSauceInput(
                                    id = state.sauceId?.let { SauceId(it) },
                                    name = name,
                                    family = state.family,
                                    englishName = englishName,
                                    description = state.description,
                                    difficulty = state.difficulty,
                                    heroCaption = state.heroCaption,
                                    usageNotes = state.application,
                                    adjustableNotes = state.notes,
                                ),
                            )
                    ) {
                        is Outcome.Success -> saved.value
                        is Outcome.Failure -> return@launch fail(saved.error)
                    }
                _uiState.update { it.copy(sauceId = sauceId.value) }
                // 分类是档案元数据；仅改分类时不创建新版本。
                if (state.sauceId != null && !state.versionDirty) {
                    state.categoryPathIds.lastOrNull()?.let { categoryId ->
                        when (
                            val result =
                                assignArchiveCategory(KitchenRecordKind.SAUCE, sauceId.value, categoryId)
                        ) {
                            is Outcome.Failure -> return@launch fail(result.error)
                            is Outcome.Success -> Unit
                        }
                    }
                    _uiState.update {
                        it.copy(isSaving = false, isDirty = false, savedSauceId = sauceId.value)
                    }
                    return@launch
                }
                val versionId =
                    when (
                        val saved =
                            saveSauceDraft(
                                SaveSauceDraftInput(
                                    sauceId = sauceId,
                                    yieldAmount = requireNotNull(amount),
                                    yieldUnit = requireNotNull(unit),
                                    components =
                                        state.lines.map { line ->
                                            com.culinary.core.domain.usecase.sauce.SauceDraftLineInput(
                                                target = line.target,
                                                amount = line.amount.toDoubleOrNull() ?: 0.0,
                                                unit = line.unit,
                                                isOptional = line.isOptional,
                                                labelOverride = if (line.isReadOnly) null else line.labelOverride,
                                                labelOverrideEn = if (line.isReadOnly) null else line.labelOverrideEn,
                                            )
                                        },
                                    instructions = SauceStepCodec.toInstructions(state.steps),
                                    stepsJson = SauceStepCodec.encode(state.steps),
                                    versionId = state.editingDraftId?.let { SauceVersionId(it) },
                                ),
                            )
                    ) {
                        is Outcome.Success -> saved.value
                        is Outcome.Failure -> return@launch fail(saved.error)
                    }
                state.categoryPathIds.lastOrNull()?.let { categoryId ->
                    when (val result = assignArchiveCategory(KitchenRecordKind.SAUCE, sauceId.value, categoryId)) {
                        is Outcome.Failure -> return@launch fail(result.error)
                        is Outcome.Success -> Unit
                    }
                }
                var hasPublished = state.hasPublishedVersion
                if (!hasPublished) {
                    when (val result = publishSauceVersion(versionId)) {
                        is Outcome.Success -> {
                            hasPublished = true
                            _uiState.update {
                                it.copy(hasPublishedVersion = true, editingDraftId = null)
                            }
                        }

                        is Outcome.Failure -> return@launch fail(result.error)
                    }
                } else {
                    _uiState.update { it.copy(editingDraftId = versionId.value) }
                }
                when (
                    val relationResult =
                        saveSauceRelations(
                            sauceId.value,
                            state.relations.mapIndexed { index, relation ->
                                KitchenRelationDraft(
                                    targetRecordId = relation.sauceId,
                                    targetKind = KitchenRecordKind.SAUCE,
                                    targetLabel = relation.name,
                                    relationLabel = relation.relationLabel,
                                    sortOrder = index,
                                )
                            },
                        )
                ) {
                    is Outcome.Failure -> return@launch fail(relationResult.error)
                    is Outcome.Success -> Unit
                }
                when (val imageResult = persistImages(state, sauceId.value, versionId.value)) {
                    is Outcome.Failure -> return@launch fail(imageResult.error)
                    is Outcome.Success -> Unit
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedSauceId = sauceId.value,
                        hasPublishedVersion = hasPublished,
                        // 已发布档案保存后保留草稿 id，避免再次保存创建重复草稿；
                        // 新档案首版发布后无需草稿指针。
                        editingDraftId = if (state.hasPublishedVersion) versionId.value else null,
                        isDirty = false,
                    )
                }
            }
        }

        @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
        private suspend fun persistImages(
            state: SauceEditorUiState,
            sauceId: String,
            versionId: String,
        ): Outcome<Unit> {
            state.removedImageIds.forEach { imageId ->
                when (val result = sauceImages.delete(imageId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            if (state.heroImagePending != null) {
                when (
                    val result =
                        sauceImages.add(
                            owner = SauceImageOwner.SAUCE,
                            ownerId = sauceId,
                            role = SauceImageRole.PRIMARY,
                            stepNo = null,
                            bytes = state.heroImagePending.bytes,
                            mimeType = state.heroImagePending.mimeType,
                        )
                ) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
                state.heroImage?.let { old ->
                    when (val result = sauceImages.delete(old.id)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> Unit
                    }
                }
            }
            state.noteImagesPending.forEach { image ->
                when (
                    val result =
                        sauceImages.add(
                            owner = SauceImageOwner.SAUCE,
                            ownerId = sauceId,
                            role = SauceImageRole.REFERENCE,
                            stepNo = null,
                            bytes = image.bytes,
                            mimeType = image.mimeType,
                        )
                ) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            val stepNumberUpdates = mutableListOf<Pair<String, Int>>()
            state.steps.forEachIndexed { index, step ->
                if (step.pendingImage != null) {
                    when (
                        val result =
                            sauceImages.add(
                                owner = SauceImageOwner.SAUCE_VERSION,
                                ownerId = versionId,
                                role = SauceImageRole.PROCESS,
                                stepNo = index + 1,
                                bytes = step.pendingImage.bytes,
                                mimeType = step.pendingImage.mimeType,
                            )
                    ) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> Unit
                    }
                    step.image?.let { old ->
                        when (val result = sauceImages.delete(old.id)) {
                            is Outcome.Failure -> return result
                            is Outcome.Success -> Unit
                        }
                    }
                } else if (step.image != null && step.image.stepNo != index + 1) {
                    stepNumberUpdates += step.image.id to (index + 1)
                }
            }
            if (stepNumberUpdates.isNotEmpty()) {
                when (val result = sauceImages.updateStepNumbers(stepNumberUpdates)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> Unit
                }
            }
            return Outcome.success(Unit)
        }

        fun archive() {
            val id = _uiState.value.sauceId ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                when (val result = archiveSauce(SauceId(id))) {
                    is Outcome.Success -> _uiState.update { it.copy(isSaving = false, archived = true) }
                    is Outcome.Failure -> fail(result.error)
                }
            }
        }

        private fun fail(error: com.culinary.core.common.CulinaryError) {
            _uiState.update {
                it.copy(isSaving = false, error = SauceErrorMessages.describe(error))
            }
        }

        private fun buildCategoryPathIds(
            categoryId: String,
            categories: List<ArchiveCategory>,
        ): List<String> {
            val byId = categories.associateBy { it.id }
            val path = mutableListOf<String>()
            var cursor = byId[categoryId]
            var guard = 0
            while (cursor != null && guard++ < 32) {
                path.add(0, cursor.id)
                cursor = cursor.parentId?.let(byId::get)
            }
            return path
        }

        private fun formatAmount(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        private fun <T> List<T>.move(
            from: Int,
            to: Int,
        ): List<T> {
            if (from == to || from !in indices || to !in indices) return this
            return toMutableList().apply { add(to, removeAt(from)) }
        }
    }
