package com.culinary.feature.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    val children: List<InventoryCategoryNode> = emptyList(),
    val archives: List<InventoryArchiveRow> = emptyList(),
    val searchResults: List<InventoryArchiveRow> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sortSaveVersion: Int = 0,
)

@HiltViewModel
class InventoryCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listCategoryTree: ListInventoryCategoryTreeUseCase,
    private val listArchives: ListInventoryArchivesUseCase,
    private val categoryUseCases: InventoryCategoryUseCases,
) : ViewModel() {
    private val categoryId: String? = savedStateHandle.get<String>(InventoryRoutes.ARG_CATEGORY_ID)
        ?.takeIf { it.isNotBlank() }
    private val _uiState = MutableStateFlow(InventoryCategoryUiState(categoryId = categoryId))
    val uiState: StateFlow<InventoryCategoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.children.isEmpty() && it.archives.isEmpty(), error = null) }
            when (val result = read()) {
                is Outcome.Success -> _uiState.value = result.value.copy(
                    searchQuery = _uiState.value.searchQuery,
                    searchResults = _uiState.value.searchResults,
                    isSearching = _uiState.value.isSearching,
                    sortSaveVersion = _uiState.value.sortSaveVersion,
                )
                is Outcome.Failure -> _uiState.update {
                    it.copy(isLoading = false, error = InventoryErrorMessages.describe(result.error))
                }
            }
        }
    }

    private suspend fun read(): Outcome<InventoryCategoryUiState> {
        val children = when (val result = listCategoryTree(categoryId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val archives = when (val result = listArchives(filter = ArchiveListFilter.ALL, categoryId = categoryId)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val direct = archives.rows.filter { it.archive.categoryId == categoryId }
        val category = categoryId?.let { findCategory(it) }
        return Outcome.success(InventoryCategoryUiState(
            categoryId = categoryId,
            title = category?.name ?: "未分类",
            pathLabel = category?.pathLabel,
            children = children,
            archives = direct,
            isLoading = false,
        ))
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
            when (val nested = listCategoryTree(node.category.id)) {
                is Outcome.Success -> nested.value.forEach(queue::addLast)
                is Outcome.Failure -> Unit
            }
        }
        return null
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank(), error = null) }
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
                return@launch
            }
            when (val result = listArchives(filter = ArchiveListFilter.ALL, query = query, categoryId = categoryId)) {
                is Outcome.Success -> _uiState.update { it.copy(searchResults = result.value.rows, isSearching = false) }
                is Outcome.Failure -> _uiState.update {
                    it.copy(isSearching = false, error = InventoryErrorMessages.describe(result.error))
                }
            }
        }
    }

    fun createCategory(parentId: String?, name: String) {
        viewModelScope.launch {
            when (val result = categoryUseCases.create(parentId, name)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(message = "已新建子库「${result.value.name}」") }
                    load()
                }
                is Outcome.Failure -> _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
            }
        }
    }

    fun renameCategory(id: String, name: String) {
        viewModelScope.launch {
            when (val result = categoryUseCases.rename(id, name)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(message = "已重命名为「${result.value.name}」") }
                    load()
                }
                is Outcome.Failure -> _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            when (val result = categoryUseCases.delete(id)) {
                is Outcome.Success -> { _uiState.update { it.copy(message = "已删除子库") }; load() }
                is Outcome.Failure -> _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
            }
        }
    }

    fun saveOrder(ids: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            when (val result = categoryUseCases.reorder(ids)) {
                is Outcome.Success -> {
                    _uiState.update { it.copy(message = "分类顺序已保存", sortSaveVersion = it.sortSaveVersion + 1) }
                    load()
                }
                is Outcome.Failure -> _uiState.update { it.copy(error = InventoryErrorMessages.describe(result.error)) }
            }
        }
    }
}

