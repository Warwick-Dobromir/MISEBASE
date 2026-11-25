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
import androidx.compose.material3.LinearProgressIndicator
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
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.QuantityText
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
                    InventoryArchiveHeading(
                        categoryLabel = detail.categoryPath.orEmpty(),
                        chineseTitle = detail.archive.name,
                        englishTitle = detail.archive.englishName,
                        description = null,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = CulinarySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(text = InventoryText.stockStatus(detail.row.status), tone = StatusTone.Accent)
                        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                        Text(detail.archive.stockUnit.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val productSpecification = detail.archive.productSpecification
                            ?: detail.links.firstOrNull()?.let { link ->
                                link.product.packageLabel ?: "${formatAmount(link.product.contentQty)} ${link.product.contentUnit.symbol}"
                            }
                        productSpecification?.takeIf { it.isNotBlank() }?.let { specification ->
                            Text(" · $specification", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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
                    InventoryArchiveSectionHeader(english = "STOCK OVERVIEW", title = "库存总量")
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs), verticalAlignment = Alignment.Bottom) {
                        Text(formatAmount(detail.row.onHand), style = CulinaryTextStyles.numeralL, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                        Text(detail.archive.stockUnit.symbol, style = CulinaryTextStyles.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(text = InventoryText.stockStatus(detail.row.status), tone = StatusTone.Accent)
                    }
                    val stockProgress = detail.archive.targetStock?.takeIf { it > 0.0 }?.let { (detail.row.onHand / it).coerceIn(0.0, 1.0).toFloat() } ?: 0f
                    LinearProgressIndicator(
                        progress = { stockProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("最低库存 ${detail.archive.minimumStock?.let(::formatAmount) ?: "未设置"} ${detail.archive.stockUnit.symbol}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("目标库存 ${detail.archive.targetStock?.let(::formatAmount) ?: "未设置"} ${detail.archive.stockUnit.symbol}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("可用库存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuantityText(amount = formatAmount(detail.row.available), unit = detail.archive.stockUnit.symbol, amountStyle = MaterialTheme.typography.titleLarge)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("冻结 / 预留", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuantityText(amount = formatAmount(detail.row.reserved), unit = detail.archive.stockUnit.symbol, amountStyle = MaterialTheme.typography.titleLarge)
                        }
                    }
                    InventoryArchiveValueRow("有效批次", detail.row.batchCount.toString())
                    InventoryArchiveValueRow("涉及库位", detail.row.locationNames.joinToString("、").ifBlank { "未指定" })
                    InventoryArchiveValueRow("默认存放位置", detail.archive.defaultLocationId?.let { state.locationNames[it.value] } ?: "未指定")
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                    InventoryArchiveSectionHeader(english = "BASIC INFORMATION", title = "基本信息")
                    InventoryArchiveValueRow("品牌", detail.archive.brand ?: detail.links.firstOrNull()?.product?.brand ?: "未设置")
                    InventoryArchiveValueRow("商品规格", detail.archive.productSpecification ?: detail.links.firstOrNull()?.product?.packageLabel ?: "未设置")
                    InventoryArchiveValueRow("商品编码", detail.archive.productCode ?: detail.links.firstOrNull()?.product?.sku ?: "未生成")
                    InventoryArchiveValueRow("条码", detail.archive.barcode?.takeIf { it.isNotBlank() } ?: "未关联")
                    detail.sauceName?.let { InventoryArchiveValueRow("关联酱汁", it) }
                    InventoryArchiveSectionHeader(english = "SHELF LIFE & STORAGE", title = "保质期与储存")
                    InventoryArchiveValueRow(
                        "默认保质期",
                        detail.archive.defaultShelfLifeUnit?.let { unit -> detail.archive.defaultShelfLifeValue?.let { value -> "$value ${unit.label}" } } ?: "未设置",
                    )
                    InventoryArchiveValueRow(
                        "保质期规则",
                        if (detail.archive.shelfLifeEnabled) {
                            "${detail.archive.shelfLifeCalculationMethod ?: "未指定起算方式"} · ${detail.archive.shelfLifeDays?.let { "$it 天" } ?: "未设置时长"}"
                        } else {
                            "未启用"
                        },
                    )
                    InventoryArchiveValueRow("开封后期限", detail.archive.openedShelfLifeDays?.let { "$it 天" } ?: "未设置")
                    InventoryArchiveValueRow("储存方式", detail.archive.storageMethod ?: "未设置")
                    InventoryArchiveValueRow("储存温度", detail.archive.storageTemperature ?: "未设置")
                    InventoryArchiveValueRow("最近到期", detail.row.nearestExpiry?.let(::formatDate) ?: "未登记")
                    InventoryArchiveSectionHeader(english = "PURCHASING INFORMATION", title = "采购信息")
                    InventoryArchiveValueRow("供应商", detail.archive.supplierName ?: "未设置")
                    InventoryArchiveValueRow("采购单位", detail.links.firstOrNull()?.product?.contentUnit?.name ?: if (detail.archive.purchaseUnitId != null) "已设置" else "未设置")
                    InventoryArchiveValueRow("单位换算", detail.archive.unitConversion ?: "未设置")
                    if (detail.links.isNotEmpty()) {
                        InventoryArchiveSectionHeader(english = "LINKED PRODUCTS", title = "关联采购商品")
                        detail.links.forEach { link ->
                            ArchiveLinkRow(
                                link = link,
                                unitSymbol = detail.archive.stockUnit.symbol,
                            )
                        }
                    }
                    InventoryArchiveSectionHeader(
                        english = "BATCH RECORDS",
                        title = "库存批次",
                        subtitle = "${detail.batches.size} BATCHES",
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
private fun InventoryArchiveHeading(
    categoryLabel: String,
    chineseTitle: String,
    englishTitle: String?,
    description: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (categoryLabel.isNotBlank()) {
            Text(categoryLabel.uppercase(), style = CulinaryTextStyles.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        }
        Text(chineseTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        if (!englishTitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
            Text(englishTitle.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        description?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
    }
}

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
            Text(
                text = "入库日期 ${formatInstant(batch.receivedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        StatusBadge(
            text = InventoryText.batchStatus(batch.status),
            tone = InventoryText.batchTone(batch.status),
        )
    }
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
