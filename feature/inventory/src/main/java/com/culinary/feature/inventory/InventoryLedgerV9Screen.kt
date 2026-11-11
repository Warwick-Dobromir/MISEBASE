package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryInfoRow
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.usecase.inventory.InventoryLedgerUseCases
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.PrintJobUseCases
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

/** 库存流水（v9）：紧凑行 + 类型筛选 + 行内展开详情与重打。 */
@Composable
fun LedgerV9Route(
    onBack: () -> Unit,
    viewModel: LedgerV9ViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LedgerV9Screen(
        state = state,
        onBack = onBack,
        onFilterChange = viewModel::selectFilter,
        onToggleRow = viewModel::toggleRow,
        onReprint = viewModel::reprint,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 流水类型筛选（页面只暴露这一组分类，内部映射到具体 LedgerEntryType）。 */
enum class LedgerFilter(val label: String) {
    ALL("全部"),
    RECEIPT("入库"),
    ISSUE("出库"),
    ADJUSTMENT("盘点"),
    FREEZE("冻结"),
    TRANSFER("转移"),
    WASTE("损耗"),
}

/** 一行流水 + 名称补全 + 关联打印任务。 */
data class LedgerRowView(
    val entry: InventoryLedgerEntry,
    val archiveName: String,
    val locationName: String,
    val printJob: PrintJobRecord? = null,
) {
    val before: Double get() = entry.onHandAfter - entry.onHandDelta
    val after: Double get() = entry.onHandAfter

    /** 出库/入库流水才展示打印状态；其他流水没有可打印单据。 */
    val isPrintable: Boolean
        get() {
            val type = entry.referenceType ?: return false
            return type in PRINTABLE_REF_TYPES && !entry.referenceId.isNullOrBlank()
        }

    companion object {
        /** 打印任务按业务引用关联：出库单 → 单据 ID；入库单 → 批次 ID。 */
        val PRINTABLE_REF_TYPES = setOf("issue_slip", "stock_receipt")
    }
}

/** 流水页状态；展开状态只保留一行，避免长列表记住过多高度。 */
data class LedgerV9UiState(
    val rows: List<LedgerRowView> = emptyList(),
    val filter: LedgerFilter = LedgerFilter.ALL,
    val expandedId: String? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val visibleRows: List<LedgerRowView>
        get() = rows.filter { matches(it.entry.type, filter) }

    private fun matches(
        type: LedgerEntryType,
        filter: LedgerFilter,
    ): Boolean =
        when (filter) {
            LedgerFilter.ALL -> true
            LedgerFilter.RECEIPT -> type == LedgerEntryType.PURCHASE_RECEIPT
            LedgerFilter.ISSUE -> type == LedgerEntryType.PRODUCTION_ISSUE
            LedgerFilter.ADJUSTMENT -> type == LedgerEntryType.ADJUSTMENT_IN || type == LedgerEntryType.ADJUSTMENT_OUT
            LedgerFilter.FREEZE -> type == LedgerEntryType.FREEZE || type == LedgerEntryType.UNFREEZE
            LedgerFilter.TRANSFER ->
                type == LedgerEntryType.LOCATION_TRANSFER ||
                    type == LedgerEntryType.TRANSFER_IN ||
                    type == LedgerEntryType.TRANSFER_OUT
            LedgerFilter.WASTE -> type == LedgerEntryType.WASTE
        }
}

/** 流水 ViewModel：只读列表，重打只更新打印任务状态，不回滚库存。 */
@HiltViewModel
class LedgerV9ViewModel
    @Inject
    constructor(
        private val ledger: InventoryLedgerUseCases,
        private val listArchives: ListInventoryArchivesUseCase,
        private val listLocations: ListStorageLocationsUseCase,
        private val printJobs: PrintJobUseCases,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LedgerV9UiState())
        val uiState: StateFlow<LedgerV9UiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val entries =
                    when (val result = ledger.recent(200)) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value
                    }
                val archiveNames =
                    when (val result = listArchives()) {
                        is Outcome.Failure -> emptyMap()
                        is Outcome.Success -> result.value.rows.associate { it.archive.id to it.archive.name }
                    }
                val locationNames =
                    when (val result = listLocations()) {
                        is Outcome.Failure -> emptyMap()
                        is Outcome.Success -> result.value.associate { it.id.value to it.name }
                    }
                // 流水写入时打印任务还不存在，因此 printJobId 恒为 null（流水 append-only，不能回填）。
                // 这里按业务引用反查：出库流水 issue_slip/单据 ID，入库流水 stock_receipt/批次 ID。
                val jobsByReference = mutableMapOf<Pair<String, String>, PrintJobRecord?>()
                entries.forEach { entry ->
                    val refType = entry.referenceType
                    val refId = entry.referenceId
                    if (refType != null && refType in LedgerRowView.PRINTABLE_REF_TYPES && !refId.isNullOrBlank()) {
                        val key = refType to refId
                        if (!jobsByReference.containsKey(key)) {
                            jobsByReference[key] =
                                when (val result = printJobs.latest(refType, refId)) {
                                    is Outcome.Failure -> null
                                    is Outcome.Success -> result.value
                                }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        rows =
                            entries.map { entry ->
                                LedgerRowView(
                                    entry = entry,
                                    archiveName =
                                        entry.archiveId?.let(archiveNames::get)
                                            ?: entry.productId?.value
                                            ?: "未命名档案",
                                    locationName = locationNames[entry.locationId.value] ?: "未命名库位",
                                    printJob =
                                        entry.referenceType
                                            ?.takeIf { type -> type in LedgerRowView.PRINTABLE_REF_TYPES }
                                            ?.let { type -> entry.referenceId?.let { id -> jobsByReference[type to id] } },
                                )
                            },
                    )
                }
            }
        }

        fun selectFilter(filter: LedgerFilter) {
            _uiState.update { it.copy(filter = filter, expandedId = null) }
        }

        fun toggleRow(entryId: String) {
            _uiState.update { it.copy(expandedId = if (it.expandedId == entryId) null else entryId) }
        }

        fun reprint(jobId: String) {
            if (_uiState.value.isWorking) return
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null) }
                when (val result = printJobs.reprint(jobId)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update { state ->
                            state.copy(
                                isWorking = false,
                                message = "已重新加入打印队列，库存余额不变。",
                                rows =
                                    state.rows.map { row ->
                                        if (row.printJob?.id == jobId) row.copy(printJob = result.value) else row
                                    },
                            )
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
internal fun LedgerV9Screen(
    state: LedgerV9UiState,
    onBack: () -> Unit,
    onFilterChange: (LedgerFilter) -> Unit,
    onToggleRow: (String) -> Unit,
    onReprint: (String) -> Unit,
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
                    eyebrow = "LEDGER",
                    title = "库存流水",
                    supporting = "流水只追加不修改；重打只影响打印任务状态，不改变库存。",
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
                    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xxs)) {
                        LedgerFilter.entries.forEach { filter ->
                            CulinaryFilterPill(
                                text = filter.label,
                                selected = state.filter == filter,
                                onClick = { onFilterChange(filter) },
                            )
                        }
                    }
                    SectionHeader(
                        title = "最近流水",
                        subtitle = "显示 ${state.visibleRows.size} 条 · 点击一行展开详情",
                        action = { CulinaryTextButton(text = "刷新", onClick = onReload) },
                    )
                    if (state.visibleRows.isEmpty()) {
                        EmptyState(
                            title = "没有符合筛选的流水",
                            description = "换一个类型筛选，或先完成入库/出库/盘点等操作。",
                            compact = true,
                        )
                    } else {
                        state.visibleRows.forEach { row ->
                            LedgerCompactRow(
                                row = row,
                                expanded = state.expandedId == row.entry.id.value,
                                isWorking = state.isWorking,
                                onClick = { onToggleRow(row.entry.id.value) },
                                onReprint = onReprint,
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
private fun LedgerCompactRow(
    row: LedgerRowView,
    expanded: Boolean,
    isWorking: Boolean,
    onClick: () -> Unit,
    onReprint: (String) -> Unit,
) {
    val entry = row.entry
    val deltaColor =
        when {
            entry.onHandDelta > 0.0 -> CulinaryColorScheme.current.success
            entry.onHandDelta < 0.0 -> CulinaryColorScheme.current.danger
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = CulinarySpacing.sm)) {
        Text(
            text = "${InventoryText.ledgerType(entry.type)} · ${formatInstant(entry.occurredAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.archiveName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = deltaText(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor,
            )
            Text(
                text = entry.stockUnit.symbol,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xxs),
            )
        }
        Text(
            text = "${formatAmount(row.before)} → ${formatAmount(row.after)} ${entry.stockUnit.symbol}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            LedgerDetail(row = row, isWorking = isWorking, onReprint = onReprint)
        }
        if (row.printJob != null || row.isPrintable) {
            StatusBadge(
                text = "打印：${row.printJob?.let { InventoryText.printStatus(it.status) } ?: "无打印任务"}",
                tone =
                    when (row.printJob?.status) {
                        PrintStatus.PRINTED -> StatusTone.Success
                        PrintStatus.FAILED -> StatusTone.Danger
                        PrintStatus.PENDING -> StatusTone.Warning
                        null -> StatusTone.Neutral
                    },
                modifier = Modifier.padding(top = CulinarySpacing.xxs),
            )
        }
        if (entry.onHandDelta == 0.0) {
            Text(
                text = "零数量变动（冻结 / 解冻 / 库位转移）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LedgerDetail(
    row: LedgerRowView,
    isWorking: Boolean,
    onReprint: (String) -> Unit,
) {
    val entry = row.entry
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = CulinarySpacing.xs),
    ) {
        CulinaryRule()
        CulinaryInfoRow(label = "操作类型", value = InventoryText.ledgerType(entry.type))
        CulinaryInfoRow(label = "时间", value = detailTime(entry))
        CulinaryInfoRow(label = "档案", value = row.archiveName)
        CulinaryInfoRow(label = "批次 ID", value = entry.batchId.value)
        CulinaryInfoRow(label = "库位", value = row.locationName)
        CulinaryInfoRow(
            label = "前后余额",
            value = "${formatAmount(row.before)} → ${formatAmount(row.after)} ${entry.stockUnit.symbol}",
        )
        CulinaryInfoRow(
            label = "来源单号",
            value = listOfNotNull(entry.referenceType, entry.referenceId).joinToString(" / ").ifBlank { "—" },
        )
        val job = row.printJob
        CulinaryInfoRow(
            label = "打印状态",
            value = job?.let { InventoryText.printStatus(it.status) } ?: "无打印任务",
        )
        if (job?.status == PrintStatus.FAILED) {
            StatusBadge(
                text = "打印失败",
                tone = StatusTone.Danger,
                modifier = Modifier.padding(bottom = CulinarySpacing.xxs),
            )
        }
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            CulinaryInfoRow(label = "备注", value = note)
        }
        if (job != null) {
            CulinaryTextButton(
                text = "重新打印",
                onClick = { onReprint(job.id) },
                enabled = !isWorking,
                danger = job.status == PrintStatus.FAILED,
            )
        }
        CulinaryRule()
    }
}

private fun deltaText(entry: InventoryLedgerEntry): String =
    when {
        entry.onHandDelta > 0.0 -> "+${formatAmount(entry.onHandDelta)}"
        entry.onHandDelta < 0.0 -> formatAmount(entry.onHandDelta)
        else -> "0"
    }

private fun detailTime(entry: InventoryLedgerEntry): String =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(entry.occurredAt)
