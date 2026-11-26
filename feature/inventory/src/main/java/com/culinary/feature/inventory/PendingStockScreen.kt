package com.culinary.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.CulinaryDateField
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.PendingStockDateAllocation
import com.culinary.core.domain.repository.PendingStockEntry
import com.culinary.core.domain.repository.PendingStockRepository
import com.culinary.core.domain.usecase.inventory.ConfirmPendingStockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PendingStockUiState(
    val items: List<PendingStockEntry> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val deleteItemId: String? = null,
    val incompleteItemId: String? = null,
    val missingLocationItemId: String? = null,
    val completed: Boolean = false,
) {
    val totalQuantity: Double get() = items.sumOf { it.quantity }
    val incompleteCount: Int get() = items.sumOf { it.unallocatedQuantity.toInt() }
}

@HiltViewModel
class PendingStockViewModel @Inject constructor(
    private val repository: PendingStockRepository,
    private val confirmPendingStock: ConfirmPendingStockUseCase,
    private val archiveRepository: InventoryArchiveRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PendingStockUiState())
    val uiState: StateFlow<PendingStockUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
            runCatching { repository.list() }
                .onSuccess { rows ->
                    val locations = runCatching { archiveRepository.listLocations() }.getOrNull()
                    _uiState.update {
                        it.copy(
                            items = rows,
                            locations = (locations as? Outcome.Success)?.value.orEmpty(),
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(isLoading = false, error = error.message ?: "读取待入库清单失败。") } }
        }
    }

    fun changeQuantity(itemId: String, delta: Double) {
        val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
        val next = item.quantity + delta
        if (next < 1.0) {
            _uiState.update { it.copy(deleteItemId = itemId) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.setQuantity(itemId, next) }
                .onSuccess { load() }
                .onFailure { error -> _uiState.update { state -> state.copy(error = error.message ?: "数量更新失败。") } }
        }
    }

    fun requestDelete(itemId: String) { _uiState.update { it.copy(deleteItemId = itemId) } }
    fun dismissDelete() { _uiState.update { it.copy(deleteItemId = null) } }

    fun confirmDelete() {
        val id = _uiState.value.deleteItemId ?: return
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess {
                    _uiState.update { it.copy(deleteItemId = null) }
                    load()
                }
                .onFailure { error -> _uiState.update { state -> state.copy(deleteItemId = null, error = error.message ?: "删除失败。") } }
        }
    }

    fun clear() {
        viewModelScope.launch {
            runCatching { repository.clear() }
                .onSuccess { load() }
                .onFailure { error -> _uiState.update { state -> state.copy(error = error.message ?: "清空失败。") } }
        }
    }

    fun saveAllocations(itemId: String, rows: List<PendingStockDateAllocation>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching { repository.replaceAllocations(itemId, rows) }
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, incompleteItemId = null) }
                    load()
                }
                .onFailure { error -> _uiState.update { state -> state.copy(isSaving = false, error = error.message ?: "生产日期保存失败。") } }
        }
    }

    fun confirm() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        val incomplete = state.items.firstOrNull { it.unallocatedQuantity > 1e-9 }
        if (incomplete != null) {
            _uiState.update { it.copy(incompleteItemId = incomplete.id) }
            return
        }
        val missingLocation = state.items.firstOrNull { item -> item.allocations.any { it.locationId.isNullOrBlank() } }
        if (missingLocation != null) {
            val allocation = missingLocation.allocations.first { it.locationId.isNullOrBlank() }
            _uiState.update {
                it.copy(
                    missingLocationItemId = missingLocation.id,
                    error = "${missingLocation.name}（生产日期 ${allocation.productionDate}）尚未选择库位。",
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = confirmPendingStock()) {
                is Outcome.Success -> _uiState.update { it.copy(isSaving = false, completed = true) }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = InventoryErrorMessages.describe(result.error))
                }
            }
        }
    }

    fun dismissIncomplete() { _uiState.update { it.copy(incompleteItemId = null) } }
    fun dismissMissingLocation() { _uiState.update { it.copy(missingLocationItemId = null) } }
}

