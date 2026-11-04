package com.culinary.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.rule.inventory.CsvPurchaseListExporter
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.inventory.BuildRequirementPlanUseCase
import com.culinary.core.domain.usecase.inventory.ConfirmRequirementDeductionUseCase
import com.culinary.core.domain.usecase.inventory.GeneratePurchaseListUseCase
import com.culinary.core.domain.usecase.inventory.GetCalculationRecordUseCase
import com.culinary.core.domain.usecase.inventory.ListCalculationRecordsUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 需求计划 ViewModel：选菜谱/份数 → 展开与缺口 → 采购清单 → 确认扣减 / 记录回放。
 *
 * 规则全部在 Domain；ViewModel 只协调加载、用户动作与错误文案。
 */
@HiltViewModel
class RequirementPlanViewModel
    @Inject
    constructor(
        private val listRecipes: ListRecipeOptionsUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val buildPlan: BuildRequirementPlanUseCase,
        private val generatePurchaseList: GeneratePurchaseListUseCase,
        private val confirmDeduction: ConfirmRequirementDeductionUseCase,
        private val listRecords: ListCalculationRecordsUseCase,
        private val getRecord: GetCalculationRecordUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RequirementPlanUiState())
        val uiState: StateFlow<RequirementPlanUiState> = _uiState.asStateFlow()
        private val csvExporter = CsvPurchaseListExporter()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val recipes = runCatching { listRecipes() }.getOrDefault(emptyList())
                val ingredientNames =
                    runCatching { listIngredients().first() }
                        .getOrDefault(emptyList())
                        .associate { it.id.value to it.name }
                _uiState.update {
                    it.copy(
                        recipes = recipes,
                        ingredientNames = ingredientNames,
                        isLoading = false,
                    )
                }
                val first = recipes.firstOrNull { it.currentVersion != null }
                if (first != null && _uiState.value.selectedRecipeId == null) {
                    selectRecipe(first.recipe.id.value)
                }
                Unit
            }
        }

        fun selectRecipe(recipeId: String) {
            val option = _uiState.value.recipes.firstOrNull { it.recipe.id.value == recipeId } ?: return
            val version = option.currentVersion
            _uiState.update {
                it.copy(
                    selectedRecipeId = recipeId,
                    selectedVersionId = version?.id,
                    servings = version?.servings?.toString().orEmpty(),
                    plan = null,
                    csv = null,
                    records = emptyList(),
                    error = if (version == null) "该菜谱还没有可用的当前版本。" else null,
                )
            }
            if (version != null) loadRecords(version.id)
        }

        fun onServingsChange(value: String) {
            _uiState.update { it.copy(servings = value) }
        }

        fun calculate() {
            val versionId = _uiState.value.selectedVersionId ?: return missingVersion()
            val servings = _uiState.value.servings.toDoubleOrNull()
            if (servings == null || !servings.isFinite() || servings <= 0.0) {
                _uiState.update { it.copy(error = "目标份数必须是大于 0 的数字。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = buildPlan(versionId, servings)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(plan = result.value, isWorking = false, csv = null)
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        fun generateList() {
            val versionId = _uiState.value.selectedVersionId ?: return missingVersion()
            val servings = _uiState.value.servings.toDoubleOrNull()
            if (servings == null || !servings.isFinite() || servings <= 0.0) {
                _uiState.update { it.copy(error = "目标份数必须是大于 0 的数字。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = generatePurchaseList(versionId, servings)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                plan = result.value.plan,
                                isWorking = false,
                                message = "采购清单已生成，计算记录已确认。",
                            )
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
                loadRecords(versionId)
                Unit
            }
        }

        fun confirmDeduction() {
            val versionId = _uiState.value.selectedVersionId ?: return missingVersion()
            val servings = _uiState.value.servings.toDoubleOrNull()
            if (servings == null || !servings.isFinite() || servings <= 0.0) {
                _uiState.update { it.copy(error = "目标份数必须是大于 0 的数字。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = confirmDeduction(versionId, servings)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                message = "已确认扣减，写入 ${result.value.appliedEntryCount} 条流水。",
                            )
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
                calculate()
                Unit
            }
        }

        fun exportCsv() {
            val plan = _uiState.value.plan
            if (plan == null) {
                _uiState.update { it.copy(error = "请先生成需求计划。") }
                return
            }
            _uiState.update { it.copy(csv = csvExporter.export(plan.purchaseList)) }
        }

        fun clearCsv() {
            _uiState.update { it.copy(csv = null) }
        }

        fun replay(record: CalculationRecord) {
            viewModelScope.launch {
                val current = _uiState.value
                if (current.records.any { it.id == record.id }) {
                    _uiState.update { it.copy(replayedRecord = record) }
                    return@launch
                }
                when (val result = getRecord(record.id)) {
                    is Outcome.Success -> _uiState.update { it.copy(replayedRecord = result.value) }
                    is Outcome.Failure ->
                        _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
                }
            }
        }

        fun replayById(id: String) {
            viewModelScope.launch {
                when (val result = getRecord(CalculationRecordId(id))) {
                    is Outcome.Success -> _uiState.update { it.copy(replayedRecord = result.value) }
                    is Outcome.Failure ->
                        _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
                }
            }
        }

        fun closeReplay() {
            _uiState.update { it.copy(replayedRecord = null) }
        }

        fun clearMessages() {
            _uiState.update { it.copy(error = null, message = null) }
        }

        private fun loadRecords(versionId: RecipeVersionId) {
            viewModelScope.launch {
                when (val result = listRecords(versionId)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                records =
                                    result.value.sortedByDescending { record ->
                                        record.confirmedAt ?: record.createdAt
                                    },
                            )
                        }

                    is Outcome.Failure ->
                        _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
                }
            }
        }

        private fun missingVersion() {
            _uiState.update { it.copy(error = "请选择有当前版本的菜谱。") }
        }
    }
