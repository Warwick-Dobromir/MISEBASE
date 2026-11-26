package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.rule.inventory.calculateDefaultExpiryDate
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 档案下的全部批次（表格）：批次、入库、生产、到期、剩余、库位、状态。 */
data class BatchListUiState(
    val archiveName: String = "",
    val unitSymbol: String = "",
    val batches: List<InventoryBatchRecord> = emptyList(),
    val expiryInfo: Map<String, BatchExpiryInfo> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BatchListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getArchiveDetail: GetInventoryArchiveDetailUseCase,
    ) : ViewModel() {
        private val archiveId: String =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID).orEmpty()

        private val _uiState = MutableStateFlow(BatchListUiState())
        val uiState: StateFlow<BatchListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.batches.isEmpty(), error = null) }
                if (archiveId.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "缺少档案标识，无法读取批次。") }
                    return@launch
                }
                when (val result = getArchiveDetail(archiveId)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                archiveName = result.value.archive.name,
                                unitSymbol = result.value.archive.stockUnit.symbol,
                                batches = result.value.batches,
                                expiryInfo = result.value.batches.associate { batch -> batch.id to calculateBatchExpiryInfo(batch, result.value.archive) },
                                isLoading = false,
                            )
                        }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }
    }

@Composable
fun BatchListRoute(
    onOpenBatch: (String) -> Unit,
    viewModel: BatchListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    BatchListScreen(
        state = state,
        onOpenBatch = onOpenBatch,
        onRetry = viewModel::load,
    )
}

@Composable
private fun BatchListScreen(
    state: BatchListUiState,
    onOpenBatch: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = "重试",
                onAction = onRetry,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.xs,
                    ),
            )
        }
        PageHeader(
            eyebrow = "BATCHES",
            title = state.archiveName.ifBlank { "批次记录" },
            supporting =
                if (state.batches.isEmpty()) {
                    "还没有批次"
                } else {
                    "共 ${state.batches.size} 个批次 · 单位 ${state.unitSymbol} · 按先进先出排列"
                },
        )
        when {
            state.isLoading -> LoadingRows()
            state.batches.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Inventory,
                    title = "还没有批次",
                    description = "该档案还没有入库记录。完成一次入库后，批次会按先进先出顺序显示在这里。",
                )

            else -> {
                BatchTableHeader()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.batches, key = { it.id }) { batch ->
                        BatchTableRow(batch = batch, expiryInfo = state.expiryInfo[batch.id] ?: BatchExpiryInfo.pending(), onClick = { onOpenBatch(batch.id) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchTableHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.sm,
                    bottom = CulinarySpacing.xs,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatchHeadCell(text = "批次", weight = 1.7f)
        BatchHeadCell(text = "入库", weight = 1f)
        BatchHeadCell(text = "生产", weight = 1f)
        BatchHeadCell(text = "到期", weight = 1f)
        BatchHeadCell(text = "剩余", weight = 0.9f)
        BatchHeadCell(text = "库位", weight = 1.1f)
        BatchHeadCell(text = "状态", weight = 1f)
    }
    CulinaryRule()
}

@Composable
private fun RowScope.BatchHeadCell(
    text: String,
    weight: Float,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(weight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BatchTableRow(
    batch: InventoryBatchRecord,
    expiryInfo: BatchExpiryInfo,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.sm,
                        bottom = CulinarySpacing.sm,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatchBodyCell(
                text = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(6),
                weight = 1.7f,
            )
            BatchBodyCell(text = shortBatchDate(batch.receivedAt.toLocalDate()), weight = 1f)
            BatchBodyCell(text = shortBatchDate(batch.productionDate), weight = 1f)
            BatchBodyCell(text = shortBatchDate(expiryInfo.expiryDate), weight = 1f)
            BatchBodyCell(text = formatAmount(batch.onHand), weight = 0.9f)
            BatchBodyCell(text = batch.locationName ?: "—", weight = 1.1f)
            BatchExpiryStatusCell(info = expiryInfo, weight = 1f)
        }
        CulinaryRule(modifier = Modifier.padding(start = CulinarySpacing.pageHorizontal))
    }
}

@Composable
private fun RowScope.BatchBodyCell(
    text: String,
    weight: Float,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.weight(weight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

data class BatchExpiryInfo(
    val expiryDate: LocalDate?,
    val remainingDays: Long?,
    val statusLabel: String,
    val status: ExpiryDisplayStatus,
) {
    companion object {
        fun pending() = BatchExpiryInfo(null, null, "待补充", ExpiryDisplayStatus.PENDING)
    }
}

enum class ExpiryDisplayStatus { NORMAL, ATTENTION, NEAR, EXPIRED, PENDING }

private fun calculateBatchExpiryInfo(batch: InventoryBatchRecord, archive: InventoryArchive): BatchExpiryInfo {
    val expiry = batch.expiresOn ?: run {
        calculateDefaultExpiryDate(
            productionDate = batch.productionDate,
            shelfLifeValue = archive.defaultShelfLifeValue ?: archive.shelfLifeDays,
            shelfLifeUnit = archive.defaultShelfLifeUnit,
        )
    }
    val production = batch.productionDate
    if (expiry == null || production == null) return BatchExpiryInfo(expiry, null, "待补充", ExpiryDisplayStatus.PENDING)
    val totalDays = java.time.temporal.ChronoUnit.DAYS.between(production, expiry)
    if (totalDays <= 0) return BatchExpiryInfo(expiry, 0, "过期", ExpiryDisplayStatus.EXPIRED)
    val remaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry)
    if (remaining <= 0) return BatchExpiryInfo(expiry, remaining, "过期", ExpiryDisplayStatus.EXPIRED)
    val ratio = remaining.toDouble() / totalDays.toDouble()
    return when {
        ratio > 0.5 -> BatchExpiryInfo(expiry, remaining, "正常", ExpiryDisplayStatus.NORMAL)
        ratio > 0.2 -> BatchExpiryInfo(expiry, remaining, "关注", ExpiryDisplayStatus.ATTENTION)
        else -> BatchExpiryInfo(expiry, remaining, "临期", ExpiryDisplayStatus.NEAR)
    }
}

@Composable
private fun RowScope.BatchExpiryStatusCell(info: BatchExpiryInfo, weight: Float) {
    val color = when (info.status) {
        ExpiryDisplayStatus.NORMAL -> CulinaryColorScheme.current.success
        ExpiryDisplayStatus.ATTENTION -> CulinaryColorScheme.current.warning
        ExpiryDisplayStatus.NEAR, ExpiryDisplayStatus.EXPIRED -> CulinaryColorScheme.current.danger
        ExpiryDisplayStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.weight(weight)) {
        Text(text = info.statusLabel, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = info.remainingDays?.let { if (it < 0) "超期 ${-it} 天" else "剩余 ${it} 天" } ?: "日期不全",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val batchDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

private fun shortBatchDate(value: LocalDate?): String =
    value?.format(batchDateFormatter) ?: "—"

private fun java.time.Instant.toLocalDate(): LocalDate =
    atZone(java.time.ZoneId.systemDefault()).toLocalDate()
