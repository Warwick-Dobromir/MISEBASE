package com.culinary.feature.recipes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import com.culinary.core.domain.usecase.recipe.PublishRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.RecipeLineInput
import com.culinary.core.domain.usecase.recipe.RecipeLineTarget
import com.culinary.core.domain.usecase.recipe.RecipeOption
import com.culinary.core.domain.usecase.recipe.RecipeVersionInput
import com.culinary.core.domain.usecase.recipe.SaveRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeInput
import com.culinary.core.domain.usecase.recipe.SaveRecipeUseCase
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
 * 子菜谱草稿保存为"当前版本"引用，保存/发布时由领域规则固定为明确版本 ID（MB-D-013）。
 */
@HiltViewModel
class RecipeEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getRecipeDetail: GetRecipeDetailUseCase,
        private val saveRecipe: SaveRecipeUseCase,
        private val saveRecipeDraft: SaveRecipeDraftUseCase,
        private val publishRecipeDraft: PublishRecipeDraftUseCase,
        private val listUnits: ListUnitsUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listRecipeOptions: ListRecipeOptionsUseCase,
    ) : ViewModel() {
        private val routeId: String? =
            savedStateHandle
                .get<String>(RecipesRoutes.ARG_RECIPE_ID)
                ?.takeUnless { it == RecipesRoutes.NEW_ID }

        private val _uiState = MutableStateFlow(RecipeEditorUiState(recipeId = routeId))
        val uiState: StateFlow<RecipeEditorUiState> = _uiState.asStateFlow()

        private var lineKey = 0

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val units = runCatching { listUnits() }.getOrDefault(emptyList())
                val ingredients = runCatching { listIngredients().first() }.getOrDefault(emptyList())
                val options = runCatching { listRecipeOptions() }.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        units = units,
                        ingredients = ingredients,
                        recipeOptions = options,
                        yieldUnit = it.yieldUnit ?: units.firstOrNull(),
                    )
                }
                if (routeId != null) {
                    when (val result = getRecipeDetail(RecipeId(routeId))) {
                        is Outcome.Success -> applyDetail(result.value)
                        is Outcome.Failure ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = RecipeErrorMessages.describe(result.error),
                                )
                            }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }

        private fun applyDetail(detail: RecipeDetail) {
            val version = detail.currentVersion ?: detail.versions.firstOrNull()
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
                    category = detail.recipe.category,
                    yieldAmount =
                        version
                            ?.yield
                            ?.amount
                            ?.let(::formatAmount)
                            .orEmpty(),
                    yieldUnit = version?.yield?.unit ?: it.units.firstOrNull(),
                    servings = (version?.servings ?: detail.baseServings).toString(),
                    lines = lines,
                    isLoading = false,
                    editingVersionNo = (detail.versions.maxOfOrNull { v -> v.versionNo } ?: 0) + 1,
                    hasPublishedVersion = detail.hasPublishedVersion,
                )
            }
        }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value, error = null) }
        }

        fun onCategoryChange(category: RecipeCategory) {
            _uiState.update { it.copy(category = category) }
        }

        fun onYieldAmountChange(value: String) {
            _uiState.update { it.copy(yieldAmount = value) }
        }

        fun onYieldUnitChange(unit: UnitModel) {
            _uiState.update { it.copy(yieldUnit = unit) }
        }

        fun onServingsChange(value: String) {
            _uiState.update { it.copy(servings = value) }
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
            _uiState.update { state ->
                val unit = state.units.firstOrNull() ?: return@update state
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

        fun save(publish: Boolean) {
            val submission = buildSubmission() ?: return
            viewModelScope.launch { persist(submission, publish) }
        }

        private suspend fun persist(
            submission: Submission,
            publish: Boolean,
        ) {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val recipeId =
                when (
                    val saved =
                        saveRecipe(
                            SaveRecipeInput(
                                name = submission.name,
                                category = submission.category,
                                id = submission.recipeId,
                            ),
                        )
                ) {
                    is Outcome.Success -> saved.value
                    is Outcome.Failure -> return fail(saved.error)
                }
            val input = submission.toVersionInput(recipeId)
            val versionResult: Outcome<RecipeVersionId> =
                if (publish) publishRecipeDraft(input) else saveRecipeDraft(input)
            when (versionResult) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return fail(versionResult.error)
            }
            _uiState.update {
                it.copy(isSaving = false, savedRecipeId = recipeId.value, error = null)
            }
        }

        private fun fail(error: com.culinary.core.common.CulinaryError) {
            _uiState.update { it.copy(isSaving = false, error = RecipeErrorMessages.describe(error)) }
        }

        private fun buildSubmission(): Submission? {
            val state = _uiState.value
            val message =
                when {
                    state.name.trim().isEmpty() -> "名称不能为空。"
                    state.yieldAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } == null ->
                        "标准产量必须是大于 0 的数字。"

                    state.servings.toIntOrNull()?.takeIf { it > 0 } == null ->
                        "基准份数必须是大于 0 的整数。"

                    state.yieldUnit == null && state.units.isEmpty() -> "缺少可用单位，无法保存。"

                    else -> null
                }
            if (message != null) {
                _uiState.update { it.copy(error = message) }
                return null
            }
            val lines = parseLines(state) ?: return null
            return Submission(
                recipeId = state.recipeId?.let { RecipeId(it) },
                name = state.name.trim(),
                category = state.category,
                yieldAmount = state.yieldAmount.toDouble(),
                yieldUnit = state.yieldUnit ?: requireNotNull(state.units.firstOrNull()),
                servings = state.servings.toInt(),
                lines = lines,
            )
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
            val category: RecipeCategory,
            val yieldAmount: Double,
            val yieldUnit: UnitModel,
            val servings: Int,
            val lines: List<RecipeLineInput>,
        ) {
            fun toVersionInput(recipeId: RecipeId): RecipeVersionInput =
                RecipeVersionInput(
                    recipeId = recipeId,
                    yieldAmount = yieldAmount,
                    yieldUnit = yieldUnit,
                    servings = servings,
                    components = lines,
                )
        }
    }
