package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryInfoRow
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.InlineError
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.FreezeAction
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.usecase.inventory.BatchFreezeHistoryUseCase
import com.culinary.core.domain.usecase.inventory.FreezeBatchUseCase
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.InventoryLedgerUseCases
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.TransferBatchLocationUseCase
import com.culinary.core.domain.usecase.inventory.UnfreezeBatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 批次详情：数量、信息、冻结/解冻/转移、冻结历史与完整流水。 */
data class BatchDetailUiState(
    val batch: InventoryBatchRecord? = null,
    val archiveName: String = "",
    val ledger: List<InventoryLedgerEntry> = emptyList(),
    val freezeRecords: List<FreezeRecord> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class BatchDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getArchiveDetail: GetInventoryArchiveDetailUseCase,
        private val ledgerUseCases: InventoryLedgerUseCases,
        private val freezeHistory: BatchFreezeHistoryUseCase,
        private val freezeBatch: FreezeBatchUseCase,
        private val unfreezeBatch: UnfreezeBatchUseCase,
        private val transferBatch: TransferBatchLocationUseCase,
        private val listLocations: ListStorageLocationsUseCase,
    ) : ViewModel() {
        private val batchId: String =
            savedStateHandle.get<String>(InventoryRoutes.ARG_BATCH_ID).orEmpty()

        private val _uiState = MutableStateFlow(BatchDetailUiState())
        val uiState: StateFlow<BatchDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.batch == null, error = null) }
                if (batchId.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "缺少批次标识，无法打开批次详情。") }
                    return@launch
                }
                when (val result = read()) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                batch = result.value.batch,
                                archiveName = result.value.archiveName,
                                ledger = result.value.ledger,
                                freezeRecords = result.value.freezeRecords,
                                locations = result.value.locations,
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

        private suspend fun read(): Outcome<BatchDetailUiState> {
            val ledger =
                when (val result = ledgerUseCases.forBatch(batchId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value.sortedByDescending { it.occurredAt }
                }
            val archiveId =
                ledger.firstNotNullOfOrNull { it.archiveId }
                    ?: return Outcome.failure(CulinaryError.NotFound("inventory_archive", batchId))
            val detail =
                when (val result = getArchiveDetail(archiveId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val batch =
                detail.batches.firstOrNull { it.id == batchId }
                    ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", batchId))
            val freezeRecords =
                when (val result = freezeHistory(batchId)) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success -> result.value
                }
            val locations =
                when (val result = listLocations()) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success -> result.value
                }
            return Outcome.success(
                BatchDetailUiState(
                    batch = batch,
                    archiveName = detail.archive.name,
                    ledger = ledger,
                    freezeRecords = freezeRecords.sortedByDescending { it.occurredAt },
                    locations = locations,
                    isLoading = false,
                ),
            )
        }

        fun freeze(reason: String) = runAction("已冻结该批次。") { freezeBatch(batchId, reason) }

        fun unfreeze(reason: String) = runAction("已解冻该批次。") { unfreezeBatch(batchId, reason) }

        fun transfer(
            toLocationId: String,
            note: String?,
        ) = runAction("已更新批次库位。") {
            transferBatch(batchId, toLocationId, note?.trim()?.takeIf { it.isNotEmpty() })
        }

        private fun runAction(
            successMessage: String,
            block: suspend () -> Outcome<Unit>,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = block()) {
                    is Outcome.Success -> {
                        _uiState.update { it.copy(isWorking = false, message = successMessage) }
                        load()
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = batchErrorText(result.error))
                        }
                }
            }
        }
    }

/** 批次写入失败原因：冻结原因、库位相同等都必须给出文字说明。 */
private fun batchErrorText(error: CulinaryError): String =
    when (error) {
        is CulinaryError.Validation ->
            when {
                error.field == "reason" -> "请填写原因后再提交。"
                error.rule.contains("same") -> "目标库位与当前库位相同，请选择其他库位。"
                error.rule.contains("recycled") -> "回收站中的批次不能冻结。"
                error.rule.contains("not frozen") -> "该批次当前不是冻结状态，无法解冻。"
                else -> "操作未通过校验，请检查输入。"
            }

        is CulinaryError.NotFound -> "批次不存在，可能已被处理。"
        else -> InventoryErrorMessages.describe(error)
    }

@Composable
fun BatchDetailRoute(
    onTransfer: (String) -> Unit,
    onPrintLabel: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BatchDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    BatchDetailScreen(
        state = state,
        onBack = onBack,
        onTransfer = onTransfer,
        onPrintLabel = onPrintLabel,
        onFreeze = viewModel::freeze,
        onUnfreeze = viewModel::unfreeze,
        onTransferConfirm = viewModel::transfer,
        onRetry = viewModel::load,
    )
}

