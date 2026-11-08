package com.culinary.feature.sauces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.culinary.core.domain.usecase.sauce.PublishSauceVersionUseCase
import com.culinary.core.domain.usecase.sauce.SauceDraftLineInput
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftInput
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceInput
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
 * 酱汁编辑器 ViewModel：新建/编辑档案与草稿版本，保存后可选择立即发布。
 *
 * 编辑已发布酱汁时总是生成新版本号；已发布内容不会被覆盖（D-006）。
 */
@HiltViewModel
class SauceEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getSauceDetail: GetSauceDetailUseCase,
        private val saveSauce: SaveSauceUseCase,
        private val saveSauceDraft: SaveSauceDraftUseCase,
        private val publishSauceVersion: PublishSauceVersionUseCase,
        private val listUnits: ListUnitsUseCase,
        private val listIngredients: ListIngredientsUseCase,
    ) : ViewModel() {
        private val routeSauceId: String? =
            savedStateHandle
                .get<String>(SaucesRoutes.ARG_SAUCE_ID)
                ?.takeUnless { it == SaucesRoutes.NEW_ID }

        private val _uiState = MutableStateFlow(SauceEditorUiState(sauceId = routeSauceId))
        val uiState: StateFlow<SauceEditorUiState> = _uiState.asStateFlow()

        private var lineKey = 0

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val units = listUnits()
                val ingredients = runCatching { listIngredients().first() }.getOrDefault(emptyList())
                _uiState.update { it.copy(units = units, ingredients = ingredients) }
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
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        private fun applyDetail(detail: SauceDetail) {
            val version = detail.currentVersion ?: detail.versions.firstOrNull()
            val labels = detail.lines.associateBy { it.lineNo }
            val lines =
                version?.components.orEmpty().map { component ->
                    SauceLineEditor(
                        key = "line-${lineKey++}",
                        target = component.target,
                        name = labels[component.lineNo]?.label ?: fallbackLabel(component.target),
                        amount = formatAmount(component.quantity.amount),
                        unit = component.quantity.unit,
                        isOptional = component.isOptional,
                    )
                }
            _uiState.update {
                it.copy(
                    name = detail.sauce.name,
                    family = detail.sauce.family.orEmpty(),
                    englishName = detail.sauce.englishName.orEmpty(),
                    usageNotes = detail.sauce.usageNotes.orEmpty(),
                    mainIngredients = detail.sauce.mainIngredients.orEmpty(),
                    preparationSummary = detail.sauce.preparationSummary.orEmpty(),
                    keyParameters = detail.sauce.keyParameters.orEmpty(),
                    storageNotes = detail.sauce.storageNotes.orEmpty(),
                    flavorProfile = detail.sauce.flavorProfile.orEmpty(),
                    adjustableNotes = detail.sauce.adjustableNotes.orEmpty(),
                    yieldAmount =
                        version
                            ?.yield
                            ?.amount
                            ?.let(::formatAmount)
                            .orEmpty(),
                    yieldUnit = version?.yield?.unit ?: it.units.firstOrNull(),
                    lines = lines,
                    isLoading = false,
                    editingVersionNo = (detail.versions.maxOfOrNull { v -> v.versionNo } ?: 0) + 1,
                    hasPublishedVersion = detail.hasPublishedVersion,
                )
            }
        }

        private fun fallbackLabel(target: ComponentTarget): String =
            when (target) {
                is ComponentTarget.Ingredient -> "食材"
                is ComponentTarget.Recipe -> "子菜谱（只读保留）"
                is ComponentTarget.Sauce -> "子酱汁（只读保留）"
            }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value, error = null) }
        }

        fun onFamilyChange(value: String) {
            _uiState.update { it.copy(family = value) }
        }

        fun onEnglishNameChange(value: String) {
            _uiState.update { it.copy(englishName = value) }
        }

        fun onUsageNotesChange(value: String) {
            _uiState.update { it.copy(usageNotes = value) }
        }

        fun onMainIngredientsChange(value: String) {
            _uiState.update { it.copy(mainIngredients = value) }
        }

        fun onPreparationSummaryChange(value: String) {
            _uiState.update { it.copy(preparationSummary = value) }
        }

        fun onKeyParametersChange(value: String) {
            _uiState.update { it.copy(keyParameters = value) }
        }

        fun onStorageNotesChange(value: String) {
            _uiState.update { it.copy(storageNotes = value) }
        }

        fun onFlavorProfileChange(value: String) {
            _uiState.update { it.copy(flavorProfile = value) }
        }

        fun onAdjustableNotesChange(value: String) {
            _uiState.update { it.copy(adjustableNotes = value) }
        }

        fun onYieldAmountChange(value: String) {
            _uiState.update { it.copy(yieldAmount = value) }
        }

        fun onYieldUnitChange(unit: UnitModel) {
            _uiState.update { it.copy(yieldUnit = unit) }
        }

        fun addIngredient(ingredient: Ingredient) {
            _uiState.update { state ->
                val unit =
                    state.units.firstOrNull { it.id == ingredient.defaultUnitId }
                        ?: state.units.firstOrNull()
                        ?: return@update state
                state.copy(
                    lines =
                        state.lines +
                            SauceLineEditor(
                                key = "line-${lineKey++}",
                                target = ComponentTarget.Ingredient(ingredient.id),
                                name = ingredient.name,
                                amount = "",
                                unit = unit,
                            ),
                )
            }
        }

        fun onLineAmountChange(
            key: String,
            value: String,
        ) {
            _uiState.update { state ->
                state.copy(lines = state.lines.map { if (it.key == key) it.copy(amount = value) else it })
            }
        }

        fun toggleOptional(key: String) {
            _uiState.update { state ->
                state.copy(
                    lines = state.lines.map { if (it.key == key) it.copy(isOptional = !it.isOptional) else it },
                )
            }
        }

        fun removeLine(key: String) {
            _uiState.update { state -> state.copy(lines = state.lines.filterNot { it.key == key }) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        /** 保存草稿；[publish] 为 true 时保存后立即发布。 */
        fun save(publish: Boolean) {
            val submission = buildSubmission() ?: return
            viewModelScope.launch { persist(submission, publish) }
        }

        private suspend fun persist(
            submission: Submission,
            publish: Boolean,
        ) {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val sauceId =
                when (
                    val saved =
                        saveSauce(
                            SaveSauceInput(
                                id = submission.sauceId,
                                name = submission.name,
                                family = submission.family,
                                englishName = submission.englishName,
                                usageNotes = submission.usageNotes,
                                mainIngredients = submission.mainIngredients,
                                preparationSummary = submission.preparationSummary,
                                keyParameters = submission.keyParameters,
                                storageNotes = submission.storageNotes,
                                flavorProfile = submission.flavorProfile,
                                adjustableNotes = submission.adjustableNotes,
                            ),
                        )
                ) {
                    is Outcome.Success -> saved.value
                    is Outcome.Failure -> return fail(saved.error)
                }
            val versionId =
                when (
                    val saved =
                        saveSauceDraft(
                            SaveSauceDraftInput(
                                sauceId = sauceId,
                                yieldAmount = submission.yieldAmount,
                                yieldUnit = submission.yieldUnit,
                                components = submission.components,
                            ),
                        )
                ) {
                    is Outcome.Success -> saved.value
                    is Outcome.Failure -> return fail(saved.error)
                }
            if (publish) {
                when (val result = publishSauceVersion(versionId)) {
                    is Outcome.Success -> Unit
                    is Outcome.Failure -> return fail(result.error)
                }
            }
            _uiState.update { it.copy(isSaving = false, savedSauceId = sauceId.value, error = null) }
        }

        private fun fail(error: com.culinary.core.common.CulinaryError) {
            _uiState.update {
                it.copy(isSaving = false, error = SauceErrorMessages.describe(error))
            }
        }

        private fun buildSubmission(): Submission? {
            val state = _uiState.value
            val basics = prepareBasics(state) ?: return null
            val components = parseLines(state.lines) ?: return null
            return Submission(
                sauceId = state.sauceId?.let { SauceId(it) },
                name = basics.name,
                family = state.family.trim().takeIf { it.isNotEmpty() },
                englishName = state.englishName.trim().takeIf { it.isNotEmpty() },
                usageNotes = state.usageNotes.trim().takeIf { it.isNotEmpty() },
                mainIngredients = state.mainIngredients.trim().takeIf { it.isNotEmpty() },
                preparationSummary = state.preparationSummary.trim().takeIf { it.isNotEmpty() },
                keyParameters = state.keyParameters.trim().takeIf { it.isNotEmpty() },
                storageNotes = state.storageNotes.trim().takeIf { it.isNotEmpty() },
                flavorProfile = state.flavorProfile.trim().takeIf { it.isNotEmpty() },
                adjustableNotes = state.adjustableNotes.trim().takeIf { it.isNotEmpty() },
                yieldAmount = basics.yieldAmount,
                yieldUnit = basics.yieldUnit,
                components = components,
            )
        }

        private fun prepareBasics(state: SauceEditorUiState): Basics? {
            val name = state.name.trim()
            val yieldAmount = state.yieldAmount.toDoubleOrNull()
            val yieldUnit = state.yieldUnit ?: state.units.firstOrNull()
            val message =
                when {
                    name.isEmpty() -> "名称不能为空。"
                    yieldAmount == null || !yieldAmount.isFinite() || yieldAmount <= 0.0 ->
                        "标准产量必须是大于 0 的数字。"

                    yieldUnit == null -> "缺少可用单位，无法保存。"
                    else -> null
                }
            if (message != null) {
                _uiState.update { it.copy(error = message) }
                return null
            }
            return Basics(
                name = name,
                yieldAmount = requireNotNull(yieldAmount),
                yieldUnit = requireNotNull(yieldUnit),
            )
        }

        private fun parseLines(lines: List<SauceLineEditor>): List<SauceDraftLineInput>? {
            val components = mutableListOf<SauceDraftLineInput>()
            lines.forEachIndexed { index, line ->
                val amount = line.amount.toDoubleOrNull()
                if (amount == null || !amount.isFinite() || amount <= 0.0) {
                    _uiState.update { it.copy(error = "第 ${index + 1} 行数量必须是大于 0 的数字。") }
                    return null
                }
                components +=
                    SauceDraftLineInput(
                        target = line.target,
                        amount = amount,
                        unit = line.unit,
                        isOptional = line.isOptional,
                    )
            }
            return components
        }

        private fun formatAmount(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

        private data class Basics(
            val name: String,
            val yieldAmount: Double,
            val yieldUnit: UnitModel,
        )

        private data class Submission(
            val sauceId: SauceId?,
            val name: String,
            val family: String?,
            val englishName: String?,
            val usageNotes: String?,
            val mainIngredients: String?,
            val preparationSummary: String?,
            val keyParameters: String?,
            val storageNotes: String?,
            val flavorProfile: String?,
            val adjustableNotes: String?,
            val yieldAmount: Double,
            val yieldUnit: UnitModel,
            val components: List<SauceDraftLineInput>,
        )
    }
