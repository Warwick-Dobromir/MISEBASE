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
import com.culinary.core.domain.model.TemporaryStatus
import com.culinary.core.domain.model.TemporaryStockRow
import com.culinary.core.domain.usecase.inventory.TemporaryStockUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/** 临时库存：15 天提醒、20 天待处理，可转为正式库存。 */
@Composable
fun TemporaryRoute(
    onBack: () -> Unit,
    viewModel: TemporaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TemporaryScreen(
        state = state,
        onBack = onBack,
        onMarkFormal = viewModel::markFormal,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 临时库存状态；天数与状态由 UseCase 按规则计算，页面不硬编码阈值。 */
data class TemporaryUiState(
    val rows: List<TemporaryStockRow> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val remindCount: Int
        get() = rows.count { it.status == TemporaryStatus.REMIND }

    val pendingCount: Int
        get() = rows.count { it.status == TemporaryStatus.PENDING }
}

/** 临时库存 ViewModel。 */
@HiltViewModel
class TemporaryViewModel
    @Inject
    constructor(
        private val temporaryStock: TemporaryStockUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TemporaryUiState())
        val uiState: StateFlow<TemporaryUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = temporaryStock.list()) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update { it.copy(rows = result.value, isLoading = false) }
                }
            }
        }

        fun markFormal(batchId: String) {
            if (_uiState.value.isWorking) return
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = temporaryStock.markFormal(batchId)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        _uiState.update {
                            it.copy(isWorking = false, message = "已转为正式库存。")
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
internal fun TemporaryScreen(
    state: TemporaryUiState,
    onBack: () -> Unit,
    onMarkFormal: (String) -> Unit,
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
                    eyebrow = "TEMPORARY",
                    title = "临时库存",
                    supporting = "提醒与待处理天数来自库存规则；转为正式库存后不再按临时库存提醒。",
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
                        title = "临时库存列表",
                        subtitle = "共 ${state.rows.size} 项 · 需处理 ${state.remindCount} · 待处理 ${state.pendingCount}",
                    )
                    if (state.rows.isEmpty()) {
                        EmptyState(
                            title = "没有临时库存",
                            description = "入库时开启「临时库存」的批次会在这里按天数提醒。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                    } else {
                        state.rows.forEach { row ->
                            TemporaryRow(
                                row = row,
                                enabled = !state.isWorking,
                                onMarkFormal = { onMarkFormal(row.batch.id) },
                            )
                            CulinaryRule()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemporaryRow(
    row: TemporaryStockRow,
    enabled: Boolean,
    onMarkFormal: () -> Unit,
) {
    val batch = row.batch
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = batch.archiveName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(
                text = InventoryText.temporaryStatus(row.status),
                tone = temporaryTone(row.status),
            )
        }
        Text(
            text =
                "${formatAmount(batch.available)} ${batch.unit.symbol} · " +
                    "创建 ${temporaryDate(batch.temporarySince ?: batch.receivedAt)} · " +
                    "已存在 ${row.days} 天",
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
            CulinaryTextButton(
                text = "转为正式库存",
                onClick = onMarkFormal,
                enabled = enabled,
            )
        }
    }
}

private fun temporaryTone(status: TemporaryStatus): StatusTone =
    when (status) {
        TemporaryStatus.NORMAL -> StatusTone.Success
        TemporaryStatus.REMIND -> StatusTone.Warning
        TemporaryStatus.PENDING -> StatusTone.Danger
    }

private fun temporaryDate(value: java.time.Instant): String =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
        .format(value)