@Suppress("LongMethod", "UNUSED_PARAMETER") // onTransfer 保留给外壳的独立转移入口，本页直接调用转移用例。
@Composable
private fun BatchDetailScreen(
    state: BatchDetailUiState,
    onBack: () -> Unit,
    onTransfer: (String) -> Unit,
    onPrintLabel: (String) -> Unit,
    onFreeze: (String) -> Unit,
    onUnfreeze: (String) -> Unit,
    onTransferConfirm: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    var reasonDialog by remember { mutableStateOf<FreezeDialogMode?>(null) }
    var transferDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        BatchBackBar(onBack = onBack)
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
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.xs,
                    ),
            )
        }
        val batch = state.batch
        when {
            state.isLoading -> LoadingRows()
            batch == null ->
                EmptyState(
                    title = "批次不存在",
                    description = "该批次可能已被回收或删除，请返回档案查看其他批次。",
                )

            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = CulinarySpacing.xl),
                ) {
                    PageHeader(
                        eyebrow = "BATCH",
                        title = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8),
                        supporting = state.archiveName,
                        actions = {
                            StatusBadge(
                                text = InventoryText.batchStatus(batch.status),
                                tone = InventoryText.batchTone(batch.status),
                            )
                        },
                    )
                    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                        ArchiveModuleHeader(number = "01", english = "QUANTITY", chinese = "数量")
                        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                            BatchMetric(
                                label = "原始入库",
                                amount = batch.originalQuantity,
                                unit = batch.unit.symbol,
                                modifier = Modifier.weight(1f),
                            )
                            BatchMetric(
                                label = "当前剩余",
                                amount = batch.onHand,
                                unit = batch.unit.symbol,
                                modifier = Modifier.weight(1f),
                            )
                            BatchMetric(
                                label = "累计出库",
                                amount = batch.outboundQuantity,
                                unit = batch.unit.symbol,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ArchiveModuleHeader(number = "02", english = "DETAIL", chinese = "批次信息")
                        CulinaryInfoRow(label = "入库时间", value = formatInstant(batch.receivedAt))
                        CulinaryInfoRow(
                            label = "生产日期",
                            value = batch.productionDate?.let(::formatDate) ?: "未登记",
                        )
                        CulinaryInfoRow(
                            label = "到期日期",
                            value = batch.expiresOn?.let(::formatDate) ?: "未登记",
                        )
                        CulinaryInfoRow(label = "当前库位", value = batch.locationName ?: "未指定")
                        CulinaryInfoRow(label = "来源商品", value = batch.sourceProductName ?: "未关联商品")
                        CulinaryInfoRow(
                            label = "状态",
                            value = InventoryText.batchStatus(batch.status),
                        )
                        if (batch.isFrozen) {
                            CulinaryInfoRow(
                                label = "冻结原因",
                                value = batch.frozenReason?.takeIf { it.isNotBlank() } ?: "未填写",
                            )
                            Text(
                                text = "冻结期间该批次不参与出库，但仍可盘点。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = CulinarySpacing.xxs),
                            )
                        }
                        ArchiveModuleHeader(number = "03", english = "ACTIONS", chinese = "操作")
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
                    ) {
                        if (batch.isFrozen) {
                            CulinaryTextButton(
                                text = "解冻",
                                onClick = { reasonDialog = FreezeDialogMode.Unfreeze },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        } else {
                            CulinaryTextButton(
                                text = "冻结",
                                onClick = { reasonDialog = FreezeDialogMode.Freeze },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            )
                        }
                        CulinaryTextButton(
                            text = "库位转移",
                            onClick = { transferDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        )
                        CulinaryTextButton(
                            text = "打印批次标签",
                            onClick = { onPrintLabel(batch.id) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        )
                    }
                    SectionHeader(
                        title = "冻结与解冻历史",
                        subtitle = "每次冻结/解冻都必须留下原因",
                        modifier =
                            Modifier
                                .padding(horizontal = CulinarySpacing.pageHorizontal)
                                .padding(top = CulinarySpacing.block),
                    )
                    if (state.freezeRecords.isEmpty()) {
                        Text(
                            text = "暂无冻结记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                            state.freezeRecords.forEach { record ->
                                FreezeHistoryRow(record = record)
                            }
                        }
                    }
                    SectionHeader(
                        title = "完整流水",
                        subtitle = "共 ${state.ledger.size} 条",
                        modifier =
                            Modifier
                                .padding(horizontal = CulinarySpacing.pageHorizontal)
                                .padding(top = CulinarySpacing.block),
                    )
                    if (state.ledger.isEmpty()) {
                        Text(
                            text = "暂无流水记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                            state.ledger.forEach { entry ->
                                BatchLedgerRow(entry = entry)
                            }
                        }
                    }
                }
        }
    }

    when (val mode = reasonDialog) {
        null -> Unit
        is FreezeDialogMode ->
            BatchReasonDialog(
                title = if (mode == FreezeDialogMode.Freeze) "冻结批次" else "解冻批次",
                confirmLabel = if (mode == FreezeDialogMode.Freeze) "冻结" else "解冻",
                isWorking = state.isWorking,
                onDismiss = { reasonDialog = null },
                onConfirm = { reason ->
                    if (mode == FreezeDialogMode.Freeze) onFreeze(reason) else onUnfreeze(reason)
                    reasonDialog = null
                },
            )
    }

    if (transferDialog) {
        val current = state.batch
        if (current != null) {
            BatchTransferDialog(
                locations = state.locations,
                currentLocationId = current.locationId.value,
                isWorking = state.isWorking,
                onDismiss = { transferDialog = false },
                onConfirm = { locationId, note ->
                    onTransferConfirm(locationId, note)
                    transferDialog = false
                },
            )
        }
    }
}

