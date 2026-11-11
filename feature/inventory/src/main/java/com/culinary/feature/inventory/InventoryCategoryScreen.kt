package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.InventoryCategoryNode
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryCategoryTreeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分类页：子分类目录 + 直属档案；分类树与数量全部由用例解析。 */
data class InventoryCategoryUiState(
    val categoryId: String? = null,
    val title: String = "库存分类",
    val pathLabel: String? = null,
    val children: List<InventoryCategoryNode> = emptyList(),
    val archives: List<InventoryArchiveRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class InventoryCategoryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val listCategoryTree: ListInventoryCategoryTreeUseCase,
        private val listArchives: ListInventoryArchivesUseCase,
    ) : ViewModel() {
        private val categoryId: String? =
            savedStateHandle
                .get<String>(InventoryRoutes.ARG_CATEGORY_ID)
                ?.takeIf { it.isNotBlank() }

        private val _uiState = MutableStateFlow(InventoryCategoryUiState(categoryId = categoryId))
        val uiState: StateFlow<InventoryCategoryUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = it.children.isEmpty() && it.archives.isEmpty(), error = null) }
                when (val result = read()) {
                    is Outcome.Success -> _uiState.value = result.value
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                        }
                }
            }
        }

        private suspend fun read(): Outcome<InventoryCategoryUiState> {
            val children =
                when (val result = listCategoryTree(categoryId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val archives =
                when (val result = listArchives(filter = ArchiveListFilter.ALL, categoryId = categoryId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val direct = archives.rows.filter { it.archive.categoryId == categoryId }
            val category = categoryId?.let { id -> findCategory(id) }
            return Outcome.success(
                InventoryCategoryUiState(
                    categoryId = categoryId,
                    title = category?.name ?: "未分类",
                    pathLabel = category?.pathLabel,
                    children = children,
                    archives = direct,
                    isLoading = false,
                ),
            )
        }

        /** 从根节点逐层查找到当前分类，避免在 UI 层持有仓库。 */
        private suspend fun findCategory(id: String): ArchiveCategory? {
            val roots =
                when (val result = listCategoryTree(null)) {
                    is Outcome.Failure -> return null
                    is Outcome.Success -> result.value
                }
            val queue = ArrayDeque(roots)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.category.id == id) return node.category
                val children =
                    when (val result = listCategoryTree(node.category.id)) {
                        is Outcome.Failure -> emptyList()
                        is Outcome.Success -> result.value
                    }
                children.forEach(queue::addLast)
            }
            return null
        }
    }

@Composable
fun InventoryCategoryRoute(
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onManage: () -> Unit,
    viewModel: InventoryCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryCategoryScreen(
        state = state,
        onOpenCategory = onOpenCategory,
        onOpenArchive = onOpenArchive,
        onManage = onManage,
        onRetry = viewModel::load,
    )
}

@Composable
private fun InventoryCategoryScreen(
    state: InventoryCategoryUiState,
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onManage: () -> Unit,
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
            eyebrow = "INVENTORY CATEGORY",
            title = state.title,
            supporting = categorySupporting(state),
            actions = {
                CulinaryTextButton(text = "管理分类", onClick = onManage)
            },
        )
        when {
            state.isLoading -> LoadingRows()
            state.children.isEmpty() && state.archives.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Inventory,
                    title = "这个分类还是空的",
                    description = "还没有子分类或档案。可以先在「管理分类」里新建子分类，再把物品归档进来。",
                    actionLabel = "管理分类",
                    onAction = onManage,
                )

            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = CulinarySpacing.xl),
                ) {
                    CategoryChildSection(
                        children = state.children,
                        onOpenCategory = onOpenCategory,
                    )
                    CategoryArchiveSection(
                        archives = state.archives,
                        onOpenArchive = onOpenArchive,
                    )
                }
            }
        }
    }
}

private fun categorySupporting(state: InventoryCategoryUiState): String {
    val path = state.pathLabel
    val counts = "${state.children.size} 个子分类 · ${state.archives.size} 个档案"
    return if (path.isNullOrBlank()) counts else "$path · $counts"
}

@Composable
private fun CategoryChildSection(
    children: List<InventoryCategoryNode>,
    onOpenCategory: (String) -> Unit,
) {
    SectionHeader(
        title = "子分类",
        subtitle = if (children.isEmpty()) "没有下级分类" else "数量为含后代的档案总数",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.sm),
    )
    if (children.isEmpty()) {
        Text(
            text = "该分类下没有子分类。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    bottom = CulinarySpacing.sm,
                ),
        )
        return
    }
    children.forEach { node ->
        ArchiveListItem(
            title = node.category.name,
            subtitle = if (node.childCount > 0) "${node.childCount} 个子分类" else null,
            meta = "${node.totalArchiveCount} 项",
            onClick = { onOpenCategory(node.category.id) },
        )
    }
}

@Composable
private fun CategoryArchiveSection(
    archives: List<InventoryArchiveRow>,
    onOpenArchive: (String) -> Unit,
) {
    SectionHeader(
        title = "直属档案",
        subtitle = if (archives.isEmpty()) "该分类下还没有档案" else "只包含直接归属该分类的档案",
        modifier =
            Modifier
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.block),
    )
    if (archives.isEmpty()) {
        Text(
            text = "该分类下还没有库存档案。归档后可用量、单位与状态会显示在这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    bottom = CulinarySpacing.sm,
                ),
        )
        return
    }
    archives.forEach { row ->
        ArchiveListItem(
            title = row.archive.name,
            subtitle = row.archive.englishName?.takeIf { it.isNotBlank() },
            onClick = { onOpenArchive(row.archive.id) },
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
    CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
}
