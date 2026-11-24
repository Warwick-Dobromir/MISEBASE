package com.culinary.feature.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.InventoryCategoryNode
import com.culinary.core.domain.usecase.inventory.InventoryCategoryUseCases
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryCategoryTreeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryCategoryUiState(
    val categoryId: String? = null,
    val title: String = "库存分类",
    val pathLabel: String? = null,
    val parentId: String? = null,
    val children: List<InventoryCategoryNode> = emptyList(),
    val archives: List<InventoryArchiveRow> = emptyList(),
    val searchResults: List<InventoryArchiveRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val isSavingOrder: Boolean = false,
)

@HiltViewModel
class InventoryCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listCategoryTree: ListInventoryCategoryTreeUseCase,
    private val listArchives: ListInventoryArchivesUseCase,
    private val categoryUseCases: InventoryCategoryUseCases,
) : ViewModel() {
    private val categoryId: String? = savedStateHandle.get<String>(InventoryRoutes.ARG_CATEGORY_ID)?.takeIf { it.isNotBlank() }
    private val _uiState = MutableStateFlow(InventoryCategoryUiState(categoryId = categoryId))
    val uiState: StateFlow<InventoryCategoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load(query: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.children.isEmpty() && it.archives.isEmpty(), error = null) }
            val children = when (val result = listCategoryTree(categoryId)) {
                is Outcome.Failure -> { fail(result.error); return@launch }
                is Outcome.Success -> result.value
            }
            val all = when (val result = listArchives(filter = ArchiveListFilter.ALL, query = query, categoryId = categoryId)) {
                is Outcome.Failure -> { fail(result.error); return@launch }
                is Outcome.Success -> result.value.rows
            }
            val category = categoryId?.let { findCategory(it) }
            _uiState.update {
                it.copy(
                    title = category?.name ?: "未分类",
                    pathLabel = category?.pathLabel,
                    parentId = category?.parentId,
                    children = children,
                    archives = if (query.isBlank()) all.filter { row -> row.archive.categoryId == categoryId } else emptyList(),
                    searchResults = if (query.isBlank()) emptyList() else all,
                    isLoading = false,
                    error = null,
                )
            }
        }
    }

    private fun fail(error: CulinaryError) {
        _uiState.update { it.copy(isLoading = false, error = InventoryErrorMessages.describe(error)) }
    }

    private suspend fun findCategory(id: String): ArchiveCategory? {
        val roots = when (val result = listCategoryTree(null)) {
            is Outcome.Failure -> return null
            is Outcome.Success -> result.value
        }
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.category.id == id) return node.category
            when (val result = listCategoryTree(node.category.id)) {
                is Outcome.Success -> result.value.forEach(queue::addLast)
                is Outcome.Failure -> Unit
            }
        }
        return null
    }

    fun createCategory(name: String) = mutate { categoryUseCases.create(categoryId, name) }
    fun renameCategory(id: String, name: String) = mutate { categoryUseCases.rename(id, name) }
    fun deleteCategory(id: String) = mutate { categoryUseCases.delete(id) }
    fun saveOrder(ids: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOrder = true, error = null, message = null) }
            when (val result = categoryUseCases.reorder(ids)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(isSavingOrder = false, message = "分类顺序已保存") }
                    load()
                }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isSavingOrder = false, error = categoryErrorText(result.error))
                }
            }
        }
    }

    private fun mutate(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, message = null) }
            when (val result = block()) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(message = "操作已完成") }
                    load()
                }
                is Outcome.Failure -> _uiState.update { it.copy(error = categoryErrorText(result.error)) }
            }
        }
    }
}

private fun categoryErrorText(error: CulinaryError): String =
    when (error) {
        is CulinaryError.Validation -> when {
            error.rule.contains("blank") -> "分类名称不能为空。"
            error.rule.contains("20 characters") -> "分类名称最多 20 个字。"
            error.rule.contains("child categories") -> "该分类下还有子分类，无法删除。请先处理下级分类。"
            error.rule.contains("inventory archives") -> "该分类下还有库存档案，无法删除。请先移动档案。"
            else -> InventoryErrorMessages.describe(error)
        }
        is CulinaryError.NotFound -> "分类不存在，可能已被删除。"
        else -> InventoryErrorMessages.describe(error)
    }

