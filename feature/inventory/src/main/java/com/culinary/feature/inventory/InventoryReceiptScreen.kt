package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryDateField
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
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit as MeasureUnit
import com.culinary.core.domain.rule.inventory.calculateDefaultExpiryDate
import com.culinary.core.domain.usecase.inventory.ArchiveReceiptInput
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveProductLinksUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.RecordArchiveReceiptUseCase
import com.culinary.core.domain.usecase.inventory.RecordPurchaseReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/**
 * 入库：`?archiveId=` 有值 = 手工入库（默认）；无值 = 采购入库（多行待确认草稿）。
 *
 * 两种模式确认前都不改变库存，确认后由 UseCase 在单事务内建批次 + 写流水。
 */
@Composable
fun ReceiptRoute(
    onBack: () -> Unit,
    onPrint: (String, String) -> Unit,
    onSaved: () -> Unit,
    isPurchase: Boolean = false,
    onOpenManual: () -> Unit = {},
    onOpenPurchase: () -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(isPurchase) { viewModel.setMode(isPurchase) }
    LaunchedEffect(state.completion) {
        when (val done = state.completion) {
            is ReceiptCompletion.Print -> {
                viewModel.consumeCompletion()
                onPrint("RECEIPT", done.refId)
            }

            ReceiptCompletion.Saved -> {
                viewModel.consumeCompletion()
                onSaved()
            }

            null -> Unit
        }
    }
    ReceiptScreen(
        state = state,
        onBack = onBack,
        onOpenManual = onOpenManual,
        onOpenPurchase = onOpenPurchase,
        onArchiveSelected = viewModel::selectArchive,
        onQuantityChange = viewModel::onQuantityChange,
        onUnitSelected = viewModel::selectUnit,
        onBatchCodeChange = viewModel::onBatchCodeChange,
        onProductionDateChange = viewModel::onProductionDateChange,
        onExpiresOnChange = viewModel::onExpiresOnChange,
        onLocationSelected = viewModel::selectLocation,
        onSourceProductSelected = viewModel::selectSourceProduct,
        onNoteChange = viewModel::onNoteChange,
        onTemporaryChange = viewModel::onTemporaryChange,
        onAddLine = viewModel::addLine,
        onRemoveLine = viewModel::removeLine,
        onSubmit = viewModel::submit,
        onReload = viewModel::load,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 采购入库草稿行；确认前只存在于内存与页面，不写任何库存。 */
data class ReceiptPurchaseLine(
    val rowId: String,
    val archiveId: String,
    val archiveName: String,
    val quantity: Double,
    val unitId: String,
    val unitSymbol: String,
    val batchCode: String? = null,
    val productionDate: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val locationId: String? = null,
    val locationName: String? = null,
)

/** 一张入库单确认后的去向：有打印任务去打印，否则直接回列表。 */
sealed interface ReceiptCompletion {
    data class Print(val refId: String) : ReceiptCompletion

    data object Saved : ReceiptCompletion
}

/** 入库表单状态；采购模式复用同一套字段做逐行录入。 */
data class ReceiptUiState(
    val isPurchase: Boolean = false,
    val archives: List<InventoryArchiveRow> = emptyList(),
    val units: List<MeasureUnit> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val links: List<ArchiveProductLink> = emptyList(),
    val selectedArchiveId: String? = null,
    val quantity: String = "",
    val unitCode: String = "",
    val batchCode: String = "",
    val productionDate: String = "",
    val expiresOn: String = "",
    val locationId: String? = null,
    val sourceProductId: String? = null,
    val note: String = "",
    val isTemporary: Boolean = false,
    val lines: List<ReceiptPurchaseLine> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val completion: ReceiptCompletion? = null,
) {
    val selectedArchive: InventoryArchive?
        get() = archives.firstOrNull { it.archive.id == selectedArchiveId }?.archive

    val selectedArchiveName: String?
        get() = selectedArchive?.name

    val selectedLocationName: String?
        get() = locations.firstOrNull { it.id.value == locationId }?.name

    val unitSymbol: String
        get() = units.firstOrNull { it.code == unitCode }?.symbol ?: unitCode

    val sourceProductName: String?
        get() = links.firstOrNull { it.product.id.value == sourceProductId }?.product?.name

    val draftCount: Int
        get() = lines.size
}

/** 手工入库与采购入库共用的 ViewModel。 */
@HiltViewModel
class ReceiptViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listArchives: ListInventoryArchivesUseCase,
        private val listLocations: ListStorageLocationsUseCase,
        private val listFormOptions: ListArchiveFormOptionsUseCase,
        private val listProductLinks: ListArchiveProductLinksUseCase,
        private val recordReceipt: RecordArchiveReceiptUseCase,
        private val recordPurchase: RecordPurchaseReceiptUseCase,
    ) : ViewModel() {
        private val routeArchiveId: String? =
            savedStateHandle.get<String>(InventoryRoutes.ARG_ARCHIVE_ID)?.takeIf { it.isNotBlank() }

        /** 采购草稿编号：同一次采购会话固定，重复确认命中幂等重放。 */
        private val purchaseDraftId: String = UUID.randomUUID().toString()

        /** 手工入库幂等键：同一屏幕只生成一次，防止重复触发导致双写。 */
        private val receiptKey: String = UUID.randomUUID().toString()

        private val _uiState = MutableStateFlow(ReceiptUiState(isPurchase = false))
        val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun setMode(isPurchase: Boolean) {
            _uiState.update { it.copy(isPurchase = isPurchase) }
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val options =
                    when (val result = listFormOptions()) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value
                    }
                val archives =
                    when (val result = listArchives()) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> result.value.rows
                    }
                val locations =
                    when (val result = listLocations()) {
                        is Outcome.Failure -> options.locations
                        is Outcome.Success -> result.value
                    }
                val preselected =
                    archives.firstOrNull { it.archive.id == routeArchiveId } ?: archives.firstOrNull()
                _uiState.update {
                    it.copy(
                        archives = archives,
                        units = options.units,
                        locations = locations,
                        isLoading = false,
                        selectedArchiveId = preselected?.archive?.id,
                        unitCode = preselected?.archive?.stockUnit?.code.orEmpty(),
                        locationId = defaultLocationId(preselected?.archive, locations),
                    )
                }
                preselected?.archive?.id?.let { loadLinks(it) }
            }
        }

        /** 只采用档案明确配置的默认库位；绝不把列表第一项当成实际存放位置。 */
        private fun defaultLocationId(
            archive: InventoryArchive?,
            locations: List<StorageLocation>,
        ): String? =
            archive?.defaultLocationId?.value?.takeIf { id -> locations.any { it.id.value == id } }

        private suspend fun loadLinks(archiveId: String) {
            val links =
                when (val result = listProductLinks(archiveId)) {
                    is Outcome.Failure -> emptyList()
                    is Outcome.Success -> result.value
                }
            _uiState.update { it.copy(links = links, sourceProductId = null) }
        }

        fun selectArchive(archiveId: String) {
            _uiState.update { state ->
                val row = state.archives.firstOrNull { it.archive.id == archiveId }
                state.copy(
                    selectedArchiveId = archiveId,
                    unitCode = row?.archive?.stockUnit?.code ?: state.unitCode,
                    expiresOn = calculateDefaultExpiryDate(
                        productionDate = parseOrNull(state.productionDate),
                        shelfLifeValue = row?.archive?.defaultShelfLifeValue,
                        shelfLifeUnit = row?.archive?.defaultShelfLifeUnit,
                    )?.toString().orEmpty(),
                    locationId = defaultLocationId(row?.archive, state.locations),
                    links = emptyList(),
                    sourceProductId = null,
                    error = null,
                )
            }
            viewModelScope.launch { loadLinks(archiveId) }
        }

        fun onQuantityChange(value: String) {
            _uiState.update { it.copy(quantity = value, error = null) }
        }

        fun selectUnit(code: String) {
            _uiState.update { it.copy(unitCode = code, error = null) }
        }

        fun onBatchCodeChange(value: String) {
            _uiState.update { it.copy(batchCode = value) }
        }

        fun onProductionDateChange(value: String) {
            val state = _uiState.value
            val productionDate = parseOrNull(value)
            val archive = state.selectedArchive
            val calculatedExpiry = calculateDefaultExpiryDate(
                productionDate = productionDate,
                shelfLifeValue = archive?.defaultShelfLifeValue,
                shelfLifeUnit = archive?.defaultShelfLifeUnit,
            )
            _uiState.update {
                it.copy(
                    productionDate = value,
                    expiresOn = calculatedExpiry?.toString().orEmpty(),
                    error = null,
                )
            }
        }

        fun onExpiresOnChange(value: String) {
            _uiState.update { it.copy(expiresOn = value, error = null) }
        }

        fun selectLocation(locationId: String) {
            _uiState.update { it.copy(locationId = locationId) }
        }

        fun selectSourceProduct(productId: String?) {
            _uiState.update { it.copy(sourceProductId = productId) }
        }

        fun onNoteChange(value: String) {
            _uiState.update { it.copy(note = value) }
        }

        fun onTemporaryChange(value: Boolean) {
            _uiState.update { it.copy(isTemporary = value) }
        }

        fun removeLine(rowId: String) {
            _uiState.update { state -> state.copy(lines = state.lines.filterNot { it.rowId == rowId }) }
        }

        /** 采购入库：把当前字段校验后追加为一行待确认草稿。 */
        fun addLine() {
            val state = _uiState.value
            val archiveId = state.selectedArchiveId
            if (archiveId == null) {
                _uiState.update { it.copy(error = "请选择库存档案。") }
                return
            }
            val amount = state.quantity.trim().toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                _uiState.update { it.copy(error = "数量必须是大于 0 的数字。") }
                return
            }
            val production = parseOrNull(state.productionDate)
            if (state.productionDate.isNotBlank() && production == null) {
                _uiState.update { it.copy(error = "生产日期格式应为 YYYY-MM-DD。") }
                return
            }
            val expires = parseOrNull(state.expiresOn)
            if (state.expiresOn.isNotBlank() && expires == null) {
                _uiState.update { it.copy(error = "到期日期格式应为 YYYY-MM-DD。") }
                return
            }
            val unit = state.units.firstOrNull { it.code == state.unitCode }
            if (unit == null) {
                _uiState.update { it.copy(error = "请选择单位。") }
                return
            }
            if (state.locationId == null) {
                _uiState.update { it.copy(error = "请选择这批商品实际存放的库位。系统不会自动指定库位。") }
                return
            }
            val calculatedExpiry = calculateDefaultExpiryDate(
                productionDate = production,
                shelfLifeValue = state.selectedArchive?.defaultShelfLifeValue,
                shelfLifeUnit = state.selectedArchive?.defaultShelfLifeUnit,
            )
            val line =
                ReceiptPurchaseLine(
                    rowId = UUID.randomUUID().toString(),
                    archiveId = archiveId,
                    archiveName = state.selectedArchiveName ?: "库存档案",
                    quantity = amount,
                    unitId = unit.id.value,
                    unitSymbol = unit.symbol,
                    batchCode = state.batchCode.trim().takeIf { it.isNotEmpty() },
                    productionDate = production,
                    expiresOn = expires ?: calculatedExpiry,
                    locationId = state.locationId,
                    locationName = state.selectedLocationName,
                )
            _uiState.update {
                it.copy(
                    lines = it.lines + line,
                    quantity = "",
                    batchCode = "",
                    productionDate = "",
                    expiresOn = "",
                    error = null,
                    message = "已加入待确认入库单，共 ${it.lines.size + 1} 行。",
                )
            }
        }

        fun submit() {
            val state = _uiState.value
            if (state.isPurchase) {
                confirmPurchase()
                return
            }
            val archiveId = state.selectedArchiveId
            if (archiveId == null) {
                _uiState.update { it.copy(error = "请选择库存档案。") }
                return
            }
            val amount = state.quantity.trim().toDoubleOrNull()
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                _uiState.update { it.copy(error = "数量必须是大于 0 的数字。") }
                return
            }
            val unit = state.units.firstOrNull { it.code == state.unitCode }
            if (unit == null) {
                _uiState.update { it.copy(error = "请选择单位。") }
                return
            }
            if (state.locationId == null) {
                _uiState.update { it.copy(error = "请选择这批商品实际存放的库位。系统不会自动指定库位。") }
                return
            }
            val production = parseOrNull(state.productionDate)
            if (state.productionDate.isNotBlank() && production == null) {
                _uiState.update { it.copy(error = "生产日期格式应为 YYYY-MM-DD。") }
                return
            }
            val expires = parseOrNull(state.expiresOn)
            if (state.expiresOn.isNotBlank() && expires == null) {
                _uiState.update { it.copy(error = "到期日期格式应为 YYYY-MM-DD。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null, message = null) }
                val result =
                    recordReceipt(
                        ArchiveReceiptInput(
                            archiveId = archiveId,
                            quantity = amount,
                            unitId = unit.id.value,
                            batchCode = state.batchCode.trim().takeIf { it.isNotEmpty() },
                            productionDate = production,
                            expiresOn = expires ?: calculateDefaultExpiryDate(
                                productionDate = production,
                                shelfLifeValue = state.selectedArchive?.defaultShelfLifeValue,
                                shelfLifeUnit = state.selectedArchive?.defaultShelfLifeUnit,
                            ),
                            locationId = state.locationId.let(::LocationId),
                            sourceProductId = state.sourceProductId,
                            note = state.note.trim().takeIf { it.isNotEmpty() },
                            isTemporary = state.isTemporary,
                            idempotencyKey = receiptKey,
                        ),
                    )
                when (result) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isSaving = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        val job = result.value.printJob
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                message = "已入库，批次余额已更新。",
                                completion = job?.let { record -> ReceiptCompletion.Print(record.refId) }
                                    ?: ReceiptCompletion.Saved,
                            )
                        }
                    }
                }
            }
        }

        /** 采购入库：整单在一个事务内逐行入库，任一行失败整单回滚。 */
        private fun confirmPurchase() {
            val state = _uiState.value
            if (state.lines.isEmpty()) {
                _uiState.update { it.copy(error = "待确认入库单至少需要一行。") }
                return
            }
            val inputs =
                state.lines.map { line ->
                    ArchiveReceiptInput(
                        archiveId = line.archiveId,
                        quantity = line.quantity,
                        unitId = line.unitId,
                        batchCode = line.batchCode,
                        productionDate = line.productionDate,
                        expiresOn = line.expiresOn,
                        locationId = line.locationId?.let(::LocationId),
                        note = state.note.trim().takeIf { it.isNotEmpty() },
                    )
                }
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null, message = null) }
                when (val result = recordPurchase(purchaseDraftId, inputs, state.note.trim().takeIf { it.isNotEmpty() })) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isSaving = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success -> {
                        val refId = result.value.firstNotNullOfOrNull { it.printJob?.refId }
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                lines = emptyList(),
                                message = "已确认入库 ${result.value.size} 行。",
                                completion = refId?.let(ReceiptCompletion::Print) ?: ReceiptCompletion.Saved,
                            )
                        }
                    }
                }
            }
        }

        fun consumeCompletion() {
            _uiState.update { it.copy(completion = null) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        private fun parseOrNull(text: String): LocalDate? {
            if (text.isBlank()) return null
            val digits = text.filter(Char::isDigit)
            if (digits.length != 8) return null
            val isoDate = digits.substring(0, 4) + "-" + digits.substring(4, 6) + "-" + digits.substring(6, 8)
            return runCatching { LocalDate.parse(isoDate) }.getOrNull()
        }
    }

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

private enum class ReceiptPicker {
    ARCHIVE,
    UNIT,
    LOCATION,
    SOURCE_PRODUCT,
}

@Composable
internal fun ReceiptScreen(
    state: ReceiptUiState,
    onBack: () -> Unit,
    onOpenManual: () -> Unit,
    onOpenPurchase: () -> Unit,
    onArchiveSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitSelected: (String) -> Unit,
    onBatchCodeChange: (String) -> Unit,
    onProductionDateChange: (String) -> Unit,
    onExpiresOnChange: (String) -> Unit,
    onLocationSelected: (String) -> Unit,
    onSourceProductSelected: (String?) -> Unit,
    onNoteChange: (String) -> Unit,
    onTemporaryChange: (Boolean) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onDismissError: () -> Unit,
) {
    var picker by remember { mutableStateOf<ReceiptPicker?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }
    var confirmationChecked by remember { mutableStateOf(false) }
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
                    eyebrow = when {
                        showConfirmation -> "STOCK IN / FINAL REVIEW"
                        state.isPurchase -> "STOCK IN / PURCHASE"
                        else -> "INVENTORY / STOCK IN"
                    },
                    title = if (showConfirmation) "确认入库" else "入库",
                    supporting = when {
                        showConfirmation -> "RECEIPT CONFIRMATION"
                        state.isPurchase -> "采购入库 · PURCHASE RECEIPT"
                        else -> "STOCK RECEIPT · 库存接收"
                    },
                )
                if (!showConfirmation) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.lg),
                    ) {
                        Text(
                            text = "手动入库",
                            modifier = Modifier.clickable(enabled = !state.isSaving, onClick = onOpenManual)
                                .padding(vertical = CulinarySpacing.md),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (!state.isPurchase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "采购入库",
                            modifier = Modifier.clickable(enabled = !state.isSaving, onClick = onOpenPurchase)
                                .padding(vertical = CulinarySpacing.md),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.isPurchase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CulinaryRule()
                }
                if (state.isPurchase) {
                    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                        SectionHeader(title = "01  采购信息")
                        ReceiptValueRow("供应商", "选择采购单后读取")
                        ReceiptValueRow("采购单号", "由采购单自动带入")
                        ReceiptValueRow("采购日期", "由采购单自动带入")
                        SectionHeader(title = "02  采购明细")
                        EmptyState(
                            title = "请选择采购单",
                            description = "采购入库将关联库存主页的采购单。选择后读取供应商、采购商品和采购数量；实际收货数量可与采购数量不同，未收齐部分保留在原采购单。",
                            compact = true,
                        )
                        SectionHeader(title = "03  验收入库")
                        ReceiptValueRow("实际收货数量", "选择采购单后填写")
                        ReceiptValueRow(
                            label = "存放库位",
                            value = state.selectedLocationName ?: "选择实际库位",
                            onClick = { picker = ReceiptPicker.LOCATION },
                        )
                        ReceiptValueRow("批次信息", "按实际收货生成")
                        ReceiptValueRow("验收状态", "待确认验收", accent = true)
                        ReceiptValueRow("采购总额", "选择采购单后计算", emphasize = true)
                    }
                    return@Column
                }
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    state.message?.let { text ->
                        StatusBadge(
                            text = text,
                            tone = StatusTone.Success,
                            modifier = Modifier.padding(bottom = CulinarySpacing.sm),
                        )
                    }
                    if (state.archives.isEmpty()) {
                        EmptyState(
                            title = "还没有库存档案",
                            description = "请先在库存档案中建立档案，再回到这里入库。",
                            actionLabel = "重新加载",
                            onAction = onReload,
                        )
                        return@Column
                    }
                    if (showConfirmation) {
                        ReceiptConfirmationContent(
                            state = state,
                            checked = confirmationChecked,
                            onCheckedChange = { confirmationChecked = it },
                            onBack = { showConfirmation = false; confirmationChecked = false },
                            onConfirm = onSubmit,
                        )
                        return@Column
                    }
                    ReceiptFields(
                        state = state,
                        onOpenPicker = { picker = it },
                        onQuantityChange = onQuantityChange,
                        onBatchCodeChange = onBatchCodeChange,
                        onProductionDateChange = onProductionDateChange,
                        onExpiresOnChange = onExpiresOnChange,
                        onNoteChange = onNoteChange,
                        onTemporaryChange = onTemporaryChange,
                        showTemporary = !state.isPurchase,
                    )
                    if (state.isPurchase) {
                        CulinaryButton(
                            text = "添加一行",
                            onClick = onAddLine,
                            variant = CulinaryButtonVariant.Secondary,
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
                        )
                        SectionHeader(
                            title = "待确认入库单",
                            subtitle = "共 ${state.draftCount} 行 · 确认前不改变库存",
                        )
                        if (state.lines.isEmpty()) {
                            EmptyState(
                                title = "待确认入库单为空",
                                description = "填写档案、数量、单位、批次与日期后逐行添加。",
                                compact = true,
                            )
                        } else {
                            state.lines.forEach { line ->
                                ReceiptDraftRow(line = line, enabled = !state.isSaving, onRemove = { onRemoveLine(line.rowId) })
                                CulinaryRule()
                            }
                        }
                        CulinaryButton(
                            text = if (state.isSaving) "正在确认" else "确认入库",
                            onClick = onSubmit,
                            enabled = !state.isSaving && state.lines.isNotEmpty(),
                            loading = state.isSaving,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
                        )
                        if (state.lines.isEmpty()) {
                            Text(
                                text = "至少添加一行后才能确认入库。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = CulinarySpacing.xs),
                            )
                        }
                    } else {
                        CulinaryRule()
                        ReceiptSummaryRow("本次入库数量", "${state.quantity.ifBlank { "0" }} ${state.unitSymbol}")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CulinaryTextButton(text = "取消", onClick = onBack, enabled = !state.isSaving)
                            CulinaryTextButton(
                                text = if (state.isSaving) "正在入库…" else "确认入库  →",
                                onClick = { showConfirmation = true },
                                enabled = !state.isSaving && state.quantity.isNotBlank() && state.selectedArchiveId != null,
                            )
                        }
                    }
                }
            }
        }
    }

    when (picker) {
        ReceiptPicker.ARCHIVE ->
            ReceiptPickerDialog(
                title = "库存档案",
                options = state.archives.map { it.archive.id to it.archive.name },
                emptyText = "还没有可入库的库存档案。",
                onSelect = {
                    onArchiveSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        ReceiptPicker.UNIT ->
            ReceiptPickerDialog(
                title = "单位",
                options = state.units.map { it.code to "${it.name}（${it.symbol}）" },
                emptyText = "没有可用单位。",
                onSelect = {
                    onUnitSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        ReceiptPicker.LOCATION ->
            ReceiptPickerDialog(
                title = "库位",
                options = state.locations.map { it.id.value to it.name },
                emptyText = "还没有库位，请先在库位管理页新建库位。",
                onSelect = {
                    onLocationSelected(it)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        ReceiptPicker.SOURCE_PRODUCT ->
            ReceiptPickerDialog(
                title = "来源采购商品",
                options =
                    listOf("" to "不关联采购商品") +
                        state.links.map { it.product.id.value to it.product.name },
                emptyText = "该档案还没有关联采购商品。",
                onSelect = {
                    onSourceProductSelected(it.takeIf { value -> value.isNotEmpty() })
                    picker = null
                },
                onDismiss = { picker = null },
            )

        null -> Unit
    }
}

@Composable
private fun ReceiptConfirmationContent(
    state: ReceiptUiState,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    StatusBadge(
        text = "待确认",
        tone = StatusTone.Success,
        modifier = Modifier.padding(bottom = CulinarySpacing.md),
    )
    SectionHeader(title = "入库摘要")
    Text(
        text = "${state.quantity.ifBlank { "0" }} ${state.unitSymbol}",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "本次实际入库数量",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CulinaryRule()
    SectionHeader(title = "食材明细")
    Text(
        text = state.selectedArchiveName ?: "未选择食材",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = "${state.quantity.ifBlank { "0" }} ${state.unitSymbol}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.xs, bottom = CulinarySpacing.sm),
    )
    ReceiptSummaryRow("新建批次", "是")
    ReceiptSummaryRow("存放库位", state.selectedLocationName ?: "未选择")
    CulinaryRule()
    SectionHeader(title = "批次信息")
    ReceiptSummaryRow("入库日期", LocalDate.now().toString())
    ReceiptSummaryRow("批次号", state.batchCode.ifBlank { "系统生成" })
    ReceiptSummaryRow("生产日期", state.productionDate.ifBlank { "未填写" })
    ReceiptSummaryRow("到期日期", state.expiresOn.ifBlank { "未填写" })
    CulinaryRule()
    SectionHeader(title = "最终确认")
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = "我已核对本次入库数量、批次和库位。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    CulinaryRule()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CulinaryTextButton(text = "返回修改", onClick = onBack)
        CulinaryTextButton(
            text = if (state.isSaving) "正在入库…" else "确认入库并打印  →",
            onClick = onConfirm,
            enabled = checked && !state.isSaving,
        )
    }
}

@Composable
private fun ReceiptSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ReceiptValueRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = CulinarySpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (onClick != null) {
                Text("  ›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    CulinaryRule()
}

@Composable
private fun ReceiptFields(
    state: ReceiptUiState,
    onOpenPicker: (ReceiptPicker) -> Unit,
    onQuantityChange: (String) -> Unit,
    onBatchCodeChange: (String) -> Unit,
    onProductionDateChange: (String) -> Unit,
    onExpiresOnChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTemporaryChange: (Boolean) -> Unit,
    showTemporary: Boolean,
) {
    SectionHeader(title = "01  选择食材")
    ReceiptValueRow(
        label = "食材名称",
        value = state.selectedArchiveName ?: "选择库存档案",
        onClick = { onOpenPicker(ReceiptPicker.ARCHIVE) },
    )

    SectionHeader(title = "02  入库数量")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CulinaryTextField(
            value = state.quantity,
            onValueChange = onQuantityChange,
            label = "数量",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            fillWidth = false,
            modifier = Modifier.weight(1f),
        )
        CulinaryTextButton(
            text = state.unitSymbol.ifBlank { "选择单位" },
            onClick = { onOpenPicker(ReceiptPicker.UNIT) },
        )
    }
    CulinaryRule()

    SectionHeader(title = "03  批次信息")
    ReceiptValueRow("入库日期", LocalDate.now().toString())
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("生产日期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CulinaryDateField(
            value = state.productionDate,
            onValueChange = onProductionDateChange,
            label = "选择日期",
            modifier = Modifier.weight(1f).padding(start = CulinarySpacing.lg),
        )
    }
    CulinaryRule()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("保质期 / 到期日期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CulinaryDateField(
            value = state.expiresOn,
            onValueChange = onExpiresOnChange,
            label = "填写",
            modifier = Modifier.weight(1f).padding(start = CulinarySpacing.lg),
        )
    }
    CulinaryRule()
    ReceiptValueRow(
        label = "库位",
        value = state.selectedLocationName ?: "选择实际库位",
        onClick = { onOpenPicker(ReceiptPicker.LOCATION) },
    )
    CulinaryTextField(
        value = state.batchCode,
        onValueChange = onBatchCodeChange,
        label = "批次号（可选）",
        placeholder = "留空则按入库时间生成",
        singleLine = true,
        modifier = Modifier.padding(top = CulinarySpacing.md),
    )
    SectionHeader(title = "04  备注")
    CulinaryTextField(
        value = state.note,
        onValueChange = onNoteChange,
        label = "填写本次入库说明……",
        minLines = 2,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    if (showTemporary) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "临时库存", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "开启后计入临时库存，超过提醒天数会在临时库存页提示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.isTemporary, onCheckedChange = onTemporaryChange)
        }
    }
}

@Composable
private fun ReceiptDraftRow(
    line: ReceiptPurchaseLine,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.archiveName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildString {
                    append(formatAmount(line.quantity))
                    append(" ")
                    append(line.unitSymbol)
                    line.batchCode?.let {
                        append(" · 批次 ")
                        append(it)
                    }
                    append(" · ")
                    append(line.locationName ?: "未指定库位")
                    line.productionDate?.let {
                        append(" · 生产 ")
                        append(it)
                    }
                    line.expiresOn?.let {
                        append(" · 到期 ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryTextButton(text = "移除", onClick = onRemove, enabled = enabled, danger = true)
    }
}

@Composable
private fun ReceiptPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    emptyText: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (options.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    options.forEach { (value, label) ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(value) }
                                    .padding(vertical = CulinarySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        CulinaryRule()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
