package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.repository.InventoryArchiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BatchHistoryUiState(
    val batches: List<InventoryBatchRecord> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BatchHistoryViewModel
    @Inject
    constructor(
        private val repository: InventoryArchiveRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BatchHistoryUiState())
        val uiState: StateFlow<BatchHistoryUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.batches.isEmpty(), error = null) }
                when (val result = repository.listAllBatchHistory()) {
                    is Outcome.Success -> _uiState.update { it.copy(batches = result.value, isLoading = false) }
                    is Outcome.Failure -> _uiState.update {
                        it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                    }
                }
            }
        }
    }

@Composable
fun BatchHistoryRoute(
    onOpenBatch: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BatchHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BatchHistoryScreen(state = state, onOpenBatch = onOpenBatch, onRetry = viewModel::load)
}

@Composable
private fun BatchHistoryScreen(
    state: BatchHistoryUiState,
    onOpenBatch: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(message = message, actionLabel = "重试", onAction = onRetry)
        }
        PageHeader(
            eyebrow = "BATCH HISTORY",
            title = "批次记录",
            supporting = "永久保留所有批次，包括数量归零及已处置批次",
        )
        when {
            state.isLoading -> LoadingRows()
            state.batches.isEmpty() -> EmptyState(
                title = "暂无批次记录",
                description = "完成首次入库后，批次及其后续状态变化会持续保留在这里。",
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.batches, key = { it.id }) { batch ->
                    BatchHistoryRow(batch = batch, onClick = { onOpenBatch(batch.id) })
                }
                item { Spacer(modifier = Modifier.padding(bottom = CulinarySpacing.xl)) }
            }
        }
    }
}

@Composable
private fun BatchHistoryRow(
    batch: InventoryBatchRecord,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = batch.archiveName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "入库 " + batch.receivedAt.atZone(ZoneId.systemDefault()).format(HISTORY_DATE) + " · " + (batch.locationName ?: "未记录库位"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(batch.onHand) + " " + batch.unit.symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = historyStatus(batch.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CulinaryRule(modifier = Modifier.padding(start = CulinarySpacing.pageHorizontal))
    }
}

private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun historyStatus(status: BatchLifecycleStatus): String =
    when (status) {
        BatchLifecycleStatus.ACTIVE -> "在库"
        BatchLifecycleStatus.FROZEN -> "冻结"
        BatchLifecycleStatus.RECYCLED -> "已归档"
        BatchLifecycleStatus.DEPLETED -> "已耗尽"
        BatchLifecycleStatus.EXPIRED -> "已过期"
        BatchLifecycleStatus.DISCARDED -> "已处置"
    }