private fun parseProductionDateDigits(value: String): LocalDate? {
    val digits = value.filter(Char::isDigit).take(8)
    if (digits.length != 8) return null
    val isoDate = "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
    return runCatching { LocalDate.parse(isoDate) }.getOrNull()
}

@Composable
fun PendingStockRoute(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: PendingStockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onCompleted() }
    PendingStockScreen(
        state = state,
        onBack = onBack,
        onChangeQuantity = viewModel::changeQuantity,
        onOpenDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onClear = viewModel::clear,
        onSaveDates = viewModel::saveAllocations,
        onConfirm = viewModel::confirm,
        onDismissIncomplete = viewModel::dismissIncomplete,
        onDismissMissingLocation = viewModel::dismissMissingLocation,
    )
}

@Composable
private fun PendingStockScreen(
    state: PendingStockUiState,
    onBack: () -> Unit,
    onChangeQuantity: (String, Double) -> Unit,
    onOpenDelete: (String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onClear: () -> Unit,
    onSaveDates: (String, List<PendingStockDateAllocation>) -> Unit,
    onConfirm: () -> Unit,
    onDismissIncomplete: () -> Unit,
) {
    var editingItem by remember { mutableStateOf<PendingStockEntry?>(null) }
    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBulkDateDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            eyebrow = "INVENTORY / PENDING STOCK",
            title = "待入库",
            supporting = "商品核对与入库管理",
        )
        state.error?.let {
            ErrorBanner(message = it, modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
        }

        if (state.isLoading) {
            LoadingRows()
        } else {
            // 商品列表独立滚动；汇总与底部操作区不参与列表滚动。
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionHeader(
                    title = "待入库概况",
                    subtitle = "商品种类 ${state.items.size} · 商品总件数 ${formatAmount(state.totalQuantity)}",
                    modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                )
                CulinaryRule()
                if (selectedItemIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = CulinarySpacing.pageHorizontal,
                            vertical = CulinarySpacing.xs,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("已选 ${selectedItemIds.size} 项", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CulinaryTextButton(text = "批量填写日期", onClick = { showBulkDateDialog = true })
                            CulinaryTextButton(text = "取消选择", onClick = { selectedItemIds = emptySet() })
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    Text(
                        text = "待入库清单为空。扫码识别的商品会先进入这里，确认后才写入正式库存。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                    )
                }
                state.items.forEach { item ->
                    PendingStockRow(
                        item = item,
                        selected = item.id in selectedItemIds,
                        showSelection = selectedItemIds.isNotEmpty(),
                        onClick = {
                            if (selectedItemIds.isNotEmpty()) {
                                selectedItemIds =
                                    if (item.id in selectedItemIds) selectedItemIds - item.id else selectedItemIds + item.id
                            } else {
                                editingItem = item
                            }
                        },
                        onLongClick = { selectedItemIds = selectedItemIds + item.id },
                        onDecrease = { onChangeQuantity(item.id, -1.0) },
                        onIncrease = { onChangeQuantity(item.id, 1.0) },
                    )
                    val missing = item.unallocatedQuantity
                    Text(
                        text = if (missing > 1e-9) {
                            "尚有 ${formatAmount(missing)} 件未填写生产日期"
                        } else {
                            "生产日期已填写"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (missing > 1e-9) CulinaryColorScheme.current.danger
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = CulinarySpacing.pageHorizontal,
                            bottom = CulinarySpacing.xs,
                        ),
                    )
                    CulinaryRule()
                }
            }

            // 固定底栏：商品再多也不随列表长度移动。
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = CulinarySpacing.pageHorizontal)
                    .padding(top = CulinarySpacing.sm, bottom = CulinarySpacing.md),
                verticalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("清单汇总", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "商品 ${state.items.size} 项",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "  ·  总件数 ${formatAmount(state.totalQuantity)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CulinaryRule()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CulinaryTextButton(
                        text = "清空清单",
                        onClick = onClear,
                        enabled = state.items.isNotEmpty() && !state.isSaving,
                    )
                    CulinaryTextButton(
                        text = if (state.isSaving) "正在入库" else "确认入库",
                        onClick = onConfirm,
                        enabled = state.items.isNotEmpty() && !state.isSaving,
                    )
                }
            }
        }
    }

    editingItem?.let { item ->
        PendingStockDateSheet(
            item = item,
            locations = state.locations,
            onDismiss = { editingItem = null },
            onSave = { rows ->
                onSaveDates(item.id, rows)
                editingItem = null
            },
        )
    }

    if (showBulkDateDialog) {
        var productionDate by remember { mutableStateOf("") }
        val selectedItems = state.items.filter { it.id in selectedItemIds && it.unallocatedQuantity > 1e-9 }
        AlertDialog(
            onDismissRequest = { showBulkDateDialog = false },
            title = { Text("批量填写生产日期") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                    Text("将同一生产日期应用于所选商品尚未分配日期的数量；已填写的日期分组不会被覆盖。")
                    CulinaryDateField(
                        value = productionDate,
                        onValueChange = { productionDate = it },
                        label = "生产日期",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("将填写 ${selectedItems.size} 项商品", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = parseProductionDateDigits(productionDate)
                        if (date != null) {
                            selectedItems.forEach { item ->
                                onSaveDates(
                                    item.id,
                                    item.allocations + PendingStockDateAllocation(
                                        id = "",
                                        productionDate = date,
                                        quantity = item.unallocatedQuantity,
                                        sortOrder = item.allocations.size,
                                    ),
                                )
                            }
                            selectedItemIds = emptySet()
                            showBulkDateDialog = false
                        }
                    },
                    enabled = parseProductionDateDigits(productionDate) != null && selectedItems.isNotEmpty(),
                ) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { showBulkDateDialog = false }) { Text("取消") } },
        )
    }

    state.deleteItemId?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("删除待入库商品？") },
            text = { Text("确定从待入库清单中删除「${item?.name.orEmpty()}」吗？正式库存档案和库存数量不会受到影响。") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("确认删除", color = CulinaryColorScheme.current.danger) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("取消") } },
        )
    }

    state.missingLocationItemId?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = onDismissMissingLocation,
            title = { Text("库位尚未选择") },
            text = { Text("${item?.name.orEmpty()} 的每个生产日期分组都必须单独选择库位，确认入库不会自动分配。") },
            confirmButton = {
                TextButton(onClick = {
                    editingItem = item
                    onDismissMissingLocation()
                }) { Text("去选择库位") }
            },
            dismissButton = { TextButton(onClick = onDismissMissingLocation) { Text("取消") } },
        )
    }

    state.incompleteItemId?.let { id ->
        val firstItem = state.items.firstOrNull { it.id == id }
        val incompleteItems = state.items.filter { it.unallocatedQuantity > 1e-9 }
        AlertDialog(
            onDismissRequest = onDismissIncomplete,
            title = { Text("生产日期尚未填写") },
            text = {
                Text(
                    incompleteItems.joinToString(separator = "\n") {
                        "${it.name}：尚有 ${formatAmount(it.unallocatedQuantity)} 件未填写"
                    } + "\n完成后才能确认入库。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingItem = firstItem
                        onDismissIncomplete()
                    },
                ) { Text("去填写") }
            },
            dismissButton = { TextButton(onClick = onDismissIncomplete) { Text("取消") } },
        )
    }
}

