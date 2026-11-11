package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.InlineError
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryRules
import com.culinary.core.domain.usecase.inventory.InventoryRulesUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/** 库存规则：FIFO 只读，天数阈值可编辑；保存后立即影响提醒与回收判定。 */
@Composable
fun RulesRoute(
    onBack: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        onBack = onBack,
        onRemindDaysChange = viewModel::onRemindDaysChange,
        onPendingDaysChange = viewModel::onPendingDaysChange,
        onRetentionDaysChange = viewModel::onRetentionDaysChange,
        onSave = viewModel::save,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 规则表单状态；天数以字符串保留用户输入，保存前统一校验。 */
data class RulesUiState(
    val fifo: Boolean = true,
    val remindDays: String = "15",
    val pendingDays: String = "20",
    val retentionDays: String = "30",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val validationError: String? = null,
    val message: String? = null,
) {
    val pendingBelowRemind: Boolean
        get() {
            val remind = remindDays.trim().toIntOrNull()
            val pending = pendingDays.trim().toIntOrNull()
            return remind != null && pending != null && pending < remind
        }
}

/** 规则 ViewModel。 */
@HiltViewModel
class RulesViewModel
    @Inject
    constructor(
        private val rules: InventoryRulesUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RulesUiState())
        val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val current = rules.read()
                _uiState.update {
                    it.copy(
                        fifo = current.fifo,
                        remindDays = current.temporaryRemindDays.toString(),
                        pendingDays = current.temporaryPendingDays.toString(),
                        retentionDays = current.recycleRetentionDays.toString(),
                        isLoading = false,
                    )
                }
            }
        }

        fun onRemindDaysChange(value: String) {
            _uiState.update { it.copy(remindDays = value, validationError = null) }
        }

        fun onPendingDaysChange(value: String) {
            _uiState.update { it.copy(pendingDays = value, validationError = null) }
        }

        fun onRetentionDaysChange(value: String) {
            _uiState.update { it.copy(retentionDays = value, validationError = null) }
        }

        fun save() {
            val state = _uiState.value
            val remind = state.remindDays.trim().toIntOrNull()
            val pending = state.pendingDays.trim().toIntOrNull()
            val retention = state.retentionDays.trim().toIntOrNull()
            val validationError =
                when {
                    remind == null || pending == null || retention == null -> "天数必须是整数。"
                    remind !in 1..3650 || retention !in 1..3650 -> "提醒与回收保留天数需在 1–3650 之间。"
                    pending < remind -> "临时库存待处理天数不能小于提醒天数。"
                    pending !in 1..3650 -> "待处理天数需在 1–3650 之间。"
                    else -> null
                }
            if (validationError != null || remind == null || pending == null || retention == null) {
                _uiState.update { it.copy(validationError = validationError ?: "天数不合法。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null, message = null) }
                val updated =
                    InventoryRules(
                        fifo = true,
                        temporaryRemindDays = remind,
                        temporaryPendingDays = pending,
                        recycleRetentionDays = retention,
                    )
                when (val result = rules.update(updated)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isSaving = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(isSaving = false, message = "规则已保存，立即生效。", validationError = null)
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

@Composable
internal fun RulesScreen(
    state: RulesUiState,
    onBack: () -> Unit,
    onRemindDaysChange: (String) -> Unit,
    onPendingDaysChange: (String) -> Unit,
    onRetentionDaysChange: (String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                onAction = onDismissError,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = CulinarySpacing.xl),
            ) {
                PageHeader(
                    eyebrow = "RULES",
                    title = "库存规则",
                    supporting = "规则保存后立即生效；批次扣减顺序在当前版本固定为先进先出。",
                    actions = { CulinaryTextButton(text = "返回", onClick = onBack) },
                )
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    state.message?.let { text ->
                        StatusBadge(
                            text = text,
                            tone = StatusTone.Success,
                            modifier = Modifier.padding(bottom = CulinarySpacing.sm),
                        )
                    }
                    SectionHeader(title = "批次扣减规则")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FIFO（先进先出）",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "当前版本固定先进先出，按入库时间分配批次，不可切换。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusBadge(text = "只读", tone = StatusTone.Neutral)
                    }
                    CulinaryRule(modifier = Modifier.padding(vertical = CulinarySpacing.sm))

                    SectionHeader(title = "临时库存")
                    RuleNumberField(
                        value = state.remindDays,
                        onValueChange = onRemindDaysChange,
                        label = "临时库存提醒天数",
                        hint = "临时库存存放超过该天数后标记为「需处理」，默认 15 天。",
                    )
                    RuleNumberField(
                        value = state.pendingDays,
                        onValueChange = onPendingDaysChange,
                        label = "临时库存待处理天数",
                        hint = "超过该天数后标记为「待处理」，默认 20 天，且不得小于提醒天数。",
                    )
                    if (state.pendingBelowRemind) {
                        InlineError(message = "待处理天数小于提醒天数，无法保存。")
                    }
                    CulinaryRule(modifier = Modifier.padding(vertical = CulinarySpacing.sm))

                    SectionHeader(title = "回收站")
                    RuleNumberField(
                        value = state.retentionDays,
                        onValueChange = onRetentionDaysChange,
                        label = "批次回收保留天数",
                        hint = "归零批次在回收站保留的天数，默认 30 天；到期由系统清理。",
                    )
                    state.validationError?.let { message ->
                        InlineError(message = message)
                    }

                    CulinaryButton(
                        text = if (state.isSaving) "正在保存" else "保存规则",
                        onClick = onSave,
                        enabled = !state.isSaving,
                        loading = state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
                    )
                    CulinaryTextButton(
                        text = "恢复为已保存的规则",
                        onClick = onReload,
                        enabled = !state.isSaving,
                    )
                    Text(
                        text = "只允许 1–3650 的整数；待处理天数必须不小于提醒天数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md)) {
        CulinaryTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xxs),
        )
    }
}
