package com.culinary.feature.inventory

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.InventoryArchiveDetail
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 库存档案详情状态与采购商品关联操作。 */
data class ArchiveDetailUiState(
    val detail: InventoryArchiveDetail? = null,
    val locationNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ArchiveDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getArchiveDetail: GetInventoryArchiveDetailUseCase,
        private val listLocations: ListStorageLocationsUseCase,
    ) : ViewModel() {
        private val archiveId: String =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID).orEmpty()

        private val _uiState = MutableStateFlow(ArchiveDetailUiState())
        val uiState: StateFlow<ArchiveDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.detail == null, error = null) }
                if (archiveId.isBlank()) {
                    _uiState.update { it.copy(isLoading = false, error = "缺少档案标识，无法打开详情。") }
                    return@launch
                }
                when (val result = read()) {
                    is Outcome.Success -> _uiState.value = result.value
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        private suspend fun read(): Outcome<ArchiveDetailUiState> {
            val detail =
                when (val result = getArchiveDetail(archiveId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val locations =
                when (val result = listLocations()) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success -> result.value
                }
            return Outcome.success(
                ArchiveDetailUiState(
                    detail = detail,
                    locationNames = locations.associate { it.id.value to it.name },
                    isLoading = false,
                ),
            )
        }


    }

@Composable
fun ArchiveDetailRoute(
    onOpenBatch: (String) -> Unit,
    onOpenBatches: (String) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onReceipt: (String) -> Unit,
    onIssue: (String) -> Unit,
    onStocktake: (String) -> Unit,
    viewModel: ArchiveDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    ArchiveDetailScreen(
        state = state,
        onOpenBatch = onOpenBatch,
        onOpenBatches = onOpenBatches,
        onEdit = onEdit,
        onBack = onBack,
        onReceipt = onReceipt,
        onIssue = onIssue,
        onStocktake = onStocktake,
        onRetry = viewModel::load,
    )
}

@Suppress("LongMethod") // 档案详情按档案展示、库存概况、批次、采购关联与流水排列。
@Composable
private fun ArchiveDetailScreen(
    state: ArchiveDetailUiState,
    onOpenBatch: (String) -> Unit,
    onOpenBatches: (String) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onReceipt: (String) -> Unit,
    onIssue: (String) -> Unit,
    onStocktake: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val detail = state.detail
    Column(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = "重试",
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            detail == null -> EmptyState(
                title = "档案不存在",
                description = "该档案可能已被删除，请返回列表重新选择。",
            )
            else -> {
                ArchiveDetailBar(
                    editLabel = "编辑档案 ↗",
                    onBack = onBack,
                    onEdit = { onEdit(detail.archive.id) },
                )
                val context = LocalContext.current.applicationContext
                val imageUri = detail.archive.imageUri?.takeIf { it.isNotBlank() }
                val imageBytes by produceState<ByteArray?>(
                    initialValue = null,
                    key1 = imageUri,
                ) {
                    value = withContext(Dispatchers.IO) {
                        imageUri?.let { uri ->
                            runCatching {
                                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
                            }.getOrNull()
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CulinarySpacing.pageHorizontal)
                        .padding(bottom = CulinarySpacing.lg),
                ) {
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    ArchiveDetailHeading(
                        categoryLabel = detail.categoryPath.orEmpty(),
                        englishTitle = detail.archive.englishName,
                        chineseTitle = detail.archive.name,
                        description = detail.archive.description,
                    )
                    StatusBadge(
                        text = InventoryText.stockStatus(detail.row.status),
                        tone = InventoryText.stockTone(detail.row.status),
                        modifier = Modifier.padding(top = CulinarySpacing.xs),
                    )
                    imageBytes?.let { bytes ->
                        Spacer(modifier = Modifier.height(CulinarySpacing.md))
                        ArchiveWideImage(bytes = bytes, caption = null)
                    }
                    state.message?.let { message ->
                        StatusBadge(
                            text = message,
                            tone = StatusTone.Success,
                            modifier = Modifier.padding(bottom = CulinarySpacing.sm),
                        )
                    }
                    InventoryArchiveSectionHeader(english = "STOCK OVERVIEW", title = "库存情况")
                    InventoryArchiveValueRow(
                        "库存总量",
                        "${formatAmount(detail.row.onHand)} ${detail.archive.stockUnit.symbol}",
                    )
                    InventoryArchiveValueRow(
                        "最低库存",
                        detail.archive.minimumStock?.let { "${formatAmount(it)} ${detail.archive.stockUnit.symbol}" } ?: "未设置",
                    )
                    InventoryArchiveValueRow(
                        "目标库存",
                        detail.archive.targetStock?.let { "${formatAmount(it)} ${detail.archive.stockUnit.symbol}" } ?: "未设置",
                    )
                    InventoryArchiveValueRow(
                        "可用库存",
                        "${formatAmount(detail.row.available)} ${detail.archive.stockUnit.symbol}",
                    )
                    InventoryArchiveValueRow(
                        "预留库存",
                        "${formatAmount(detail.row.reserved)} ${detail.archive.stockUnit.symbol}",
                    )
                    InventoryArchiveValueRow("库存状态", InventoryText.stockStatus(detail.row.status), status = true)
                    InventoryArchiveValueRow("有效批次", detail.row.batchCount.toString())
                    InventoryArchiveValueRow("涉及库位", detail.row.locationNames.joinToString("、").ifBlank { "未指定" })

                    InventoryArchiveSectionHeader(english = "BASIC INFORMATION", title = "基本信息")
                    InventoryArchiveValueRow("所属分类", detail.categoryPath ?: "未指定分类")
                    InventoryArchiveValueRow("档案类型", if (detail.archive.kind == ArchiveKind.SAUCE) "酱汁现货档案" else "食材库存档案")
                    InventoryArchiveValueRow("库存基本单位", "${detail.archive.stockUnit.name}（${detail.archive.stockUnit.symbol}）")
                    InventoryArchiveValueRow("计量维度", detail.archive.stockUnit.dimension.name)
                    if (detail.archive.stockUnit.isApproximate) {
                        InventoryArchiveValueRow("换算属性", "近似换算")
                    }
                    detail.sauceName?.let { InventoryArchiveValueRow("关联酱汁", it) }

                    InventoryArchiveSectionHeader(english = "SHELF LIFE & STORAGE", title = "保质期与储存")
                    InventoryArchiveValueRow("储存方式", detail.archive.storageMethod ?: "未设置")
                    InventoryArchiveValueRow("储存温度", detail.archive.storageTemperature ?: "未设置")
                    InventoryArchiveValueRow("最近到期", detail.row.nearestExpiry?.let(::formatDate) ?: "未登记")

                    InventoryArchiveSectionHeader(english = "PURCHASING INFORMATION", title = "采购信息")
                    if (detail.links.isEmpty()) {
                        Text(
                            text = "尚未关联采购商品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = CulinarySpacing.xs),
                        )
                    } else {
                        detail.links.forEach { link ->
                            link.product.sku?.let { InventoryArchiveValueRow("商品编码", it) }
                            link.product.brand?.let { InventoryArchiveValueRow("品牌", it) }
                            link.product.packageLabel?.let { InventoryArchiveValueRow("包装规格", it) }
                            link.product.shelfLifeDays?.let { InventoryArchiveValueRow("商品保质期", "${it} 天") }
                            link.product.storageNote?.let { InventoryArchiveValueRow("商品储存备注", it) }
                            ArchiveLinkRow(
                                link = link,
                                unitSymbol = detail.archive.stockUnit.symbol,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    InventoryArchiveSectionHeader(
                        english = "BATCH RECORDS",
                        title = "库存批次",
                        action = {
                            CulinaryTextButton(
                                text = "查看全部 →",
                                onClick = { onOpenBatches(detail.archive.id) },
                            )
                        },
                    )
                    if (detail.batches.isEmpty()) {
                        Text(
                            text = "还没有批次。入库后会自动建立批次并累计余额。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        detail.batches.take(3).forEach { batch ->
                            ArchiveBatchRow(batch = batch, onClick = { onOpenBatch(batch.id) })
                        }
                    }
                    if (detail.relatedRecipeNames.isNotEmpty()) {
                        InventoryArchiveSectionHeader(english = "RELATED RECIPES", title = "关联菜谱")
                        detail.relatedRecipeNames.forEach { recipeName ->
                            InventoryArchiveValueRow("菜谱", recipeName)
                        }
                    }
                    InventoryArchiveSectionHeader(
                        english = "STOCK TRANSACTIONS",
                        title = "库存流水",
                        subtitle = "最近 ${minOf(detail.ledger.size, LEDGER_LIMIT)} 条",
                    )
                    if (detail.ledger.isEmpty()) {
                        Text(
                            text = "暂无流水。入库、出库、盘点与冻结都会留下记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        detail.ledger.take(LEDGER_LIMIT).forEach { entry ->
                            ArchiveLedgerRow(entry = entry)
                        }
                    }
                    InventoryArchiveSectionHeader(english = "ARCHIVE RECORD", title = "档案记录")
                    InventoryArchiveValueRow("档案编号", detail.archive.id)
                    InventoryArchiveValueRow("档案状态", detail.archive.status.name)
                    InventoryArchiveValueRow("创建时间", formatInstant(detail.archive.createdAt))
                    InventoryArchiveValueRow("最后修改", formatInstant(detail.archive.updatedAt))
                    InventoryArchiveValueRow("修订版本", "REV ${detail.archive.revision}")
                }
                CulinaryRule()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
                ) {
                    CulinaryTextButton(
                        text = "入库",
                        onClick = { onReceipt(detail.archive.id) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    )
                    CulinaryTextButton(
                        text = "出库",
                        onClick = { onIssue(detail.archive.id) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    )
                    CulinaryTextButton(
                        text = "盘点",
                        onClick = { onStocktake(detail.archive.id) },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    )
                }
            }
        }

    }
}

private const val LEDGER_LIMIT = 10

@Composable
private fun InventoryArchiveSectionHeader(
    english: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(english, style = CulinaryTextStyles.eyebrow, color = MaterialTheme.colorScheme.secondary)
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    }
}

@Composable
private fun InventoryArchiveValueRow(
    label: String,
    value: String,
    status: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (status) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
        )
    }
    CulinaryRule()
}

@Composable
private fun ArchiveLinkRow(
    link: ArchiveProductLink,
    unitSymbol: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.product.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "包装内容 ${formatAmount(link.product.contentQty)} ${link.product.contentUnit.symbol} · 1 件折算 ${formatAmount(link.conversionRatio)} $unitSymbol",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (link.isDefault) {
            StatusBadge(text = "默认", tone = StatusTone.Accent)
        }
    }
    CulinaryRule()
}

@Composable
private fun ArchiveBatchRow(
    batch: InventoryBatchRecord,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 56.dp)
                .padding(
                    horizontal = CulinarySpacing.pageHorizontal,
                    vertical = CulinarySpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.id.take(8),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    CulinaryRule(modifier = Modifier.padding(start = CulinarySpacing.pageHorizontal))
}

@Composable
private fun ArchiveLedgerRow(entry: InventoryLedgerEntry) {
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
                text = archiveDeltaText(entry),
                style = CulinaryTextStyles.numeralS,
                color = archiveDeltaColor(entry),
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
            Text(
                text = entry.stockUnit.symbol,
                style = CulinaryTextStyles.unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    CulinaryRule()
}

private fun archiveDeltaText(entry: InventoryLedgerEntry): String =
    when {
        entry.onHandDelta > 0.0 -> "+${formatAmount(entry.onHandDelta)}"
        entry.onHandDelta < 0.0 -> formatAmount(entry.onHandDelta)
        else -> "0"
    }

@Composable
private fun archiveDeltaColor(entry: InventoryLedgerEntry) =
    when {
        entry.onHandDelta > 0.0 -> CulinaryColorScheme.current.success
        entry.onHandDelta < 0.0 -> CulinaryColorScheme.current.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
