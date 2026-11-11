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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.culinary.core.designsystem.component.CulinaryFilterPill
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
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StocktakeLine
import com.culinary.core.domain.model.StocktakeScope
import com.culinary.core.domain.model.StocktakeStatus
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.StocktakeUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// 路由
// ---------------------------------------------------------------------------

/** 盘点：范围选择 → 快照报告 → 逐行实盘 → 确认调整。 */
@Composable
fun StocktakeRoute(
    onBack: () -> Unit,
    viewModel: StocktakeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StocktakeScreen(
        state = state,
        onBack = onBack,
        onScopeChange = viewModel::selectScope,
        onScopeRefSelected = viewModel::selectScopeRef,
        onCreateReport = viewModel::createReport,
        onCountedChange = viewModel::onCountedChange,
        onConfirm = viewModel::confirm,
        onDismissError = viewModel::clearError,
    )
}

// ---------------------------------------------------------------------------
// 状态
// ---------------------------------------------------------------------------

/** 盘点页面状态；实盘输入先留在内存，确认前一次性写入盘点单。 */
data class StocktakeUiState(
    val scope: StocktakeScope = StocktakeScope.ALL,
    val scopeRef: String? = null,
    val archives: List<InventoryArchiveRow> = emptyList(),
    val categories: List<ArchiveCategory> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val stocktake: Stocktake? = null,
    val counted: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val isConfirmed: Boolean
        get() = stocktake?.status == StocktakeStatus.CONFIRMED

    val scopeRefLabel: String?
        get() =
            when (scope) {
                StocktakeScope.ALL -> null
                StocktakeScope.CATEGORY -> categories.firstOrNull { it.id == scopeRef }?.pathLabel
                StocktakeScope.LOCATION -> locations.firstOrNull { it.id.value == scopeRef }?.name
                StocktakeScope.ARCHIVE -> archives.firstOrNull { it.archive.id == scopeRef }?.archive?.name
            }

    fun countedText(line: StocktakeLine): String =
        counted[line.id] ?: formatAmount(line.countedQty)

    fun countedValue(line: StocktakeLine): Double =
        counted[line.id]?.trim()?.toDoubleOrNull() ?: line.countedQty

    fun diffOf(line: StocktakeLine): Double = countedValue(line) - line.bookQty
}

/** 盘点 ViewModel：建单只读，确认才写调整流水。 */
@HiltViewModel
class StocktakeViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listArchives: ListInventoryArchivesUseCase,
        private val listFormOptions: ListArchiveFormOptionsUseCase,
        private val listLocations: ListStorageLocationsUseCase,
        private val stocktakes: StocktakeUseCases,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                StocktakeUiState(
                    scope = parseScope(savedStateHandle.get<String>(InventoryRoutes.ARG_SCOPE)),
                    scopeRef = savedStateHandle.get<String>(InventoryRoutes.ARG_SCOPE_REF)?.takeIf { it.isNotBlank() },
                ),
            )
        val uiState: StateFlow<StocktakeUiState> = _uiState.asStateFlow()

        init {
            load()
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
                _uiState.update {
                    it.copy(
                        archives = archives,
                        categories = options.categories,
                        locations = locations,
                        isLoading = false,
                    )
                }
            }
        }

        fun selectScope(scope: StocktakeScope) {
            _uiState.update {
                it.copy(scope = scope, scopeRef = null, stocktake = null, counted = emptyMap(), error = null, message = null)
            }
        }

        fun selectScopeRef(ref: String) {
            _uiState.update {
                it.copy(scopeRef = ref, stocktake = null, counted = emptyMap(), error = null, message = null)
            }
        }

        fun createReport() {
            val state = _uiState.value
            if (state.scope != StocktakeScope.ALL && state.scopeRef.isNullOrBlank()) {
                _uiState.update { it.copy(error = "请先选择盘点范围的具体对象。") }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                when (val result = stocktakes.create(state.scope, state.scopeRef)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                stocktake = result.value,
                                counted = result.value.lines.associate { line -> line.id to formatAmount(line.countedQty) },
                                message = "盘点报告已生成，共 ${result.value.lines.size} 行。",
                            )
                        }
                }
            }
        }

        fun onCountedChange(lineId: String, text: String) {
            _uiState.update { it.copy(counted = it.counted + (lineId to text), error = null) }
        }

        /** 确认：先把实盘值写回盘点单，再逐行写调整流水；冻结批次不因盘点解冻。 */
        fun confirm() {
            val state = _uiState.value
            val stocktake = state.stocktake ?: return
            if (state.isConfirmed) return
            val parsed =
                stocktake.lines.associate { line ->
                    val raw = state.countedText(line).trim()
                    val value = raw.toDoubleOrNull()
                    if (value == null || !value.isFinite() || value < 0.0) {
                        _uiState.update { it.copy(error = "「${line.archiveName}」的实盘数量必须是非负数字。") }
                        return
                    }
                    line.id to value
                }
            viewModelScope.launch {
                _uiState.update { it.copy(isWorking = true, error = null, message = null) }
                var current = stocktake
                for (line in stocktake.lines) {
                    val value = parsed[line.id] ?: line.countedQty
                    if (kotlin.math.abs(value - line.countedQty) < 1e-9) continue
                    when (val result = stocktakes.counted(stocktake.id, line.id, value)) {
                        is Outcome.Failure -> {
                            _uiState.update {
                                it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                            }
                            return@launch
                        }

                        is Outcome.Success -> {
                            current = result.value
                            _uiState.update { it.copy(stocktake = current) }
                        }
                    }
                }
                when (val result = stocktakes.confirm(stocktake.id)) {
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isWorking = false, error = InventoryErrorMessages.describe(result.error))
                        }

                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                isWorking = false,
                                stocktake = result.value,
                                message = "盘点已确认，差异已写入调整流水。",
                            )
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