private enum class FreezeDialogMode {
    Freeze,
    Unfreeze,
}

@Composable
private fun BatchBackBar(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CulinarySpacing.xxl)
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onBack)
                        .defaultMinSize(minHeight = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "BATCH DETAIL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryRule()
    }
}

@Composable
private fun BatchMetric(
    label: String,
    amount: Double,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        QuantityText(
            amount = formatAmount(amount),
            unit = unit,
            amountStyle = CulinaryTextStyles.numeralM,
        )
    }
}

@Composable
private fun FreezeHistoryRow(record: FreezeRecord) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = if (record.action == FreezeAction.FREEZE) "冻结" else "解冻",
                tone = if (record.action == FreezeAction.FREEZE) StatusTone.Warning else StatusTone.Success,
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Text(
                text = formatInstant(record.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            record.operator?.takeIf { it.isNotBlank() }?.let { operator ->
                Text(
                    text = operator,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = record.reason.ifBlank { "未填写原因" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    CulinaryRule()
}

@Composable
private fun BatchLedgerRow(entry: InventoryLedgerEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = InventoryText.ledgerType(entry.type),
                tone = if (entry.onHandDelta < 0.0) StatusTone.Neutral else StatusTone.Success,
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Text(
                text = formatInstant(entry.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = batchDeltaText(entry),
                style = CulinaryTextStyles.numeralS,
                color = batchDeltaColor(entry),
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
            Text(
                text = entry.stockUnit.symbol,
                style = CulinaryTextStyles.unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "结存 ${formatAmount(entry.onHandAfter)} ${entry.stockUnit.symbol}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    CulinaryRule()
}

private fun batchDeltaText(entry: InventoryLedgerEntry): String =
    when {
        entry.onHandDelta > 0.0 -> "+${formatAmount(entry.onHandDelta)}"
        entry.onHandDelta < 0.0 -> formatAmount(entry.onHandDelta)
        else -> "0"
    }

@Composable
private fun batchDeltaColor(entry: InventoryLedgerEntry) =
    when {
        entry.onHandDelta > 0.0 -> CulinaryColorScheme.current.success
        entry.onHandDelta < 0.0 -> CulinaryColorScheme.current.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

/** 冻结/解冻共用原因弹窗：原因为空时不提交，并在弹窗内给出行内提示。 */
@Composable
private fun BatchReasonDialog(
    title: String,
    confirmLabel: String,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CulinaryTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        showError = false
                    },
                    label = "原因 *",
                    minLines = 2,
                    isError = showError,
                    supportingText = if (showError) "请填写原因。" else "原因会写入冻结历史与库存流水",
                )
            }
        },
        confirmButton = {
            CulinaryButton(
                text = confirmLabel,
                onClick = {
                    if (reason.isBlank()) showError = true else onConfirm(reason.trim())
                },
                loading = isWorking,
            )
        },
        dismissButton = {
            CulinaryTextButton(text = "取消", onClick = onDismiss)
        },
    )
}

/** 库位转移弹窗：先选目标库位（当前库位置灰不可选），再填备注。 */
@Composable
private fun BatchTransferDialog(
    locations: List<StorageLocation>,
    currentLocationId: String,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "库位转移",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (locations.isEmpty()) {
                    Text(
                        text = "还没有可用库位。请先在「库位管理」中建立库位。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        locations.forEach { location ->
                            val isCurrent = location.id.value == currentLocationId
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = !isCurrent,
                                            role = Role.Button,
                                            onClick = {
                                                selectedId = location.id.value
                                                showError = false
                                            },
                                        ).defaultMinSize(minHeight = 48.dp)
                                        .padding(vertical = CulinarySpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = location.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                        when {
                                            isCurrent -> MaterialTheme.colorScheme.onSurfaceVariant
                                            selectedId == location.id.value ->
                                                MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text =
                                        when {
                                            isCurrent -> "当前库位"
                                            selectedId == location.id.value -> "已选"
                                            else -> ""
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                    CulinaryTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = "备注",
                        singleLine = true,
                        supportingText = "可留空",
                    )
                    if (showError) {
                        InlineError(message = "请选择目标库位。")
                    }
                }
            }
        },
        confirmButton = {
            CulinaryButton(
                text = "确认转移",
                onClick = {
                    val target = selectedId
                    if (target == null) showError = true else onConfirm(target, note)
                },
                enabled = locations.isNotEmpty(),
                loading = isWorking,
            )
        },
        dismissButton = {
            CulinaryTextButton(text = "取消", onClick = onDismiss)
        },
    )
}
