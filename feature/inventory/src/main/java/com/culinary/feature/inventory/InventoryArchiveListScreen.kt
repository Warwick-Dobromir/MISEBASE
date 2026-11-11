package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinarySearchField
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 档案列表：搜索 + 四档状态筛选；行内展示分类、可用量与批次数。 */
data class ArchiveListUiState(
    val rows: List<InventoryArchiveRow> = emptyList(),
    val total: Int = 0,
    val filter: ArchiveListFilter = ArchiveListFilter.ALL,
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArchiveListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listArchives: ListInventoryArchivesUseCase,
    ) : ViewModel() {
        private val initialFilter: ArchiveListFilter =
            savedStateHandle
                .get<String>(InventoryRoutes.ARG_FILTER)
                ?.let { raw -> ArchiveListFilter.entries.firstOrNull { it.name == raw.uppercase() } }
                ?: ArchiveListFilter.ALL

        private val _uiState = MutableStateFlow(ArchiveListUiState(filter = initialFilter))
        val uiState: StateFlow<ArchiveListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.rows.isEmpty(), error = null) }
                val state = _uiState.value
                when (val result = listArchives(filter = state.filter, query = state.query)) {
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                rows = result.value.rows,
                                total = result.value.summary.total,
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

        fun onQueryChange(value: String) {
            _uiState.update { it.copy(query = value) }
            load()
        }

        fun onFilterChange(filter: ArchiveListFilter) {
            if (_uiState.value.filter == filter) return
            _uiState.update { it.copy(filter = filter) }
            load()
        }
    }

@Composable
fun ArchiveListRoute(
    onOpenArchive: (String) -> Unit,
    onNewArchive: () -> Unit,
    viewModel: ArchiveListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    ArchiveListScreen(
        state = state,
        onOpenArchive = onOpenArchive,
        onNewArchive = onNewArchive,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onRetry = viewModel::load,
    )
}

@Suppress("LongMethod") // 搜索、筛选与列表属于同一屏状态。
@Composable
private fun ArchiveListScreen(
    state: ArchiveListUiState,
    onOpenArchive: (String) -> Unit,
    onNewArchive: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ArchiveListFilter) -> Unit,
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
            eyebrow = "INVENTORY",
            title = "库存物品",
            supporting = "共 ${state.total} 个档案 · 当前显示 ${state.rows.size} 个",
            actions = {
                CulinaryTextButton(text = "＋ 新建档案", onClick = onNewArchive)
            },
        )
        CulinarySearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = "搜索档案名称",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
        )
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
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        ) {
            ArchiveListFilter.entries.forEach { filter ->
                CulinaryFilterPill(
                    text = archiveFilterLabel(filter),
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                )
            }
        }
        when {
            state.isLoading -> LoadingRows()
            state.total == 0 ->
                EmptyState(
                    icon = CulinaryIcons.Inventory,
                    title = "还没有库存档案",
                    description = "新建档案后即可入库、出库与盘点，数量会随流水自动累计。",
                    actionLabel = "＋ 新建档案",
                    onAction = onNewArchive,
                )

            state.rows.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Search,
                    title = "没有符合条件的物品",
                    description = "试试清空搜索关键词，或换一个状态筛选。",
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.archive.id }) { row ->
                        ArchiveListRowItem(row = row, onClick = { onOpenArchive(row.archive.id) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
                    }
                }
        }
    }
}

@Composable
private fun ArchiveListRowItem(
    row: InventoryArchiveRow,
    onClick: () -> Unit,
) {
    ArchiveListItem(
        title = row.archive.name,
        subtitle =
            listOfNotNull(
                row.archive.englishName?.takeIf { it.isNotBlank() },
                row.categoryName ?: "未分类",
                "${row.batchCount} 个批次",
            ).joinToString(" · "),
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                QuantityText(
                    amount = formatAmount(row.available),
                    unit = row.archive.stockUnit.symbol,
                )
                Spacer(modifier = Modifier.height(2.dp))
                StatusBadge(
                    text = InventoryText.stockStatus(row.status),
                    tone = InventoryText.stockTone(row.status),
                )
            }
        },
    )
}

private fun archiveFilterLabel(filter: ArchiveListFilter): String =
    when (filter) {
        ArchiveListFilter.ALL -> "全部"
        ArchiveListFilter.NORMAL -> "正常"
        ArchiveListFilter.LOW -> "低库存"
        ArchiveListFilter.OUT -> "不足"
    }