private fun parseScope(value: String?): StocktakeScope =
    when (value) {
        StocktakeScope.CATEGORY.name -> StocktakeScope.CATEGORY
        StocktakeScope.LOCATION.name -> StocktakeScope.LOCATION
        StocktakeScope.ARCHIVE.name -> StocktakeScope.ARCHIVE
        else -> StocktakeScope.ALL
    }

// ---------------------------------------------------------------------------
// 页面
// ---------------------------------------------------------------------------

@Suppress("LongMethod") // 经营范围、报告与确认三段共用一个纵向滚动，拆分会重复状态判断。
@Composable
internal fun StocktakeScreen(
    state: StocktakeUiState,
    onBack: () -> Unit,
    onScopeChange: (StocktakeScope) -> Unit,
    onScopeRefSelected: (String) -> Unit,
    onCreateReport: () -> Unit,
    onCountedChange: (String, String) -> Unit,
    onConfirm: () -> Unit,
    onDismissError: () -> Unit,
) {
    var scopePickerOpen by remember { mutableStateOf(false) }
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
                    eyebrow = "STOCKTAKE",
                    title = "库存盘点",
                    supporting = "先生成报告再确认；确认前不改变库存，冻结批次可盘点且不会因此解冻。",
                    actions = { CulinaryTextButton(text = "返回", onClick = onBack) },
                )
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    state.message?.let { text ->
                        StatusBadge(text = text, tone = StatusTone.Success, modifier = Modifier.padding(bottom = CulinarySpacing.sm))
                    }
                    SectionHeader(title = "盘点范围", subtitle = "范围决定快照包含哪些在库批次。")
                    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                        CulinaryFilterPill(
                            text = "全部",
                            selected = state.scope == StocktakeScope.ALL,
                            onClick = { onScopeChange(StocktakeScope.ALL) },
                        )
                        CulinaryFilterPill(
                            text = "分类",
                            selected = state.scope == StocktakeScope.CATEGORY,
                            onClick = { onScopeChange(StocktakeScope.CATEGORY) },
                        )
                        CulinaryFilterPill(
                            text = "库位",
                            selected = state.scope == StocktakeScope.LOCATION,
                            onClick = { onScopeChange(StocktakeScope.LOCATION) },
                        )
                        CulinaryFilterPill(
                            text = "档案",
                            selected = state.scope == StocktakeScope.ARCHIVE,
                            onClick = { onScopeChange(StocktakeScope.ARCHIVE) },
                        )
                    }
                    if (state.scope != StocktakeScope.ALL) {
                        CulinaryButton(
                            text = state.scopeRefLabel ?: scopeRefPlaceholder(state.scope),
                            onClick = { scopePickerOpen = true },
                            variant = CulinaryButtonVariant.Secondary,
                            icon = CulinaryIcons.ChevronDown,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
                        )
                    }
                    CulinaryButton(
                        text = if (state.isWorking) "正在生成" else "生成盘点报告",
                        onClick = onCreateReport,
                        enabled = !state.isWorking,
                        loading = state.isWorking,
                        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
                    )

                    val stocktake = state.stocktake
                    SectionHeader(
                        title = "盘点报告",
                        subtitle = stocktake?.let { "范围：${it.scopeLabel} · 差异 ${it.diffCount} 行" },
                    )
                    if (stocktake == null) {
                        EmptyState(
                            title = "尚未生成盘点报告",
                            description = "选择范围后点击「生成盘点报告」，账面数量来自当前在库批次。",
                            compact = true,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = if (state.isConfirmed) "已确认" else "草稿",
                                tone = if (state.isConfirmed) StatusTone.Success else StatusTone.Warning,
                            )
                            Text(
                                text = if (state.isConfirmed) "已确认，报告只读。" else "确认后差异写入调整流水。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = CulinarySpacing.xs),
                            )
                        }
                        stocktake.lines.forEach { line ->
                            StocktakeLineRow(
                                line = line,
                                countedText = state.countedText(line),
                                diff = state.diffOf(line),
                                enabled = !state.isConfirmed,
                                onCountedChange = { onCountedChange(line.id, it) },
                            )
                            CulinaryRule()
                        }
                        if (stocktake.lines.isEmpty()) {
                            EmptyState(
                                title = "该范围没有在库批次",
                                description = "换一个范围或先完成入库，再生成盘点报告。",
                                compact = true,
                            )
                        }
                        CulinaryButton(
                            text = if (state.isConfirmed) "已确认" else "确认盘点调整",
                            onClick = onConfirm,
                            enabled = !state.isConfirmed && !state.isWorking && stocktake.lines.isNotEmpty(),
                            loading = state.isWorking,
                            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
                        )
                        if (state.isConfirmed) {
                            Text(
                                text = "已确认，库存已按差异调整并留下流水。",
                                style = MaterialTheme.typography.bodySmall,
                                color = CulinaryColorScheme.current.success,
                                modifier = Modifier.padding(top = CulinarySpacing.xs),
                            )
                        } else {
                            Text(
                                text = "差异为「实盘 − 账面」：正数盘盈、负数盘亏；为 0 的行不写流水。",
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

    if (scopePickerOpen) {
        val options =
            when (state.scope) {
                StocktakeScope.CATEGORY -> state.categories.map { it.id to it.pathLabel }
                StocktakeScope.LOCATION -> state.locations.map { it.id.value to it.name }
                StocktakeScope.ARCHIVE -> state.archives.map { it.archive.id to it.archive.name }
                StocktakeScope.ALL -> emptyList()
            }
        StocktakePickerDialog(
            title = scopeRefPlaceholder(state.scope),
            options = options,
            onSelect = {
                onScopeRefSelected(it)
                scopePickerOpen = false
            },
            onDismiss = { scopePickerOpen = false },
        )
    }
}

private fun scopeRefPlaceholder(scope: StocktakeScope): String =
    when (scope) {
        StocktakeScope.ALL -> "全部库存"
        StocktakeScope.CATEGORY -> "选择分类"
        StocktakeScope.LOCATION -> "选择库位"
        StocktakeScope.ARCHIVE -> "选择档案"
    }

@Composable
private fun StocktakeLineRow(
    line: StocktakeLine,
    countedText: String,
    diff: Double,
    enabled: Boolean,
    onCountedChange: (String) -> Unit,
) {
    val danger = CulinaryColorScheme.current.danger
    val success = CulinaryColorScheme.current.success
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line.archiveName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (line.frozen) {
                StatusBadge(text = "冻结", tone = StatusTone.Warning)
            }
        }
        Text(
            text =
                (line.batchCode?.takeIf { it.isNotBlank() } ?: line.batchId.take(8)) +
                    " · " + (line.locationName ?: "未指定库位") +
                    " · 账面 ${formatAmount(line.bookQty)} ${line.unitSymbol}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CulinaryTextField(
                value = countedText,
                onValueChange = onCountedChange,
                label = "实盘",
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                fillWidth = false,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${line.unitSymbol}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
            Column(modifier = Modifier.padding(start = CulinarySpacing.md), horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDiff(diff),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        when {
                            diff > 0.0 -> success
                            diff < 0.0 -> danger
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = diffDirection(diff),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDiff(diff: Double): String =
    when {
        diff > 1e-9 -> "+${formatAmount(diff)}"
        diff < -1e-9 -> formatAmount(diff)
        else -> "0"
    }

private fun diffDirection(diff: Double): String =
    when {
        diff > 1e-9 -> "盘盈"
        diff < -1e-9 -> "盘亏"
        else -> "无差异"
    }

@Composable
private fun StocktakePickerDialog(
    title: String,
    options: List<Pair<String, String>>,
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
                    text = "没有可选对象。",
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
