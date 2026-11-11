package com.culinary.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.usecase.inventory.CreatePrintJobUseCase
import com.culinary.core.domain.usecase.inventory.InventoryLedgerUseCases
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.PrintJobUseCases
import com.culinary.feature.inventory.print.InventoryPrintService
import com.culinary.feature.inventory.print.PrintResult
import com.culinary.feature.inventory.print.SlipLayout
import com.culinary.feature.inventory.print.SlipLine
import com.culinary.feature.inventory.print.SlipRenderPlan
import com.culinary.feature.inventory.print.SlipTextBuilder
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

/** 打印预览：只打印与更新打印任务状态，绝不改变库存。 */
@Composable
fun PrintPreviewRoute(
    onBack: () -> Unit,
    onPrinted: () -> Unit,
    viewModel: PrintPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val printService = remember(context) { InventoryPrintService(context) }
    LaunchedEffect(state.printed) {
        if (state.printed) {
            viewModel.consumePrinted()
            onPrinted()
        }
    }
    PrintPreviewScreen(
        state = state,
        onBack = onBack,
        onWidthChange = viewModel::selectWidth,
        onPrint = { viewModel.print(printService) },
        onReprint = { viewModel.reprint(printService) },
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 打印预览状态：计划由 SlipTextBuilder 生成，打印结果只写打印任务。 */
data class PrintPreviewUiState(
    val kind: String = "RECEIPT",
    val refId: String = "",
    val widthMm: Int = 80,
    val plan: SlipRenderPlan? = null,
    val jobId: String? = null,
    val jobStatus: PrintStatus? = null,
    val reprintCount: Int = 0,
    val isLoading: Boolean = true,
    val isPrinting: Boolean = false,
    val printFailed: Boolean = false,
    val failureReason: String? = null,
    val printed: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val title: String
        get() =
            when (kind) {
                "ISSUE" -> "出库单"
                "LABEL" -> "批次标签"
                else -> "入库单"
            }

    val refType: String
        get() =
            when (kind) {
                "ISSUE" -> "issue_slip"
                "LABEL" -> "inventory_batch"
                else -> "stock_receipt"
            }
}

/** 打印预览 ViewModel；打印失败只影响打印任务，库存已经记账且不会回滚。 */
@HiltViewModel
class PrintPreviewViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ledger: InventoryLedgerUseCases,
        private val listArchives: ListInventoryArchivesUseCase,
        private val listLocations: ListStorageLocationsUseCase,
        private val printJobs: PrintJobUseCases,
        private val createPrintJob: CreatePrintJobUseCase,
    ) : ViewModel() {
        private val kind: String =
            savedStateHandle.get<String>(InventoryRoutes.ARG_PRINT_KIND)
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }
                ?: "RECEIPT"

        private val refId: String = savedStateHandle.get<String>(InventoryRoutes.ARG_REF_ID).orEmpty()

        private val routeWidth: Int =
            when (savedStateHandle.get<String>(InventoryRoutes.ARG_WIDTH)?.toIntOrNull()) {
                58 -> 58
                else -> 80
            }

        private val _uiState =
            MutableStateFlow(
                PrintPreviewUiState(
                    kind = kind,
                    refId = refId,
                    widthMm = routeWidth,
                ),
            )
        val uiState: StateFlow<PrintPreviewUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val state = _uiState.value
                val entries =
                    when (val result = loadEntries(state.kind, state.refId)) {
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
                val job =
                    when (val result = printJobs.latest(state.refType, state.refId)) {
                        is Outcome.Failure -> null
                        is Outcome.Success -> result.value
                    }
                val width = job?.paperWidthMm ?: state.widthMm
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        widthMm = width,
                        jobId = job?.id,
                        jobStatus = job?.status,
                        reprintCount = job?.reprintCount ?: 0,
                        plan = buildPlan(state.kind, width, entries, archiveNames, locationNames),
                    )
                }
            }
        }

        /** 只切换纸宽：复用同一组逻辑行，重新折行与估算高度。 */
        fun selectWidth(widthMm: Int) {
            val state = _uiState.value
            if (state.widthMm == widthMm) return
            val plan = state.plan
            _uiState.update {
                it.copy(
                    widthMm = widthMm,
                    printFailed = false,
                    plan = plan?.let { current -> SlipLayout.plan(widthMm, current.lines) },
                )
            }
        }

        private suspend fun loadEntries(
            kind: String,
            refId: String,
        ): Outcome<List<InventoryLedgerEntry>> =
            when (kind) {
                "ISSUE" -> ledger.forReference("issue_slip", refId)
                "LABEL" -> ledger.forBatch(refId)
                else -> {
                    val byReference = ledger.forReference("stock_receipt", refId)
                    when (byReference) {
                        is Outcome.Failure -> ledger.forBatch(refId)
                        is Outcome.Success ->
                            if (byReference.value.isEmpty()) {
                                ledger.forBatch(refId)
                            } else {
                                byReference
                            }
                    }
                }
            }

        /** 用 SlipTextBuilder 组装逻辑行；同一次输入始终得到同一张小票，便于重打比对。 */
        private fun buildPlan(
            kind: String,
            widthMm: Int,
            entries: List<InventoryLedgerEntry>,
            archiveNames: Map<String, String>,
            locationNames: Map<String, String>,
        ): SlipRenderPlan {
            val lines = mutableListOf<SlipLine>()
            val first = entries.firstOrNull()
            val occurredAt = first?.occurredAt
            val time = occurredAt?.let(::detailTime) ?: "—"
            val location = first?.let { locationNames[it.locationId.value] } ?: "—"
            when (kind) {
                "ISSUE" -> {
                    lines +=
                        SlipTextBuilder.header(
                            title = "MISEBASE",
                            subtitle = "库存出库单 / INVENTORY ISSUE SLIP",
                            code = refId.take(12).uppercase(),
                            meta =
                                listOf(
                                    "类型" to "出库",
                                    "时间" to time,
                                    "库位" to location,
                                    "物料" to "${entries.size} 项",
                                ),
                        )
                    lines += SlipTextBuilder.divider()
                    entries.forEach { entry ->
                        lines +=
                            SlipTextBuilder.item(
                                name = archiveNames[entry.archiveId.orEmpty()] ?: "库存档案",
                                qtyWithUnit = "${formatAmount(kotlin.math.abs(entry.onHandDelta))} ${entry.stockUnit.symbol}",
                                detailLines =
                                    listOfNotNull(
                                        "批次 ${entry.batchId.value.take(8)}",
                                        entry.archiveId?.let { "档案 ${archiveNames[it] ?: it.take(8)}" },
                                    ),
                            )
                    }
                    lines += SlipTextBuilder.total("合计", "${entries.size} 项")
                    lines +=
                        SlipTextBuilder.footer(
                            "打印失败不影响库存记账；可在库存流水中重新打印。",
                        )
                }

                "LABEL" -> {
                    val name = first?.archiveId?.let { archiveNames[it] } ?: "库存批次"
                    lines +=
                        SlipTextBuilder.header(
                            title = "MISEBASE",
                            subtitle = "批次标签",
                            code = refId.take(12).uppercase(),
                            meta =
                                listOf(
                                    "档案" to name,
                                    "库位" to location,
                                    "入库" to time,
                                ),
                        )
                    lines += SlipTextBuilder.divider()
                    first?.let { entry ->
                        lines +=
                            SlipTextBuilder.item(
                                name = name,
                                qtyWithUnit = "${formatAmount(entry.onHandAfter)} ${entry.stockUnit.symbol}",
                                detailLines = listOf("批次 ${entry.batchId.value.take(8)}"),
                            )
                    }
                    lines += SlipTextBuilder.footer()
                }

                else -> {
                    val receiptEntries =
                        entries.filter { it.type == LedgerEntryType.PURCHASE_RECEIPT }.ifEmpty { entries }
                    val target = receiptEntries.firstOrNull()
                    lines +=
                        SlipTextBuilder.header(
                            title = "MISEBASE",
                            subtitle = "库存入库单 / INVENTORY RECEIPT",
                            code = refId.take(12).uppercase(),
                            meta =
                                listOf(
                                    "类型" to "入库",
                                    "时间" to time,
                                    "库位" to location,
                                    "档案" to (target?.archiveId?.let { archiveNames[it] } ?: "—"),
                                ),
                        )
                    lines += SlipTextBuilder.divider()
                    receiptEntries.forEach { entry ->
                        lines +=
                            SlipTextBuilder.item(
                                name = archiveNames[entry.archiveId.orEmpty()] ?: "库存档案",
                                qtyWithUnit = "${formatAmount(entry.onHandDelta)} ${entry.stockUnit.symbol}",
                                detailLines = listOf("批次 ${entry.batchId.value.take(8)}"),
                            )
                    }
                    lines += SlipTextBuilder.total("合计", "${receiptEntries.size} 项")
                    lines +=
                        SlipTextBuilder.footer(
                            "入库已记账；打印失败不回滚库存，可重新打印。",
                        )
                }
            }
            if (entries.isEmpty()) {
                lines += SlipTextBuilder.blank()
                lines += SlipLine("未找到单据明细", sizeSp = SlipTextBuilder.DETAIL_SIZE_SP)
            }
            return SlipLayout.plan(widthMm, lines)
        }

        /** 打印：成功标记已打印，失败标记打印失败；两种情况都不改变库存。 */
        fun print(service: InventoryPrintService) {
            val state = _uiState.value
            val plan = state.plan ?: run {
                _uiState.update { it.copy(error = "没有可打印的内容。") }
                return
            }
            if (state.isPrinting) return
            viewModelScope.launch {
                _uiState.update { it.copy(isPrinting = true, printFailed = false, failureReason = null, error = null) }
                val jobId = ensureJob(state)
                when (val result = service.print(jobName(state), plan)) {
                    is PrintResult.Started -> {
                        val job = jobId?.let { id -> markPrintedJob(id) }
                        _uiState.update {
                            it.copy(
                                isPrinting = false,
                                printed = true,
                                jobStatus = PrintStatus.PRINTED,
                                reprintCount = job?.reprintCount ?: it.reprintCount,
                                message = "已提交打印（${plan.widthMm}mm）。",
                            )
                        }
                    }

                    is PrintResult.Unavailable -> {
                        val job = jobId?.let { id -> markFailedJob(id, result.reason) }
                        _uiState.update {
                            it.copy(
                                isPrinting = false,
                                printFailed = true,
                                failureReason = result.reason,
                                jobStatus = PrintStatus.FAILED,
                                reprintCount = job?.reprintCount ?: it.reprintCount,
                                message = null,
                            )
                        }
                    }
                }
            }
        }

        /** 重新打印：先把任务置回待打印（计数 +1），再打印一次。 */
        fun reprint(service: InventoryPrintService) {
            val state = _uiState.value
            val plan = state.plan ?: return
            if (state.isPrinting) return
            viewModelScope.launch {
                _uiState.update { it.copy(isPrinting = true, error = null) }
                val jobId = state.jobId ?: ensureJob(state)
                val refreshed = jobId?.let { id -> markPendingJob(id) }
                when (val result = service.print(jobName(state), plan)) {
                    is PrintResult.Started -> {
                        val job = jobId?.let { id -> markPrintedJob(id) }
                        _uiState.update {
                            it.copy(
                                isPrinting = false,
                                printed = true,
                                printFailed = false,
                                failureReason = null,
                                jobStatus = PrintStatus.PRINTED,
                                reprintCount = job?.reprintCount ?: refreshed?.reprintCount ?: it.reprintCount,
                                message = "已重新提交打印，库存余额不变。",
                            )
                        }
                    }

                    is PrintResult.Unavailable -> {
                        val job = jobId?.let { id -> markFailedJob(id, result.reason) }
                        _uiState.update {
                            it.copy(
                                isPrinting = false,
                                printFailed = true,
                                failureReason = result.reason,
                                jobStatus = PrintStatus.FAILED,
                                reprintCount = job?.reprintCount ?: refreshed?.reprintCount ?: it.reprintCount,
                            )
                        }
                    }
                }
            }
        }

        private suspend fun ensureJob(state: PrintPreviewUiState): String? {
            state.jobId?.let { return it }
            val kindValue =
                when (state.kind) {
                    "ISSUE" -> PrintKind.ISSUE
                    "LABEL" -> PrintKind.LABEL
                    else -> PrintKind.RECEIPT
                }
            val job: PrintJobRecord? =
                when (val result = createPrintJob(kindValue, state.refType, state.refId, state.widthMm)) {
                    is Outcome.Failure -> null
                    is Outcome.Success -> result.value
                }
            if (job != null) {
                _uiState.update { it.copy(jobId = job.id, jobStatus = job.status, widthMm = job.paperWidthMm) }
            }
            return job?.id
        }

        private fun jobName(state: PrintPreviewUiState): String = "MISEBASE ${state.title} ${state.refId.take(8)}"

        private suspend fun markPrintedJob(jobId: String): PrintJobRecord? =
            (printJobs.markPrinted(jobId) as? Outcome.Success)?.value

        private suspend fun markFailedJob(
            jobId: String,
            reason: String?,
        ): PrintJobRecord? = (printJobs.markFailed(jobId, reason) as? Outcome.Success)?.value

        private suspend fun markPendingJob(jobId: String): PrintJobRecord? =
            (printJobs.reprint(jobId) as? Outcome.Success)?.value

        fun consumePrinted() {
            _uiState.update { it.copy(printed = false) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

private fun detailTime(value: java.time.Instant): String =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(value)

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

@Composable
internal fun PrintPreviewScreen(
    state: PrintPreviewUiState,
    onBack: () -> Unit,
    onWidthChange: (Int) -> Unit,
    onPrint: () -> Unit,
    onReprint: () -> Unit,
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
                    eyebrow = "PRINT",
                    title = "打印预览 · ${state.title}",
                    supporting = "预览按 58/80mm 比例显示；打印失败不会回滚库存，可重新打印。",
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
                    if (state.printFailed) {
                        ErrorBanner(
                            message =
                                "打印失败，库存已记账，可重新打印。" +
                                    (state.failureReason?.let { "（$it）" } ?: ""),
                            actionLabel = "重新打印",
                            onAction = onReprint,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
                    ) {
                        CulinaryFilterPill(
                            text = "58mm",
                            selected = state.widthMm == 58,
                            onClick = { onWidthChange(58) },
                        )
                        CulinaryFilterPill(
                            text = "80mm",
                            selected = state.widthMm == 80,
                            onClick = { onWidthChange(80) },
                        )
                        Text(
                            text = "打印任务：${state.jobStatus?.let { InventoryText.printStatus(it) } ?: "未创建"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SectionHeader(
                        title = "单据预览",
                        subtitle = "纸宽 ${state.widthMm}mm · 重打 ${state.reprintCount} 次",
                    )
                    val plan = state.plan
                    if (plan == null || plan.lines.isEmpty()) {
                        EmptyState(
                            title = "没有可预览的单据",
                            description = "单据明细为空或读取失败，请返回库存流水确认后再试。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                    } else {
                        SlipPreview(plan = plan)
                        CulinaryButton(
                            text = if (state.isPrinting) "正在打印" else "打印",
                            onClick = onPrint,
                            enabled = !state.isPrinting,
                            loading = state.isPrinting,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
                        )
                        CulinaryButton(
                            text = "重新打印",
                            onClick = onReprint,
                            variant = CulinaryButtonVariant.Secondary,
                            enabled = !state.isPrinting,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
                        )
                        Text(
                            text = "说明：打印失败只把打印任务标记为失败，库存已记账且不会回滚。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** 预览：按纸宽比例（1mm ≈ 4dp）铺开，白纸黑字、等宽、细线框。 */
@Composable
private fun SlipPreview(plan: SlipRenderPlan) {
    val ink = Color(0xFF1F1F1F)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            modifier =
                Modifier
                    .width((plan.widthMm * 4).dp)
                    .border(width = 1.dp, color = ink.copy(alpha = 0.35f))
                    .background(Color.White)
                    .padding(horizontal = CulinarySpacing.sm, vertical = CulinarySpacing.sm),
        ) {
            plan.lines.forEach { line ->
                val isDivider = line.text.isNotEmpty() && line.text.all { it == line.text.first() } && line.text.length > 6
                Text(
                    text = line.text,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = (line.sizeSp * 0.9f).sp,
                            fontWeight = if (line.bold) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    color = if (isDivider) ink.copy(alpha = 0.5f) else ink,
                    textAlign = if (line.alignEnd) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