@Composable
fun InventoryCategoryRoute(
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onNewArchive: () -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: InventoryCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryCategoryScreen(
        state = state,
        onOpenCategory = onOpenCategory,
        onOpenArchive = onOpenArchive,
        onNewArchive = onNewArchive,
        onBack = onBack,
        onCreateCategory = viewModel::createCategory,
        onRenameCategory = viewModel::renameCategory,
        onDeleteCategory = viewModel::deleteCategory,
        onSaveOrder = viewModel::saveOrder,
        onSearch = viewModel::search,
        onRetry = viewModel::load,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun InventoryCategoryScreen(
    state: InventoryCategoryUiState,
    onOpenCategory: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onNewArchive: () -> Unit,
    onBack: () -> Unit,
    onCreateCategory: (String?, String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onSaveOrder: (List<String>) -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var visibleLimit by rememberSaveable(state.categoryId, query) { mutableIntStateOf(20) }
    var sorting by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var workingOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var showExitSort by rememberSaveable { mutableStateOf(false) }
    var pendingSortSave by rememberSaveable { mutableStateOf(false) }
    var lastSortSaveVersion by rememberSaveable { mutableIntStateOf(state.sortSaveVersion) }
    var newName by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberSaveable(state.categoryId, saver = LazyListState.Saver) { LazyListState() }
    val snackbar = remember { SnackbarHostState() }
    val currentCategoryId = state.categoryId
    val sortDirty = sorting && workingOrder != savedOrder
    val scope = rememberCoroutineScope()
    val latestOrder = rememberUpdatedState(workingOrder)
    val moveCategory = rememberUpdatedState(newValue = { id: String, delta: Int ->
        val from = latestOrder.value.indexOf(id)
        val to = from + delta
        if (from >= 0 && to in latestOrder.value.indices) {
            workingOrder = latestOrder.value.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    })

    LaunchedEffect(state.children.map { it.category.id }) {
        val ids = state.children.map { it.category.id }
        if (!sorting) { workingOrder = ids; savedOrder = ids }
        else {
            workingOrder = workingOrder.filter { it in ids } + ids.filterNot { it in workingOrder }
            savedOrder = savedOrder.filter { it in ids } + ids.filterNot { it in savedOrder }
        }
    }
    LaunchedEffect(state.sortSaveVersion) {
        if (state.sortSaveVersion > lastSortSaveVersion) {
            lastSortSaveVersion = state.sortSaveVersion
            if (pendingSortSave) {
                savedOrder = workingOrder
                pendingSortSave = false
                sorting = false
                snackbar.showSnackbar("排序已保存")
            }
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.error) {
        state.error?.let {
            if (pendingSortSave) pendingSortSave = false
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(query) { visibleLimit = 20 }
    fun beginSort() {
        workingOrder = state.children.map { it.category.id }
        savedOrder = workingOrder
        sorting = true
    }
    fun finishSort() {
        workingOrder = savedOrder
        sorting = false
        showExitSort = false
    }
    fun requestSortExit() {
        if (sortDirty) showExitSort = true else finishSort()
    }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && sorting) { requestSortExit() }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId != null) { onBack() }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId == null) { onBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.error != null) {
                ErrorBanner(
                    message = state.error,
                    actionLabel = "重试",
                    onAction = onRetry,
                    modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                )
            }
            if (state.isLoading) {
                LoadingRows()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                ) {
                    item(key = "context-header") {
                        Column(Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                            Spacer(Modifier.height(48.dp + CulinarySpacing.xs))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.weight(1f).clickable { if (sorting) requestSortExit() else onBack() },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(CulinaryIcons.Back, contentDescription = "返回", modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (currentCategoryId == null) "库存" else "上级分类",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = CulinarySpacing.xxs),
                                    )
                                }
                                TextButton(onClick = { searchOpen = !searchOpen; if (searchOpen) query = "" }) {
                                    Text(if (searchOpen) "关闭搜索" else "搜索")
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    state.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CulinaryTextButton(text = "＋ 新建档案", onClick = onNewArchive)
                            }
                            state.pathLabel?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(CulinarySpacing.xs))
                            Text(
                                "${state.children.size} 个子分类 · ${state.archives.size} 个档案",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searchOpen) {
                                Spacer(Modifier.height(CulinarySpacing.sm))
                                TextField(
                                    value = query,
                                    onValueChange = { query = it; onSearch(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("搜索当前分类及下级档案") },
                                    singleLine = true,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CulinaryTextButton(text = "＋ 新建子库", onClick = { newName = ""; showCreate = true })
                                Spacer(Modifier.weight(1f))
                                if (state.children.isNotEmpty() && !sorting) {
                                    TextButton(onClick = ::beginSort) { Text("分类排序") }
                                }
                            }
                        }
                    }
                    if (searchOpen && query.isNotBlank()) {
                        item(key = "search-heading") { InventoryDirectorySectionLabel("搜索结果") }
                        items(state.searchResults.take(visibleLimit), key = { "search-${it.archive.id}" }) { row ->
                            InventoryStockArchiveRow(row) { onOpenArchive(row.archive.id) }
                        }
                        if (state.searchResults.size > visibleLimit) {
                            item(key = "search-more") {
                                TextButton(onClick = { visibleLimit += 20 }, modifier = Modifier.fillMaxWidth()) {
                                    Text("加载更多（已显示 ${visibleLimit.coerceAtMost(state.searchResults.size)} / ${state.searchResults.size}）")
                                }
                            }
                        }
                        if (state.searchResults.isEmpty() && !state.isSearching) {
                            item(key = "search-empty") {
                                EmptyState(
                                    icon = CulinaryIcons.Search,
                                    title = "没有找到档案",
                                    description = "请调整关键词，或检查下级分类中的档案名称。",
                                )
                            }
                        }
                    } else {
                        if (state.children.isNotEmpty()) {
                            item(key = "child-heading") {
                                InventoryDirectorySectionLabel("下级分类", showSort = !sorting, onSort = ::beginSort)
                            }
                            val orderedChildren = if (sorting) {
                                workingOrder.mapNotNull { id -> state.children.firstOrNull { it.category.id == id } }
                            } else state.children
                            items(orderedChildren, key = { it.category.id }) { node ->
                                InventoryCategoryRow(
                                    node = node,
                                    sorting = sorting,
                                    listState = listState,
                                    onClick = { onOpenCategory(node.category.id) },
                                    onRename = {
                                        renameId = node.category.id
                                        renameName = node.category.name
                                    },
                                    onDelete = { deleteId = node.category.id },
                                    canMove = { delta ->
                                        val from = latestOrder.value.indexOf(node.category.id)
                                        from >= 0 && from + delta in latestOrder.value.indices
                                    },
                                    onMove = { delta -> moveCategory.value(node.category.id, delta) },
                                )
                            }
                        }
                        if (state.archives.isNotEmpty() || state.children.isEmpty()) {
                            item(key = "archive-heading") {
                                InventoryDirectorySectionLabel(
                                    if (state.children.isEmpty()) "档案" else "当前分类档案",
                                )
                            }
                            items(state.archives.take(visibleLimit), key = { "archive-${it.archive.id}" }) { row ->
                                InventoryStockArchiveRow(row) { onOpenArchive(row.archive.id) }
                            }
                            if (state.archives.size > visibleLimit) {
                                item(key = "archive-more") {
                                    TextButton(onClick = { visibleLimit += 20 }, modifier = Modifier.fillMaxWidth()) {
                                        Text("加载更多（已显示 ${visibleLimit.coerceAtMost(state.archives.size)} / ${state.archives.size}）")
                                    }
                                }
                            }
                        }
                        if (state.children.isEmpty() && state.archives.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    icon = CulinaryIcons.Inventory,
                                    title = "这个分类还是空的",
                                    description = "可以新建子库，或直接创建库存档案。",
                                    actionLabel = "＋ 新建档案",
                                    onAction = onNewArchive,
                                )
                            }
                        }
                    }
                    item(key = "bottom-space") { Spacer(Modifier.height(CulinarySpacing.xl)) }
                }
            }
            if (sorting) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    CulinaryRule()
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TextButton(
                                enabled = sortDirty && !pendingSortSave,
                                onClick = {
                                    pendingSortSave = true
                                    onSaveOrder(workingOrder)
                                },
                            ) { Text("保存排序") }
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TextButton(onClick = ::requestSortExit) { Text("取消") }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建子库") },
            text = {
                Column {
                    Text(
                        "所属位置：${state.pathLabel ?: state.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("子库名称") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { onCreateCategory(currentCategoryId, newName.trim()); showCreate = false },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("重命名子库") },
            text = { TextField(value = renameName, onValueChange = { renameName = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = renameName.isNotBlank(), onClick = {
                    onRenameCategory(id, renameName.trim()); renameId = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("取消") } },
        )
    }
    deleteId?.let { id ->
        val target = state.children.firstOrNull { it.category.id == id }
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("删除子库？") },
            text = {
                Text(
                    if (target != null && target.childCount == 0 && target.directArchiveCount == 0) {
                        "确定删除「${target.category.name}」？此操作不可撤销。"
                    } else {
                        "该子库仍包含下级分类或档案。为保护库存数据，当前不允许删除。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = target != null && target.childCount == 0 && target.directArchiveCount == 0,
                    onClick = { onDeleteCategory(id); deleteId = null },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
        )
    }
    if (showExitSort) {
        AlertDialog(
            onDismissRequest = { showExitSort = false },
            title = { Text("本次排序尚未保存") },
            text = { Text("返回后将恢复最近一次保存的顺序。确定退出吗？") },
            confirmButton = { TextButton(onClick = ::finishSort) { Text("确认") } },
            dismissButton = { TextButton(onClick = { showExitSort = false }) { Text("继续调整") } },
        )
    }
}

@Composable
private fun InventoryDirectorySectionLabel(
    title: String,
    showSort: Boolean = false,
    onSort: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = CulinarySpacing.sm),
            )
            if (showSort) {
                Box(
                    modifier = Modifier.size(32.dp).clickable(onClick = onSort),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        CulinaryIcons.Sort,
                        contentDescription = "分类排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** Inventory counterpart of the kitchen archive CategoryRow; interaction and geometry stay aligned. */
@Composable
private fun InventoryCategoryRow(
    node: InventoryCategoryNode,
    sorting: Boolean,
    listState: LazyListState,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    canMove: (Int) -> Boolean,
    onMove: (Int) -> Boolean,
) {
    var dragging by remember(node.category.id) { mutableStateOf(false) }
    var dragOffsetY by remember(node.category.id) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(node.category.id) { mutableIntStateOf(0) }
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)
    val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { 144.dp.toPx() }

    LaunchedEffect(dragging, sorting, edgePx) {
        var previousFrame = 0L
        while (dragging && sorting) {
            val frame = androidx.compose.runtime.withFrameNanos { it }
            val seconds = if (previousFrame == 0L) 0f else
                ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrame = frame
            if (seconds > 0f) {
                val viewport = listState.layoutInfo
                val center = viewport.visibleItemsInfo.firstOrNull { it.key == node.category.id }
                    ?.let { it.offset + rowHeightPx / 2f + dragOffsetY }
                if (center != null) {
                    val top = ((edgePx - (center - viewport.viewportStartOffset)) / edgePx).coerceIn(0f, 1f)
                    val bottom = ((edgePx - (viewport.viewportEndOffset - center)) / edgePx).coerceIn(0f, 1f)
                    val direction = when {
                        top > 0f && top >= bottom -> -1f
                        bottom > 0f -> 1f
                        else -> 0f
                    }
                    if (direction != 0f) {
                        val progress = maxOf(top, bottom)
                        val speed = 320f + 2_080f * progress * progress
                        val consumed = listState.scrollBy(direction * speed * seconds)
                        dragOffsetY += consumed
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }
            .graphicsLayer {
                val active = sorting && dragging
                scaleX = if (active) 1.045f else 1f
                scaleY = if (active) 1.045f else 1f
                translationX = 0f
                translationY = if (active) dragOffsetY else 0f
            }
            .then(
                if (sorting) Modifier.pointerInput(node.category.id, sorting) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = true; dragOffsetY = 0f },
                        onDragEnd = { dragging = false; dragOffsetY = 0f },
                        onDragCancel = { dragging = false; dragOffsetY = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffsetY += amount.y
                            val threshold = rowHeightPx.coerceAtLeast(1).toFloat()
                            while (dragOffsetY >= threshold && currentCanMove(1)) {
                                if (currentOnMove(1)) dragOffsetY -= threshold else break
                            }
                            while (dragOffsetY <= -threshold && currentCanMove(-1)) {
                                if (currentOnMove(-1)) dragOffsetY += threshold else break
                            }
                        },
                    )
                } else Modifier.pointerInput(node.category.id, sorting) {
                    detectDragGesturesAfterLongPress(onDrag = { change, _ -> change.consume() })
                }
            )
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = if (sorting) Modifier.clickable(onClick = onRename) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sorting) {
                    Text(
                        text = "✎",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = CulinarySpacing.xs).graphicsLayer { scaleX = -1f },
                    )
                }
            }
            node.category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${node.childCount} 个子分类 · ${node.directArchiveCount} 个档案",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sorting) {
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onDelete).padding(CulinarySpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CulinaryIcons.Trash, contentDescription = "删除子库",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        } else {
            Text(node.totalArchiveCount.toString(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(CulinaryIcons.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).padding(start = 4.dp))
        }
    }
    CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
}

@Composable
private fun InventoryStockArchiveRow(row: InventoryArchiveRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.archive.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            row.archive.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            QuantityText(amount = formatAmount(row.available), unit = row.archive.stockUnit.symbol)
            Spacer(Modifier.height(2.dp))
            StatusBadge(text = InventoryText.stockStatus(row.status), tone = InventoryText.stockTone(row.status))
        }
        Icon(CulinaryIcons.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = CulinarySpacing.xs).size(16.dp))
    }
    CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
}
