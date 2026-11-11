package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.usecase.inventory.ListFrozenBatchesUseCase
import com.culinary.core.domain.usecase.inventory.UnfreezeBatchUseCase
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

/** 冻结批次管理：只列当前冻结批次，解冻必须填写原因。 */
@Composable
fun FreezeRoute(
    onBack: () -> Unit,
    onOpenBatch: (String) -> Unit,
    viewModel: FreezeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FreezeScreen(
        state = state,
        onBack = onBack,
        onOpenBatch = onOpenBatch,
        onRequestUnfreeze = viewModel::requestUnfreeze,
        onReasonChange = viewModel::onReasonChange,
        onConfirmUnfreeze = viewModel::confirmUnfreeze,
        onCancelUnfreeze = viewModel::cancelUnfreeze,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 冻结列表状态；解冻原因随行弹窗输入。 */
data class FreezeUiState(
    val batches: List<InventoryBatchRecord> = emptyList(),
    val pendingBatchId: String? = null,
    val pendingBatchLabel: String? = null,
    val reason: String = "",
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/** 冻结 ViewModel：列表只读，解冻走 UnfreezeBatchUseCase。 */
@HiltViewModel
class FreezeViewModel
    @Inject
    constructor(
        private val listFrozen: ListFrozenBatchesUseCase,
        private val unfreeze: UnfreezeBatchUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FreezeUiState())
        val uiState: StateFlow<FreezeUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = listFrozen()) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update { it.copy(batches = result.value, isLoading = false) }
                }
            }
        }

        fun requestUnfreeze(batchId: String) {
            val batch = _uiState.value.batches.firstOrNull { it.id == batchId } ?: return
            _uiState.update {
                it.copy(
                    pendingBatchId = batchId,
                    pendingBatchLabel = "${batch.archiveName} · ${batch.batchCode ?: batch.id.take(8)}",
                    reason = "",
                    error = null,
                )
            }
        }

        fun onReasonChange(value: String) {
            _uiState.update { it.copy(reason = value, error = null) }
        }

        fun cancelUnfreeze() {
            _uiState.update { it.copy(pendingBatchId = null, pendingBatchLabel = null, reason = "") }
        }

        fun confirmUnfreeze() {
            val state = _uiState.value
            val batchId = state.pendingBatchId ?: return
            if (state.reason.isBlank()) {
                _uiState.update { it.copy(error = "解冻必须填写原因。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null) }
                when (val result = unfreeze(batchId, state.reason.trim())) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                pendingBatchId = null,
                                pendingBatchLabel = null,
                                reason = "",
                                message = "批次已解冻，冻结与解冻记录均已留痕。",
                            )
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
internal fun FreezeScreen(
    state: FreezeUiState,
    onBack: () -> Unit,
    onOpenBatch: (String) -> Unit,
    onRequestUnfreeze: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onConfirmUnfreeze: () -> Unit,
    onCancelUnfreeze: () -> Unit,
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
                    eyebrow = "FREEZE",
                    title = "冻结批次",
                    supporting = "冻结批次不参与出库，但可盘点；解冻必须填写原因并留痕。",
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
                    SectionHeader(title = "冻结列表", subtitle = "共 ${state.batches.size} 个批次")
                    if (state.batches.isEmpty()) {
                        EmptyState(
                            title = "没有冻结批次",
                            description = "在批次详情中冻结批次后，会出现在这里；解冻同样需要填写原因。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                    } else {
                        state.batches.forEach { batch ->
                            FrozenBatchRow(
                                batch = batch,
                                enabled = !state.isWorking,
                                onOpen = { onOpenBatch(batch.id) },
                                onUnfreeze = { onRequestUnfreeze(batch.id) },
                            )
                            CulinaryRule()
                        }
                    }
                }
            }
        }
    }

    val pendingBatchId = state.pendingBatchId
    if (pendingBatchId != null) {
        AlertDialog(
            onDismissRequest = onCancelUnfreeze,
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = "解冻批次", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        text = state.pendingBatchLabel ?: "该批次",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "解冻后批次重新参与 FIFO 出库。原因会写入冻结记录与库存流水。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CulinarySpacing.xs),
                    )
                    CulinaryTextField(
                        value = state.reason,
                        onValueChange = onReasonChange,
                        label = "解冻原因 *",
                        singleLine = false,
                        modifier = Modifier.padding(top = CulinarySpacing.md),
                    )
                    if (state.reason.isBlank()) {
                        Text(
                            text = "未填写原因时无法解冻。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                }
            },
            confirmButton = {
                CulinaryButton(
                    text = if (state.isWorking) "正在解冻" else "确认解冻",
                    onClick = onConfirmUnfreeze,
                    enabled = !state.isWorking && state.reason.isNotBlank(),
                    loading = state.isWorking,
                )
            },
            dismissButton = {
                TextButton(onClick = onCancelUnfreeze, enabled = !state.isWorking) {
                    Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun FrozenBatchRow(
    batch: InventoryBatchRecord,
    enabled: Boolean,
    onOpen: () -> Unit,
    onUnfreeze: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = batch.archiveName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    StatusBadge(
                        text = "冻结",
                        tone = StatusTone.Warning,
                        modifier = Modifier.padding(start = CulinarySpacing.xs),
                    )
                }
                Text(
                    text =
                        buildString {
                            append(batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8))
                            append(" · ")
                            append(batch.locationName ?: "未指定库位")
                            append(" · ")
                            append(formatAmount(batch.available))
                            append(" ")
                            append(batch.unit.symbol)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "原因：${batch.frozenReason.orEmpty().ifBlank { "未填写" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "冻结时间：${batch.frozenAt?.let(::formatInstant) ?: "未知"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CulinaryTextButton(text = "解冻", onClick = onUnfreeze, enabled = enabled)
        }
    }
}