@Composable
fun InventoryCategoryRoute(
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onManage: () -> Unit,
    onNewArchive: () -> Unit,
    onBack: () -> Unit,
    viewModel: InventoryCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryCategoryScreen(
        state = state,
        onOpenCategory = onOpenCategory,
        onOpenArchive = onOpenArchive,
        onManage = onManage,
        onNewArchive = onNewArchive,
        onBack = onBack,
        onQuery = viewModel::load,
        onCreateCategory = viewModel::createCategory,
        onRenameCategory = viewModel::renameCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onSaveOrder = viewModel::saveOrder,
        onRetry = { viewModel.load() },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun InventoryCategoryScreen(
    state: InventoryCategoryUiState,
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onManage: () -> Unit,
    onNewArchive: () -> Unit,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveOrder: (List<String>) -> Unit,
    onRetry: () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var workingOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<InventoryCategoryNode?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<InventoryCategoryNode?>(null) }
    var visibleLimit by rememberSaveable(state.categoryId) { mutableIntStateOf(20) }
    val listState = rememberLazyListState()
    val dirty = sorting && workingOrder != savedOrder
    val children = state.children
    val displayedChildren = if (sorting) workingOrder.mapNotNull { id -> children.firstOrNull { it.category.id == id } } else children
    val currentBackLabel = if (state.parentId == null) "库存" else state.pathLabel?.substringBeforeLast(" / ")?.substringAfterLast(" / ") ?: "上一级"

    LaunchedEffect(children) {
        if (!sorting) {
            val ids = children.map { it.category.id }
            workingOrder = ids
            savedOrder = ids
        } else {
            val valid = children.map { it.category.id }.toSet()
            workingOrder = workingOrder.filter { it in valid }
            savedOrder = savedOrder.filter { it in valid }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message == "分类顺序已保存") savedOrder = workingOrder
    }
    LaunchedEffect(query, searchOpen) { visibleLimit = 20; onQuery(if (searchOpen) query else "") }
    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    BackHandler(enabled = !searchOpen && sorting) { if (dirty) showExitDialog = true else sorting = false }
    BackHandler(enabled = !searchOpen && !sorting && state.categoryId != null) {
        if (state.parentId != null) onOpenCategory(state.parentId) else onBack()
    }
    BackHandler(enabled = !searchOpen && !sorting && state.categoryId == null) { onBack() }

    Column(Modifier.fillMaxSize()) {
        state.error?.let { ErrorBanner(it, actionLabel = "重试", onAction = onRetry, modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xxs))
        }
        if (state.isLoading) {
            LoadingRows()
            return@Column
        }
        if (state.error != null && state.children.isEmpty() && state.archives.isEmpty()) return@Column

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            item(key = "context_header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    Spacer(Modifier.height(48.dp + CulinarySpacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.clickable {
                                if (sorting) { if (dirty) showExitDialog = true else sorting = false }
                                else if (state.parentId != null) onOpenCategory(state.parentId) else onBack()
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CulinaryIcons.Back, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                            Text(currentBackLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                            Icon(if (searchOpen) CulinaryIcons.Close else CulinaryIcons.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (!sorting) {
                            CulinaryTextButton(text = "＋ 新建档案", onClick = onNewArchive)
                            CulinaryTextButton(text = "＋ 新建子库", onClick = { newName = ""; showCreateDialog = true })
                        }
                    }
                    state.pathLabel?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Spacer(Modifier.height(CulinarySpacing.xs))
                    if (!sorting) Text("${children.size} 个子分类 · ${state.archives.size} 个档案",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (searchOpen) {
                        Spacer(Modifier.height(CulinarySpacing.sm))
                        CulinaryTextField(value = query, onValueChange = { query = it }, label = { Text("搜索库存档案") }, singleLine = true)
                    }
                }
            }
            if (!searchOpen || query.isBlank()) {
                if (displayedChildren.isNotEmpty()) {
                    item(key = "children_label") {
                        CategorySectionLabel("下级分类", showSort = !sorting, onSort = {
                            if (!sorting) {
                                val ids = children.map { it.category.id }
                                workingOrder = ids; savedOrder = ids; sorting = true
                            }
                        })
                    }
                    items(displayedChildren, key = { it.category.id }) { node ->
                        InventoryCategoryRow(
                            node = node,
                            sorting = sorting,
                            onOpen = { onOpenCategory(node.category.id) },
                            onRename = { renameTarget = node; renameName = node.category.name },
                            onDelete = { deleteTarget = node },
                            onMove = { delta ->
                                val index = workingOrder.indexOf(node.category.id)
                                val target = index + delta
                                if (target in workingOrder.indices) workingOrder = workingOrder.toMutableList().apply {
                                    add(target, removeAt(index))
                                }
                            },
                        )
                    }
                    if (sorting) item(key = "sort_actions") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm), horizontalArrangement = Arrangement.End) {
                            CulinaryTextButton(text = "取消", onClick = { if (dirty) showExitDialog = true else sorting = false })
                            CulinaryTextButton(text = if (state.isSavingOrder) "保存中…" else "保存排序", onClick = {
                                if (dirty && !state.isSavingOrder) onSaveOrder(workingOrder)
                            })
                        }
                    }
                }
                if (state.archives.isNotEmpty()) {
                    item(key = "archives_label") { CategorySectionLabel(if (children.isEmpty()) "档案" else "当前分类档案") }
                    items(state.archives.take(visibleLimit), key = { it.archive.id }) { row ->
                        ArchiveListItem(
                            title = row.archive.name,
                            subtitle = row.archive.englishName?.takeIf { it.isNotBlank() },
                            onClick = { onOpenArchive(row.archive.id) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    QuantityText(amount = formatAmount(row.available), unit = row.archive.stockUnit.symbol)
                                    Spacer(Modifier.height(2.dp))
                                    StatusBadge(text = InventoryText.stockStatus(row.status), tone = InventoryText.stockTone(row.status))
                                }
                            },
                        )
                    }
                    if (visibleLimit < state.archives.size) item(key = "load_more") {
                        Column(Modifier.fillMaxWidth().padding(CulinarySpacing.md)) {
                            Text("已显示 ${visibleLimit.coerceAtMost(state.archives.size)} / ${state.archives.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            CulinaryTextButton(text = "加载更多", onClick = { visibleLimit += 20 })
                        }
                    }
                }
            } else {
                val results = state.searchResults
                item(key = "search_count") { CategorySectionLabel("搜索结果 · ${results.size}") }
                items(results.take(visibleLimit), key = { "search_${it.archive.id}" }) { row ->
                    ArchiveListItem(
                        title = row.archive.name,
                        subtitle = row.archive.categoryId,
                        onClick = { onOpenArchive(row.archive.id) },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                QuantityText(amount = formatAmount(row.available), unit = row.archive.stockUnit.symbol)
                                Spacer(Modifier.height(2.dp))
                                StatusBadge(text = InventoryText.stockStatus(row.status), tone = InventoryText.stockTone(row.status))
                            }
                        },
                    )
                }
                if (visibleLimit < results.size) item { CulinaryTextButton(text = "加载更多", onClick = { visibleLimit += 20 }) }
                if (results.isEmpty()) item {
                    EmptyState(icon = CulinaryIcons.Search, title = "没有找到库存档案", description = "请检查名称或关键词。")
                }
            }
            if (!searchOpen && children.isEmpty() && state.archives.isEmpty()) item(key = "empty") {
                EmptyState(icon = CulinaryIcons.Inventory, title = "这个分类还是空的",
                    description = "可以新建库存档案，或建立下级分类。",
                    actionLabel = "＋ 新建档案", onAction = onNewArchive)
            }
            item(key = "bottom_space") { Spacer(Modifier.height(CulinarySpacing.xl)) }
        }
    }

    if (showCreateDialog) AlertDialog(
        onDismissRequest = { showCreateDialog = false },
        title = { Text("新建子库") },
        text = { CulinaryTextField(value = newName, onValueChange = { newName = it }, label = { Text("子库名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = { onCreateCategory(newName.trim()); showCreateDialog = false }) { Text("创建") } },
        dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } },
    )
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名子库") },
            text = { CulinaryTextField(value = renameName, onValueChange = { renameName = it }, label = { Text("子库名称") }, singleLine = true) },
            confirmButton = { TextButton(enabled = renameName.isNotBlank(), onClick = { onRenameCategory(target.category.id, renameName.trim()); renameTarget = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { target ->
        val canDelete = target.childCount == 0 && target.directArchiveCount == 0
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除子库？") },
            text = { Text(if (canDelete) "确定删除「${target.category.name}」？此操作不可撤销。" else "该子库仍有下级分类或关联库存档案，不能删除。") },
            confirmButton = { TextButton(enabled = canDelete, onClick = { onDeleteCategory(target.category.id); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (showExitDialog) AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = { Text("本次排序尚未保存") },
        text = { Text("当前有未保存的调整。返回后将恢复上次保存的顺序，确定返回吗？") },
        confirmButton = { TextButton(onClick = { workingOrder = savedOrder; sorting = false; showExitDialog = false }) { Text("确认") } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("取消") } },
    )
}

@Composable
private fun CategorySectionLabel(title: String, showSort: Boolean = false, onSort: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        CulinaryRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = CulinaryTextStyles.eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = CulinarySpacing.sm))
            if (showSort) IconButton(onClick = onSort, modifier = Modifier.size(32.dp)) {
                Icon(CulinaryIcons.Sort, contentDescription = "分类排序", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun InventoryCategoryRow(
    node: InventoryCategoryNode,
    sorting: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var dragDistance by remember(node.category.id) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().onSizeChanged { rowSize = it }
            .clickable(enabled = !sorting, onClick = onOpen)
            .pointerInput(sorting, node.category.id, rowSize) {
                if (sorting) detectDragGesturesAfterLongPress(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount.y
                        val threshold = (rowSize.height * .55f).coerceAtLeast(1f)
                        while (dragDistance >= threshold) {
                            onMove(1)
                            dragDistance -= threshold
                        }
                        while (dragDistance <= -threshold) {
                            onMove(-1)
                            dragDistance += threshold
                        }
                    },
                )
            }
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.category.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (sorting) Text("  编辑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable(onClick = onRename))
            }
            node.category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (node.childCount > 0) Text("${node.childCount} 个下级分类", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sorting) {
            TextButton(onClick = onRename) { Text("重命名") }
            IconButton(onClick = onDelete) { Icon(CulinaryIcons.Trash, contentDescription = "删除子库", tint = MaterialTheme.colorScheme.error) }
        } else {
            Text("${node.totalArchiveCount} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(CulinaryIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
    CulinaryRule(Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
}