@Composable
private fun PendingStockRow(
    item: PendingStockEntry,
    selected: Boolean,
    showSelection: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        if (showSelection) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else if (!item.imageUri.isNullOrBlank()) {
            AsyncImage(
                model = item.imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.brand?.takeIf { it.isNotBlank() }?.let { "${item.name} · $it" } ?: item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            item.categoryName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.specification?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDecrease) { Text("−") }
            Text(formatAmount(item.quantity), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onIncrease) { Text("+") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingStockDateSheet(
    item: PendingStockEntry,
    locations: List<StorageLocation>,
    onDismiss: () -> Unit,
    onSave: (List<PendingStockDateAllocation>) -> Unit,
) {
    var rows by remember(item.id) {
        mutableStateOf(
            item.allocations.map { it.productionDate.toString().filter(Char::isDigit) to formatAmount(it.quantity) }
                .ifEmpty { listOf("" to "") },
        )
    }
    var locationIds by remember(item.id) { mutableStateOf(item.allocations.map { it.locationId }.ifEmpty { listOf(null) }) }
    val allocated = rows.sumOf { it.second.toDoubleOrNull()?.takeIf { value -> value.isFinite() && value > 0 } ?: 0.0 }
    val remaining = (item.quantity - allocated).coerceAtLeast(0.0)
    val valid = allocated <= item.quantity + 1e-9 && locationIds.size == rows.size && locationIds.all { !it.isNullOrBlank() } && rows.all { (date, quantity) ->
        date.isBlank() && quantity.isBlank() ||
            parseProductionDateDigits(date) != null &&
            (quantity.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true)
    }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = CulinarySpacing.xl),
            verticalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "生产日期分配",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            CulinaryRule()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("待分配总量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${formatAmount(item.quantity)} 件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            rows.forEachIndexed { index, row ->
                Column(verticalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${(index + 1).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "  日期分组",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (rows.size > 1) {
                            TextButton(onClick = {
                                rows = rows.toMutableList().also { it.removeAt(index) }
                                locationIds = locationIds.toMutableList().also { it.removeAt(index) }
                            }) { Text("移除") }
                        }
                    }
                    CulinaryDateField(
                        value = row.first,
                        onValueChange = { value ->
                            rows = rows.toMutableList().also { it[index] = value to row.second }
                        },
                        label = "生产日期",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    var locationMenuExpanded by remember(index) { mutableStateOf(false) }
                    val selectedLocation = locations.firstOrNull { it.id.value == locationIds.getOrNull(index) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLocation?.name ?: "选择库位",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("存放库位") },
                            modifier = Modifier.fillMaxWidth().clickable { locationMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                        )
                        DropdownMenu(
                            expanded = locationMenuExpanded,
                            onDismissRequest = { locationMenuExpanded = false },
                        ) {
                            if (locations.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("暂无可用库位") },
                                    enabled = false,
                                    onClick = {},
                                )
                            }
                            locations.forEach { location ->
                                DropdownMenuItem(
                                    text = { Text(location.name) },
                                    onClick = {
                                        locationIds = locationIds.toMutableList().also { it[index] = location.id.value }
                                        locationMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Text("对应数量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = row.second,
                        onValueChange = { value ->
                            rows = rows.toMutableList().also { it[index] = row.first to value }
                        },
                        placeholder = { Text("填写数量") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
                if (index != rows.lastIndex) CulinaryRule()
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    rows = rows + ("" to "")
                    locationIds = locationIds + null
                }.padding(vertical = CulinarySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(
                    "  添加生产日期分组",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            CulinaryRule()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("已分配", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatAmount(allocated)} 件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("尚未分配", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${formatAmount(remaining)} 件",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (remaining > 1e-9) CulinaryColorScheme.current.danger else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = valid) {
                    val parsed = rows.mapIndexedNotNull { index, row ->
                        val date = parseProductionDateDigits(row.first)
                        val qty = row.second.trim().toDoubleOrNull()
                        if (date != null && qty != null && qty.isFinite() && qty > 0) {
                            PendingStockDateAllocation(
                                id = item.allocations.getOrNull(index)?.id.orEmpty(),
                                productionDate = date,
                                quantity = qty,
                                sortOrder = index,
                                locationId = locationIds.getOrNull(index),
                            )
                        } else null
                    }
                    onSave(parsed)
                }.padding(vertical = CulinarySpacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "保存日期分配",
                    color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("  →", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
