package com.culinary.feature.inventory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
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
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sign
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

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
    var searchOpen by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var query by rememberSaveable(state.categoryId) { mutableStateOf("") }
    var visibleLimit by rememberSaveable(state.categoryId, query) { mutableIntStateOf(20) }
    var sorting by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var workingOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var showSortExitDialog by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var pendingSave by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var submittedOrder by rememberSaveable(state.categoryId) { mutableStateOf(emptyList<String>()) }
    var lastSortSaveVersion by rememberSaveable(state.categoryId) { mutableIntStateOf(state.sortSaveVersion) }
    var newName by rememberSaveable(state.categoryId) { mutableStateOf("") }
    var showCreate by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var renameId by rememberSaveable(state.categoryId) { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable(state.categoryId) { mutableStateOf("") }
    var deleteId by rememberSaveable(state.categoryId) { mutableStateOf<String?>(null) }
    val listState = rememberSaveable(state.categoryId, saver = LazyListState.Saver) { LazyListState() }
    val snackbar = remember { SnackbarHostState() }
    val currentCategoryId = state.categoryId
    val sortDirty = sorting && workingOrder != savedOrder
    val backLabel = if (currentCategoryId == null) "库存" else {
        state.pathLabel?.substringBeforeLast(" / ")?.takeIf { it != state.pathLabel } ?: "库存"
    }
    val latestOrder = rememberUpdatedState(workingOrder)
    val moveCategory = rememberUpdatedState(newValue = { id: String, delta: Int ->
        val from = latestOrder.value.indexOf(id)
        val to = from + delta
        if (from >= 0 && to in latestOrder.value.indices) {
            workingOrder = latestOrder.value.toMutableList().apply { add(to, removeAt(from)) }
            true
        } else false
    })

    LaunchedEffect(state.children.map { it.category.id }) {
        val ids = state.children.map { it.category.id }
        if (!sorting) {
            workingOrder = ids
            savedOrder = ids
        } else {
            workingOrder = workingOrder.filter { it in ids } + ids.filterNot { it in workingOrder }
            savedOrder = savedOrder.filter { it in ids } + ids.filterNot { it in savedOrder }
            submittedOrder = submittedOrder.filter { it in ids } + ids.filterNot { it in submittedOrder }
        }
    }
    LaunchedEffect(state.sortSaveVersion) {
        if (state.sortSaveVersion > lastSortSaveVersion) {
            lastSortSaveVersion = state.sortSaveVersion
            if (sorting && pendingSave) {
                savedOrder = submittedOrder
                pendingSave = false
                snackbar.showSnackbar("已保存")
            }
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.error) {
        state.error?.let {
            if (pendingSave) pendingSave = false
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(query) { visibleLimit = 20 }
    fun beginSort() {
        if (!sorting) {
            val ids = state.children.map { it.category.id }
            workingOrder = ids
            savedOrder = ids
            sorting = true
        }
    }
    fun finishSort() {
        workingOrder = savedOrder
        sorting = false
        pendingSave = false
        showSortExitDialog = false
    }
    fun requestSortExit() {
        if (sortDirty) showSortExitDialog = true else finishSort()
    }
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
        onSearch("")
    }
    BackHandler(enabled = !searchOpen && sorting) { requestSortExit() }
    BackHandler(enabled = !searchOpen && !sorting) { onBack() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            if (state.isLoading) {
                LoadingRows()
            } else if (state.error != null && state.children.isEmpty() && state.archives.isEmpty()) {
                ErrorBanner(
                    message = state.error,
                    actionLabel = "重试",
                    onAction = onRetry,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )
            } else if (searchOpen && query.isNotBlank()) {
                if (state.searchResults.isEmpty() && !state.isSearching) {
                    EmptyState(
                        icon = CulinaryIcons.Search,
                        title = "没有找到档案",
                        description = "请调整关键词，或检查下级分类中的档案名称。",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { InventoryDirectorySectionLabel("搜索结果 · " + state.searchResults.size) }
                        items(state.searchResults.take(visibleLimit), key = { "search-" + it.archive.id }) { row ->
                            InventoryStockArchiveRow(row) { onOpenArchive(row.archive.id) }
                        }
                        if (visibleLimit < state.searchResults.size) {
                            item {
                                InventoryLoadMore(
                                    shown = visibleLimit.coerceAtMost(state.searchResults.size),
                                    total = state.searchResults.size,
                                    onLoadMore = { visibleLimit += 20 },
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    item(key = "archive-context-header") {
                        InventoryArchiveContextHeader(
                            title = state.title,
                            pathLabel = state.pathLabel,
                            childCount = state.children.size,
                            archiveCount = state.archives.size,
                            onAddArchive = onNewArchive,
                            onCreateSubcategory = { newName = ""; showCreate = true },
                            sorting = sorting,
                        )
                    }
                    val orderedChildren = if (sorting) {
                        workingOrder.mapNotNull { id -> state.children.firstOrNull { it.category.id == id } }
                    } else state.children
                    if (orderedChildren.isNotEmpty()) {
                        if (!sorting) {
                            item {
                                InventoryDirectorySectionLabel(
                                    title = "下级分类",
                                    showSort = true,
                                    onSort = ::beginSort,
                                )
                            }
                        }
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
                                    from >= 0 && (from + delta) in latestOrder.value.indices
                                },
                                onMove = { delta -> moveCategory.value(node.category.id, delta) },
                            )
                        }
                    }
                    if (!sorting && state.archives.isNotEmpty()) {
                        item {
                            InventoryDirectorySectionLabel(
                                if (state.children.isEmpty()) "档案" else "当前分类档案",
                            )
                        }
                        items(state.archives.take(visibleLimit), key = { "archive-" + it.archive.id }) { row ->
                            InventoryStockArchiveRow(row) { onOpenArchive(row.archive.id) }
                        }
                        if (visibleLimit < state.archives.size) {
                            item {
                                InventoryLoadMore(
                                    shown = visibleLimit.coerceAtMost(state.archives.size),
                                    total = state.archives.size,
                                    onLoadMore = { visibleLimit += 20 },
                                )
                            }
                        }
                    }
                    if (state.children.isEmpty() && state.archives.isEmpty()) {
                        item {
                            EmptyState(
                                icon = CulinaryIcons.Library,
                                title = "这个分类还是空的",
                                description = "可以新建子库，或直接创建库存档案。",
                                actionLabel = "＋ 新建档案",
                                onAction = onNewArchive,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(CulinarySpacing.xl)) }
                }
            }
            if (sorting) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().height(68.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.weight(1f).height(68.dp)
                                .clickable(enabled = sortDirty && !pendingSave) {
                                    submittedOrder = workingOrder
                                    pendingSave = true
                                    onSaveOrder(workingOrder)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("保存", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                        Box(
                            Modifier.weight(1f).height(68.dp).clickable { requestSortExit() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("取消", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        InventoryArchiveBackFloatButton(
            label = backLabel,
            onClick = { if (sorting) requestSortExit() else onBack() },
            modifier = Modifier.align(Alignment.TopStart)
                .padding(top = CulinarySpacing.xs, start = CulinarySpacing.pageHorizontal),
        )
        InventoryArchiveSearchIsland(
            expanded = searchOpen,
            query = query,
            onQueryChange = { query = it; onSearch(it) },
            onExpand = { searchOpen = true },
            onCollapse = { searchOpen = false; query = ""; onSearch("") },
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = CulinarySpacing.xs, end = CulinarySpacing.pageHorizontal)
                .widthIn(max = 292.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = if (sorting) 76.dp else 12.dp)
                .navigationBarsPadding(),
        )
    }

    if (showSortExitDialog) {
        AlertDialog(
            onDismissRequest = { showSortExitDialog = false },
            title = { Text("本次排序尚未保存") },
            text = { Text("当前还有未保存的调整，返回后将恢复上次保存的顺序。确定返回吗？") },
            confirmButton = { TextButton(onClick = ::finishSort) { Text("确认") } },
            dismissButton = { TextButton(onClick = { showSortExitDialog = false }) { Text("取消") } },
        )
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建子库") },
            text = {
                Column {
                    Text(
                        "所属位置：" + (state.pathLabel ?: state.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(CulinarySpacing.sm))
                    CulinaryTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("子库名称") },
                        singleLine = true,
                    )
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
            text = {
                CulinaryTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("子库名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.isNotBlank(),
                    onClick = { onRenameCategory(id, renameName.trim()); renameId = null },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("取消") } },
        )
    }
    deleteId?.let { id ->
        val target = state.children.firstOrNull { it.category.id == id }
        val canDelete = target != null && target.childCount == 0 && target.directArchiveCount == 0
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("删除子库？") },
            text = {
                Text(
                    if (canDelete) "确定删除「" + target!!.category.name + "」？此操作不可撤销。"
                    else "该子库仍包含下级分类或档案。为保护库存数据，当前不允许删除。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canDelete,
                    onClick = { onDeleteCategory(id); deleteId = null },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun InventoryArchiveBackFloatButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(40.dp).clickable(onClick = onClick)
            .padding(horizontal = CulinarySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CulinaryIcons.Back, contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CulinarySpacing.xxs))
    }
}

@Composable
private fun InventoryArchiveSearchIsland(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(if (expanded) 292.dp else 48.dp).height(48.dp).animateContentSize(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = if (expanded) CulinarySpacing.sm else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                CulinaryIcons.Search,
                contentDescription = if (expanded) null else "搜索库存档案",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = if (expanded) Modifier.size(20.dp) else Modifier.fillMaxSize()
                    .clickable(onClick = onExpand).padding(14.dp),
            )
            if (expanded) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(Modifier.padding(start = CulinarySpacing.xs)) {
                            if (query.isEmpty()) Text(
                                "搜索库存档案",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            innerTextField()
                        }
                    },
                )
                Icon(
                    CulinaryIcons.Close,
                    contentDescription = "关闭搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp).clickable(onClick = onCollapse).padding(9.dp),
                )
            }
        }
    }
}

@Composable
private fun InventoryArchiveContextHeader(
    title: String,
    pathLabel: String?,
    childCount: Int,
    archiveCount: Int,
    onAddArchive: () -> Unit,
    onCreateSubcategory: () -> Unit,
    sorting: Boolean,
) {
    Column(Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        Spacer(Modifier.height(48.dp + CulinarySpacing.xs))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CulinaryTextButton(text = "＋ 新建档案", onClick = onAddArchive)
            CulinaryTextButton(text = "＋ 新建子库", onClick = onCreateSubcategory)
        }
        pathLabel?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(CulinarySpacing.xs))
        if (!sorting) {
            Text(
                childCount.toString() + " 个子分类 · " + archiveCount + " 个档案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InventoryLoadMore(shown: Int, total: Int, onLoadMore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(CulinarySpacing.md)) {
        Text(shown.toString() + " / " + total,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CulinaryTextButton(text = "加载更多", onClick = onLoadMore)
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

private data class InventoryDragOffsetState(
    val offsetPx: Float,
    val edgeOverdragPx: Float = 0f,
)

private fun inventoryEdgeResistanceOffset(overdragPx: Float, maxOffsetPx: Float): Float {
    val limit = maxOffsetPx.coerceAtLeast(1f)
    val maxRaw = limit * 5f
    val raw = overdragPx.coerceIn(-maxRaw, maxRaw)
    val progress = (kotlin.math.abs(raw) / maxRaw).coerceIn(0f, 1f)
    return limit * progress.pow(1.15f) * raw.sign
}

private fun inventoryDragOffsetAfterDelta(
    currentOffsetPx: Float,
    currentEdgeOverdragPx: Float,
    deltaPx: Float,
    rowHeightPx: Float,
    maxEdgeOffsetPx: Float,
    canMove: (Int) -> Boolean,
    move: (Int) -> Boolean,
): InventoryDragOffsetState {
    val threshold = rowHeightPx.coerceAtLeast(1f)
    var offset = currentOffsetPx + deltaPx
    if (currentEdgeOverdragPx != 0f) {
        val updatedRaw = currentEdgeOverdragPx + deltaPx
        if (updatedRaw != 0f && updatedRaw.sign == currentEdgeOverdragPx.sign) {
            return InventoryDragOffsetState(
                offsetPx = inventoryEdgeResistanceOffset(updatedRaw, maxEdgeOffsetPx),
                edgeOverdragPx = updatedRaw,
            )
        }
        offset = updatedRaw
    }
    while (offset >= threshold) {
        if (!canMove(1) || !move(1)) {
            return InventoryDragOffsetState(inventoryEdgeResistanceOffset(offset, maxEdgeOffsetPx), offset)
        }
        offset -= threshold
    }
    while (offset <= -threshold) {
        if (!canMove(-1) || !move(-1)) {
            return InventoryDragOffsetState(inventoryEdgeResistanceOffset(offset, maxEdgeOffsetPx), offset)
        }
        offset += threshold
    }
    if ((offset < 0f && !canMove(-1)) || (offset > 0f && !canMove(1))) {
        return InventoryDragOffsetState(inventoryEdgeResistanceOffset(offset, maxEdgeOffsetPx), offset)
    }
    return InventoryDragOffsetState(offsetPx = offset)
}

/** Inventory row uses the same geometry, drag resistance and edge auto-scroll as the kitchen archive row. */
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
    var edgeOverdragY by remember(node.category.id) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(node.category.id) { mutableIntStateOf(0) }
    val maxEdgeOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)
    val autoScrollEdgePx = with(LocalDensity.current) { 144.dp.toPx() }

    LaunchedEffect(dragging, sorting, autoScrollEdgePx) {
        var previousFrameNanos = 0L
        while (dragging && sorting) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds = if (previousFrameNanos == 0L) 0f else
                ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrameNanos = frameNanos
            if (frameSeconds > 0f) {
                val viewport = listState.layoutInfo
                val draggedCenter = viewport.visibleItemsInfo
                    .firstOrNull { it.key == node.category.id }
                    ?.let { it.offset + rowHeightPx / 2f + dragOffsetY }
                if (draggedCenter != null) {
                    val topProgress = (
                        (autoScrollEdgePx - (draggedCenter - viewport.viewportStartOffset)) /
                            autoScrollEdgePx
                        ).coerceIn(0f, 1f)
                    val bottomProgress = (
                        (autoScrollEdgePx - (viewport.viewportEndOffset - draggedCenter)) /
                            autoScrollEdgePx
                        ).coerceIn(0f, 1f)
                    val direction: Float
                    val progress: Float
                    when {
                        topProgress > 0f && topProgress >= bottomProgress -> {
                            direction = -1f
                            progress = topProgress
                        }
                        bottomProgress > 0f -> {
                            direction = 1f
                            progress = bottomProgress
                        }
                        else -> {
                            direction = 0f
                            progress = 0f
                        }
                    }
                    if (direction != 0f) {
                        val speed = 320f + 2_080f * progress * progress
                        val consumed = listState.scrollBy(direction * speed * frameSeconds)
                        dragOffsetY += consumed
                        if (edgeOverdragY != 0f) {
                            edgeOverdragY += consumed
                            dragOffsetY = inventoryEdgeResistanceOffset(edgeOverdragY, maxEdgeOffsetPx)
                        }
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
                val selectedScale = sorting && dragging
                val scale = if (selectedScale) 1.045f else 1f
                scaleX = scale
                scaleY = scale
                translationX = 0f
                translationY = if (selectedScale) dragOffsetY else 0f
            }
            .shadow(
                elevation = if (sorting && dragging) 7.dp else 0.dp,
                shape = RectangleShape,
            )
            .then(
                if (sorting) {
                    Modifier.pointerInput(node.category.id, sorting) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                dragOffsetY = 0f
                                edgeOverdragY = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                dragOffsetY = 0f
                                edgeOverdragY = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffsetY = 0f
                                edgeOverdragY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val nextDrag = inventoryDragOffsetAfterDelta(
                                    currentOffsetPx = dragOffsetY,
                                    currentEdgeOverdragPx = edgeOverdragY,
                                    deltaPx = amount.y,
                                    rowHeightPx = rowHeightPx.toFloat(),
                                    maxEdgeOffsetPx = maxEdgeOffsetPx,
                                    canMove = currentCanMove,
                                    move = currentOnMove,
                                )
                                dragOffsetY = nextDrag.offsetPx
                                edgeOverdragY = nextDrag.edgeOverdragPx
                            },
                        )
                    }
                } else {
                    Modifier.pointerInput(node.category.id, sorting) {
                        detectTapGestures(onTap = { onClick() })
                    }
                },
            )
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                modifier = if (sorting) Modifier.clickable(onClick = onRename) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    node.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (sorting) {
                    Text(
                        "✎",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = CulinarySpacing.xs).graphicsLayer { scaleX = -1f },
                    )
                }
            }
            node.category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                node.childCount.toString() + " 个子分类 · " + node.directArchiveCount + " 个档案",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (sorting) {
            Box(
                Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
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
