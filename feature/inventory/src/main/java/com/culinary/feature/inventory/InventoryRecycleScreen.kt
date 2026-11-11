package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.RecycleRow
import com.culinary.core.domain.usecase.inventory.BatchRecycleUseCases
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

/** 批次回收站：保留期内可恢复，到期由系统维护清理，页面不提供手动清理。 */
@Composable
fun RecycleRoute(
    onBack: () -> Unit,
    viewModel: RecycleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecycleScreen(
        state = state,
        onBack = onBack,
        onRestore = viewModel::restore,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 回收站状态；剩余天数与保留期来自库存规则。 */
data class RecycleUiState(
    val rows: List<RecycleRow> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val restorableCount: Int
        get() = rows.count { it.daysLeft > 0 }

    val expiredCount: Int
        get() = rows.count { it.daysLeft <= 0 }
}

/** 回收站 ViewModel：恢复只修复批次身份，余额保持原值。 */
@HiltViewModel
class RecycleViewModel
    @Inject
    constructor(
        private val recycle: BatchRecycleUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecycleUiState())
        val uiState: StateFlow<RecycleUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = recycle.list()) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update { it.copy(rows = result.value, isLoading = false) }
                }
            }
        }

        fun restore(batchId: String) {
            if (_uiState.value.isWorking) return
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = recycle.restore(batchId)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        _uiState.update {
                            it.copy(isWorking = false, message = "批次已恢复，余额保持归零状态。")
                        }
                        load()
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
internal fun RecycleScreen(
    state: RecycleUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
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
                    eyebrow = "RECYCLE",
                    title = "批次回收站",
                    supporting = "归零批次保留在回收站，保留期内可恢复；到期由系统清理，不提供手动清理。",
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
                    SectionHeader(
                        title = "回收站",
                        subtitle = "共 ${state.rows.size} 个批次 · 可恢复 ${state.restorableCount} · 已到期 ${state.expiredCount}",
                    )
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = "回收站为空",
                            description = "批次余额归零后进入回收站，保留期结束由系统自动清理。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                    } else {
                        state.rows.forEach { row ->
                            RecycleRowItem(
                                row = row,
                                enabled = !state.isWorking,
                                onRestore = { onRestore(row.batch.id) },
                            )
                            CulinaryRule()
                        }
                        Text(
                            text = "说明：回收站不提供手动清理。到期批次只会被标记为已报废，历史流水始终保留。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecycleRowItem(
    row: RecycleRow,
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    val batch = row.batch
    val restorable = row.daysLeft > 0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(
                text = if (restorable) "可恢复 · 剩 ${row.daysLeft} 天" else "已到期",
                tone = if (restorable) StatusTone.Warning else StatusTone.Neutral,
            )
        }
        Text(
            text = batch.archiveName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                "原始入库 ${formatAmount(batch.originalQuantity)} ${batch.unit.symbol} · " +
                    "归零时间 ${batch.recycledAt?.let(::formatInstant) ?: "未知"} · " +
                    "已回收 ${row.daysInRecycle} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = batch.locationName ?: "未指定库位",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (restorable) {
                CulinaryTextButton(text = "恢复", onClick = onRestore, enabled = enabled)
            } else {
                Text(
                    text = "已到期，等待系统清理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
