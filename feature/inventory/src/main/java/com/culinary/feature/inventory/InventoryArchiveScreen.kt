package com.culinary.feature.inventory

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.InventoryArchiveDetail
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.LinkArchiveProductUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.UnlinkArchiveProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 档案详情：身份、库存、存储、采购换算、批次与流水。 */
data class ArchiveDetailUiState(
    val detail: InventoryArchiveDetail? = null,
    val locationNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val products: List<Product> = emptyList(),
    val linkDialogOpen: Boolean = false,
    val linkProductId: String? = null,
    val linkRatio: String = "1",
    val linkIsDefault: Boolean = false,
    val linkError: String? = null,
    val isLinking: Boolean = false,
)

@HiltViewModel
class ArchiveDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getArchiveDetail: GetInventoryArchiveDetailUseCase,
        private val listLocations: ListStorageLocationsUseCase,
        private val formOptions: ListArchiveFormOptionsUseCase,
        private val linkArchiveProduct: LinkArchiveProductUseCase,
        private val unlinkArchiveProduct: UnlinkArchiveProductUseCase,
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
            val products =
                when (val result = formOptions()) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success -> result.value.products
                }
            return Outcome.success(
                ArchiveDetailUiState(
                    detail = detail,
                    locationNames = locations.associate { it.id.value to it.name },
                    isLoading = false,
                    products = products,
                ),
            )
        }

        fun openLinkDialog() {
            _uiState.update {
                it.copy(
                    linkDialogOpen = true,
                    linkProductId = null,
                    linkRatio = "1",
                    linkIsDefault = false,
                    linkError = null,
                )
            }
        }

        fun dismissLinkDialog() {
            if (_uiState.value.isLinking) return
            _uiState.update { it.copy(linkDialogOpen = false, linkError = null) }
        }

        fun selectLinkProduct(productId: String) {
            _uiState.update { it.copy(linkProductId = productId, linkError = null) }
        }

        fun updateLinkRatio(value: String) {
            _uiState.update { it.copy(linkRatio = value, linkError = null) }
        }

        fun toggleLinkDefault(isDefault: Boolean) {
            _uiState.update { it.copy(linkIsDefault = isDefault) }
        }

        fun confirmLink() {
            val state = _uiState.value
            if (state.isLinking) return
            val detail = state.detail ?: return
            val productId =
                state.linkProductId
                    ?: run {
                        _uiState.update { it.copy(linkError = "请选择要关联的采购商品。") }
                        return
                    }
            val ratio = state.linkRatio.trim().toDoubleOrNull()
            if (ratio == null || !ratio.isFinite() || ratio <= 0.0) {
                _uiState.update { it.copy(linkError = "换算比必须是大于 0 的数字。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isLinking = true, linkError = null) }
                when (val result = linkArchiveProduct(detail.archive.id, productId, ratio, state.linkIsDefault)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLinking = false,
                                linkError = InventoryErrorMessages.describe(result.error),
                            )
                        }

                    is Outcome.Success -> refreshAfterLinkChange("已关联采购商品。")
                }
            }
        }

        fun unlink(productId: String) {
            val state = _uiState.value
            if (state.isLinking) return
            val detail = state.detail ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isLinking = true, error = null) }
                when (val result = unlinkArchiveProduct(detail.archive.id, productId)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLinking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> refreshAfterLinkChange("已解除关联。")
                }
            }
        }

        private suspend fun refreshAfterLinkChange(message: String) {
            when (val result = read()) {
                is Outcome.Success ->
                    _uiState.value = result.value.copy(message = message, isLinking = false)
                is Outcome.Failure ->
                    _uiState.update {
                        it.copy(
                            isLinking = false,
                            message = message,
                            error = InventoryErrorMessages.describe(result.error),
                        )
                    }
            }
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
        onOpenLinkDialog = viewModel::openLinkDialog,
        onDismissLinkDialog = viewModel::dismissLinkDialog,
        onSelectLinkProduct = viewModel::selectLinkProduct,
        onLinkRatioChange = viewModel::updateLinkRatio,
        onLinkDefaultChange = viewModel::toggleLinkDefault,
        onConfirmLink = viewModel::confirmLink,
        onUnlink = viewModel::unlink,
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
    onOpenLinkDialog: () -> Unit,
    onDismissLinkDialog: () -> Unit,
    onSelectLinkProduct: (String) -> Unit,
    onLinkRatioChange: (String) -> Unit,
    onLinkDefaultChange: (Boolean) -> Unit,
    onConfirmLink: () -> Unit,
    onUnlink: (String) -> Unit,
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
                    Spacer(modifier = Modifier.height(CulinarySpacing.lg))
                    detail.categoryPath?.takeIf { it.isNotBlank() }?.let { category ->
                        Text(
                            text = category.uppercase(),
                            style = CulinaryTextStyles.eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                    }
                    Text(
                        text = detail.archive.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    detail.archive.englishName?.takeIf { it.isNotBlank() }?.let { englishName ->
                        Text(
                            text = englishName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                    detail.archive.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = CulinarySpacing.sm),
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(
                            text = InventoryText.stockStatus(detail.row.status),
                            tone = InventoryText.stockTone(detail.row.status),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "库存编号 · ${detail.archive.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CulinaryRule()
                    Spacer(modifier = Modifier.height(CulinarySpacing.lg))
                    Text(
                        text = "库存概况",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                        Column(modifier = Modifier.weight(1f)) {
                            QuantityText(
                                amount = formatAmount(detail.row.onHand),
                                unit = detail.archive.stockUnit.symbol,
                                amountStyle = CulinaryTextStyles.numeralL,
                            )
                            Text("当前库存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.row.batchCount.toString().padStart(2, '0'),
                                style = CulinaryTextStyles.numeralL,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("有效批次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.archive.minimumStock?.let { "${formatAmount(it)} ${detail.archive.stockUnit.symbol}" } ?: "未设置",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("库存预警线", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.archive.defaultLocationId?.let { state.locationNames[it.value] } ?: "未指定",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text("主要存放位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(CulinarySpacing.lg))
                    SectionHeader(
                        title = "库存批次",
                        subtitle = "按先进先出排列，点击查看批次详情",
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
                        detail.batches.forEach { batch ->
                            ArchiveBatchRow(batch = batch, onClick = { onOpenBatch(batch.id) })
                        }
                    }
                    CulinaryRule()
                    Spacer(modifier = Modifier.height(CulinarySpacing.md))
                    SectionHeader(title = "关联采购商品")
                    if (detail.links.isEmpty()) {
                        Text(
                            text = "尚未关联采购商品",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = CulinarySpacing.xs),
                        )
                    } else {
                        detail.links.forEach { link ->
                            ArchiveLinkRow(
                                link = link,
                                unitSymbol = detail.archive.stockUnit.symbol,
                                enabled = !state.isLinking,
                                onUnlink = { onUnlink(link.product.id.value) },
                            )
                        }
                    }
                    CulinaryTextButton(
                        text = "＋ 关联采购商品",
                        onClick = onOpenLinkDialog,
                        enabled = !state.isLinking,
                        modifier = Modifier.padding(top = CulinarySpacing.xxs),
                    )
                    CulinaryRule(modifier = Modifier.padding(top = CulinarySpacing.lg))
                    SectionHeader(
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
        if (state.linkDialogOpen) {
            LinkProductDialog(
                state = state,
                unitSymbol = state.detail?.archive?.stockUnit?.symbol ?: "",
                onDismiss = onDismissLinkDialog,
                onSelectProduct = onSelectLinkProduct,
                onRatioChange = onLinkRatioChange,
                onDefaultChange = onLinkDefaultChange,
                onConfirm = onConfirmLink,
            )
        }
    }
}

@Composable
private fun ArchiveLinkRow(
    link: ArchiveProductLink,
    unitSymbol: String,
    enabled: Boolean,
    onUnlink: () -> Unit,
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
                text = "1 个 → ${formatAmount(link.conversionRatio)} $unitSymbol",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (link.isDefault) {
            StatusBadge(text = "默认", tone = StatusTone.Accent)
        }
        CulinaryTextButton(
            text = "解除关联",
            onClick = onUnlink,
            enabled = enabled,
            danger = true,
            modifier = Modifier.padding(start = CulinarySpacing.xxs),
        )
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
            Text(
                text =
                    listOfNotNull(
                        batch.productionDate?.let { "生产 ${formatDate(it)}" },
                        batch.expiresOn?.let { "到期 ${formatDate(it)}" },
                    ).joinToString(" · ").ifBlank { "未登记生产与到期日期" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                    listOfNotNull(
                        batch.locationName?.let { "库位 $it" },
                        "剩余 ${formatAmount(batch.onHand)} ${batch.unit.symbol}",
                    ).joinToString(" · "),
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
