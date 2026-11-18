package com.culinary.kitchen.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/** 六库统一目录路由：分类钻取、分类/档案分区、分页和单库搜索。 */
@Composable
fun ArchiveLibraryRoute(
    section: KitchenArchiveSection,
    onAdd: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    onBack: () -> Unit = {},
    onSortingChanged: (Boolean) -> Unit = {},
    cancelSortRequestVersion: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: ArchiveLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArchiveLibraryScreen(
        section = section,
        state = state,
        onAdd = onAdd,
        onOpenEntry = onOpenEntry,
        onBack = onBack,
        onSortingChanged = onSortingChanged,
        cancelSortRequestVersion = cancelSortRequestVersion,
        onCreateSubcategory = viewModel::createSubcategory,
        onRenameSubcategory = viewModel::renameSubcategory,
        onDeleteSubcategory = viewModel::deleteSubcategory,
        onReorderSubcategories = viewModel::reorderSubcategories,
        modifier = modifier,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // 目录状态机：搜索、分类、档案、空态共用同一屏
@Composable
internal fun ArchiveLibraryScreen(
    section: KitchenArchiveSection,
    state: ArchiveLibraryUiState,
    onAdd: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    onBack: () -> Unit = {},
    onSortingChanged: (Boolean) -> Unit = {},
    cancelSortRequestVersion: Int = 0,
    onCreateSubcategory: (String?, String) -> Unit = { _, _ -> },
    onRenameSubcategory: (String, String) -> Unit = { _, _ -> },
    onDeleteSubcategory: (String) -> Unit = {},
    onReorderSubcategories: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var currentCategoryId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable(section) { mutableStateOf(false) }
    var query by rememberSaveable(section) { mutableStateOf("") }
    var visibleLimit by rememberSaveable(section) { mutableIntStateOf(20) }
    var newSubcategoryParentId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var showNewSubcategoryDialog by rememberSaveable(section) { mutableStateOf(false) }
    var newSubcategoryName by rememberSaveable(section) { mutableStateOf("") }
    var renameTargetId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable(section) { mutableStateOf("") }
    var deleteTargetId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var deleteModeCategoryId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var sorting by rememberSaveable(section) { mutableStateOf(false) }
    val categoryListState = rememberLazyListState()
    var workingOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var pendingSave by rememberSaveable(section, currentCategoryId) { mutableStateOf(false) }
    var submittedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var showSortExitDialog by rememberSaveable(section) { mutableStateOf(false) }
    var lastSortSaveVersion by rememberSaveable(section) { mutableIntStateOf(state.categorySortSaveVersion) }
    val sortSnackbarHostState = remember { SnackbarHostState() }

    val categories = state.categories
    val entries = state.entries.filter { it.kind == section.kind }
    val currentCategory = categories.firstOrNull { it.id == currentCategoryId }
    // 测试分类在数据库启动清理后不应再出现；页面同时不展示任何遗留测试节点。
    val children = categories.filter { it.parentId == currentCategoryId && !it.isTest }
    val formalChildren = children
    val displayedChildren = if (sorting && workingOrder.isNotEmpty()) {
        workingOrder.mapNotNull { id -> children.firstOrNull { it.id == id } }
    } else children
    val sortDirty = sorting && workingOrder != savedOrder
    val directEntries = entries.filter { entry -> entry.categoryId == currentCategoryId }
    val searchResults =
        if (query.isBlank()) {
            emptyList()
        } else {
            entries.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (it.subtitle?.contains(query, ignoreCase = true) == true) ||
                    it.searchText.contains(query, ignoreCase = true) ||
                    it.categoryPathLabel.contains(query, ignoreCase = true)
            }
        }
    val showingSearch = searchOpen && query.isNotBlank()

    LaunchedEffect(categories, currentCategoryId) {
        if (currentCategoryId != null && categories.none { it.id == currentCategoryId }) {
            currentCategoryId = null
        }
    }
    LaunchedEffect(currentCategoryId, query, searchOpen) { visibleLimit = 20 }
    LaunchedEffect(state.categorySortSaveVersion) {
        if (state.categorySortSaveVersion > lastSortSaveVersion) {
            lastSortSaveVersion = state.categorySortSaveVersion
            if (sorting && pendingSave) {
                savedOrder = submittedOrder
                pendingSave = false
                sortSnackbarHostState.showSnackbar("已保存")
            }
        }
    }
    LaunchedEffect(state.categoryError) {
        if (state.categoryError != null && pendingSave) pendingSave = false
    }
    LaunchedEffect(children.map { it.id }.toSet()) {
        if (sorting) {
            val validIds = children.map { it.id }.toSet()
            workingOrder = workingOrder.filter { it in validIds }
            savedOrder = savedOrder.filter { it in validIds }
            submittedOrder = submittedOrder.filter { it in validIds }
        }
    }
    fun beginSorting() {
        if (!sorting) {
            val ids = children.map { it.id }
            workingOrder = ids
            savedOrder = ids
            sorting = true
            onSortingChanged(true)
        }
    }
    fun finishSorting() {
        workingOrder = savedOrder
        sorting = false
        onSortingChanged(false)
        pendingSave = false
        showSortExitDialog = false
    }
    fun requestSortReturn() {
        // 排序模式退出始终经过确认；无脏修改时确认后恢复最近一次保存顺序。
        showSortExitDialog = true
    }

    LaunchedEffect(cancelSortRequestVersion) {
        if (cancelSortRequestVersion > 0 && sorting) requestSortReturn()
    }

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && sorting) { requestSortReturn() }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId != null) {
        currentCategoryId = currentCategory?.parentId
    }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId == null) { onBack() }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        state.categoryError?.let { message ->
            ErrorBanner(
                message = message,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        if (!sorting) state.categoryMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier.padding(
                        horizontal = CulinarySpacing.pageHorizontal,
                        vertical = CulinarySpacing.xxs,
                    ),
            )
        }
        if (state.isLoading) {
            LoadingRows()
            return@Column
        }
        if (state.loadFailed) {
            ErrorBanner(
                message = stringResource(R.string.kitchen_library_load_failed),
                modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
            )
            return@Column
        }
        if (showingSearch) {
            SearchResultList(
                entries = searchResults,
                limit = visibleLimit,
                onLoadMore = { visibleLimit += 20 },
                onOpenEntry = onOpenEntry,
            )
            return@Column
        }
        LazyColumn(state = categoryListState, modifier = Modifier.weight(1f)) {
            item {
                ArchiveContextHeader(
                    section = section,
                    currentCategory = currentCategory,
                    parentName =
                        currentCategory?.parentId?.let { parentId ->
                            categories.firstOrNull { it.id == parentId }?.name
                        } ?: stringResource(R.string.kitchen_title),
                    categoryPath =
                        currentCategory?.let { category -> categoryPath(category, categories) }.orEmpty(),
                    childCount = children.size,
                    directCount = directEntries.size,
                    sorting = sorting,
                    onAdd = if (!showingSearch) onAdd else null,
                    onCreateSubcategory = {
                        newSubcategoryParentId = currentCategory?.id
                        newSubcategoryName = ""
                        showNewSubcategoryDialog = true
                    },
                    onBack = {
                        if (sorting) requestSortReturn()
                        else if (currentCategory == null) onBack()
                        else currentCategoryId = currentCategory.parentId
                    },
                )
            }
            if (displayedChildren.isNotEmpty()) {
                if (!sorting) item {
                    SectionLabel(
                        text = stringResource(R.string.kitchen_directory_subcategories),
                        showSortButton = true,
                        onSort = { beginSorting() },
                    )
                }
                items(displayedChildren, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        sorting = sorting,
                        deleteMode = sorting,
                        onClick = { currentCategoryId = category.id },
                        listState = categoryListState,
                        onRename = {
                            renameTargetId = category.id
                            renameName = category.name
                        },
                        onMove = { delta ->
                            val from = workingOrder.indexOf(category.id)
                            val to = (from + delta).coerceIn(0, workingOrder.lastIndex)
                            if (from >= 0 && to != from) {
                                workingOrder = workingOrder.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                            }
                        },
                        onDelete = { deleteTargetId = category.id },
                    )
                }
            }
            if (!sorting && directEntries.isNotEmpty()) {
                item {
                    SectionLabel(
                        if (children.isEmpty()) {
                            stringResource(R.string.kitchen_directory_records)
                        } else {
                            stringResource(R.string.kitchen_directory_current_records)
                        },
                    )
                }
                items(directEntries.take(visibleLimit), key = { it.kind.name + it.id }) { entry ->
                    EntryRow(entry = entry, onClick = { onOpenEntry(entry) })
                }
                if (visibleLimit < directEntries.size) {
                    item {
                        LoadMore(
                            shown = visibleLimit.coerceAtMost(directEntries.size),
                            total = directEntries.size,
                            onLoadMore = { visibleLimit += 20 },
                        )
                    }
                }
            }
            if (children.isEmpty() && directEntries.isEmpty()) {
                item {
                    EmptyState(
                        icon = CulinaryIcons.Library,
                        title = stringResource(R.string.kitchen_directory_empty_title),
                        description = stringResource(R.string.kitchen_directory_empty_body),
                        actionLabel = stringResource(section.newLabelRes),
                        onAction = onAdd,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = sortSnackbarHostState,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sorting) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(68.dp)
                        .clickable { requestSortReturn() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }

        ArchiveBackFloatButton(
            label = currentCategory?.parentId?.let { parentId ->
                categories.firstOrNull { it.id == parentId }?.name
            } ?: stringResource(R.string.kitchen_title),
            onClick = {
                if (sorting) requestSortReturn()
                else if (currentCategory == null) onBack()
                else currentCategoryId = currentCategory.parentId
            },
            modifier = Modifier.align(Alignment.TopStart)
                .padding(top = CulinarySpacing.xs, start = CulinarySpacing.pageHorizontal),
        )

        ArchiveSearchIsland(
            expanded = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onExpand = { searchOpen = true },
            onCollapse = { searchOpen = false },
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(top = CulinarySpacing.xs, end = CulinarySpacing.pageHorizontal)
                .widthIn(max = 220.dp),
        )
    }

    if (showSortExitDialog) {
        AlertDialog(
            onDismissRequest = { showSortExitDialog = false },
            title = { Text("本次排序尚未保存") },
            text = { Text("当前还有未保存的调整，返回后将恢复上次保存的顺序。确定返回吗？") },
            confirmButton = { TextButton(onClick = { finishSorting() }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { showSortExitDialog = false }) { Text("取消") } },
        )
    }

    val parentForDialog = categories.firstOrNull { it.id == newSubcategoryParentId }
    if (showNewSubcategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewSubcategoryDialog = false },
            title = { Text("新建子库") },
            text = {
                Column {
                    Text(
                        text =
                            parentForDialog?.let { parent ->
                                "所属分类：${categoryPath(parent, categories).joinToString(" / ")}"
                            } ?: "所属资料库：${section.title()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
                    CulinaryTextField(
                        value = newSubcategoryName,
                        onValueChange = { newSubcategoryName = it },
                        label = { Text("子库名称") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateSubcategory(newSubcategoryParentId, newSubcategoryName)
                        newSubcategoryParentId = null
                        showNewSubcategoryDialog = false
                    },
                    enabled = newSubcategoryName.isNotBlank(),
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newSubcategoryParentId = null
                        showNewSubcategoryDialog = false
                    },
                ) { Text("取消") }
            },
        )
    }

    val renameTarget = categories.firstOrNull { it.id == renameTargetId }
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
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
                    onClick = {
                        onRenameSubcategory(renameTarget.id, renameName)
                        renameTargetId = null
                    },
                    enabled = renameName.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("取消") }
            },
        )
    }

    val deleteTarget = categories.firstOrNull { it.id == deleteTargetId }
    if (deleteTarget != null) {
        val canDelete = deleteTarget.childCount == 0 && deleteTarget.directCount == 0
        AlertDialog(
            onDismissRequest = {
                deleteTargetId = null
                deleteModeCategoryId = null
            },
            title = { Text("删除子库？") },
            text = {
                Text(
                    if (canDelete) {
                        "确定删除「${deleteTarget.name}」？此操作不可撤销。"
                    } else {
                        "「${deleteTarget.name}」仍有子分类或关联档案。为保护数据，系统不允许删除。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (canDelete) onDeleteSubcategory(deleteTarget.id)
                        deleteTargetId = null
                        deleteModeCategoryId = null
                    },
                    enabled = canDelete,
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteTargetId = null
                        deleteModeCategoryId = null
                    },
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ArchiveBackFloatButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(40.dp).clickable(onClick = onClick)
            .padding(horizontal = CulinarySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CulinaryIcons.Back,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CulinarySpacing.xxs),
        )
    }
}

@Composable
private fun ArchiveSearchIsland(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(if (expanded) 292.dp else 48.dp)
            .height(48.dp).animateContentSize(),
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
                imageVector = CulinaryIcons.Search,
                contentDescription = if (expanded) null else stringResource(R.string.kitchen_search_global_hint),
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
                        Box(modifier = Modifier.padding(start = CulinarySpacing.xs)) {
                            if (query.isEmpty()) Text(
                                text = stringResource(R.string.kitchen_search_global_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            innerTextField()
                        }
                    },
                )
                Icon(
                    imageVector = CulinaryIcons.Close,
                    contentDescription = stringResource(R.string.kitchen_search_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp).clickable(onClick = onCollapse).padding(9.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchiveContextHeader(
    section: KitchenArchiveSection,
    currentCategory: KitchenArchiveCategory?,
    parentName: String,
    categoryPath: List<String>,
    childCount: Int,
    directCount: Int,
    sorting: Boolean,
    onAdd: (() -> Unit)?,
    onCreateSubcategory: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        Spacer(modifier = Modifier.height(48.dp + CulinarySpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentCategory?.name ?: section.title(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            onAdd?.let { action ->
                CulinaryTextButton(
                    text = "＋ " + stringResource(section.newLabelRes),
                    onClick = action,
                )
            }
            onCreateSubcategory?.let { action ->
                CulinaryTextButton(text = "＋ 新建子库", onClick = action)
            }
        }
        if (categoryPath.isNotEmpty()) {
            Text(
                text = categoryPath.joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        if (!sorting) {
            Text(
                text = stringResource(R.string.kitchen_directory_counts, childCount, directCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    showSortButton: Boolean = false,
    onSort: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = CulinarySpacing.sm),
            )
            if (showSortButton) Box(
                modifier = Modifier.size(32.dp).clickable(onClick = onSort),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Sort,
                    contentDescription = "分类排序",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Edge auto-scroll velocity in px/s. A quadratic curve gives controlled entry
 * and a fast maximum so long lists can be traversed without reaching system edges.
 */
internal fun archiveAutoScrollVelocityPxPerSecond(edgeProgress: Float): Float {
    val progress = edgeProgress.coerceIn(0f, 1f)
    return 320f + 2_080f * progress * progress
}

@Composable
private fun CategoryRow(
    category: KitchenArchiveCategory,
    sorting: Boolean,
    deleteMode: Boolean,
    onClick: () -> Unit,
    listState: LazyListState,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var dragging by remember(category.id) { mutableStateOf(false) }
    var dragOffsetY by remember(category.id) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(category.id) { mutableIntStateOf(0) }
    val currentOnMove by rememberUpdatedState(onMove)

    // 在内容视口内提前触发自动滚动，避免手指必须抵达系统状态栏/导航栏边缘。
    // 速度按进入边缘区的深度平方增长，并按帧时间计算，避免固定小步长造成蜗牛式滚动。
    val autoScrollEdgePx = with(LocalDensity.current) { 144.dp.toPx() }
    LaunchedEffect(dragging, sorting, autoScrollEdgePx) {
        var previousFrameNanos = 0L
        while (dragging && sorting) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds =
                if (previousFrameNanos == 0L) 0f
                else ((frameNanos - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previousFrameNanos = frameNanos

            if (frameSeconds > 0f) {
                val viewport = listState.layoutInfo
                val draggedCenter = viewport.visibleItemsInfo
                    .firstOrNull { it.key == category.id }
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
                        val speed = archiveAutoScrollVelocityPxPerSecond(progress)
                        val requestedDelta = direction * speed * frameSeconds
                        val consumed = listState.scrollBy(requestedDelta)
                        // 列表滚动会移动行本身；用实际消费量补偿视觉偏移，保持项目与手指同步。
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
                val selectedScale = sorting && dragging
                val scale = when {
                    selectedScale -> 1.045f
                    sorting -> 1f
                    else -> 1f
                }
                scaleX = scale
                scaleY = scale
                translationX = 0f
                translationY = if (selectedScale) dragOffsetY else 0f
            }
            .shadow(
                elevation = when {
                    sorting && dragging -> 7.dp
                    else -> 0.dp
                },
                shape = RectangleShape,
            )
            .then(
                if (sorting) {
                    Modifier.pointerInput(category.id, sorting) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffsetY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetY += amount.y
                                // 以整行实际高度作为换位补偿，抵消列表重排造成的布局位移，
                                // 选中项的视觉位置因此连续跟随手指，不会一次跨越多行。
                                val threshold = rowHeightPx.toFloat().coerceAtLeast(1f)
                                while (dragOffsetY >= threshold) {
                                    currentOnMove(1)
                                    dragOffsetY -= threshold
                                }
                                while (dragOffsetY <= -threshold) {
                                    currentOnMove(-1)
                                    dragOffsetY += threshold
                                }
                            },
                        )
                    }
                } else {
                    Modifier.pointerInput(category.id, sorting) {
                        detectTapGestures(onTap = { onClick() })
                    }
                },
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
                    text = category.name,
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
            category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.kitchen_directory_category_meta, category.childCount, category.directCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (deleteMode) {
            Box(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CulinaryIcons.Trash, contentDescription = "删除子库",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
        if (!sorting) {
            Text(category.descendantCount.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(CulinaryIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(start = 4.dp))
        }
    }
    CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
}

@Composable
private fun EntryRow(
    entry: KitchenArchiveEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            entry.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
}

@Composable
private fun SearchResultList(
    entries: List<KitchenArchiveEntry>,
    limit: Int,
    onLoadMore: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = CulinaryIcons.Search,
            title = stringResource(R.string.kitchen_search_empty_title),
            description = stringResource(R.string.kitchen_search_empty_body),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionLabel(stringResource(R.string.kitchen_search_result_count, entries.size)) }
        items(entries.take(limit), key = { it.kind.name + it.id }) { entry ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEntry(entry) }
                        .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.categoryPathLabel.isNotBlank()) {
                    Text(
                        text = entry.categoryPathLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
        }
        if (limit < entries.size) {
            item { LoadMore(shown = limit.coerceAtMost(entries.size), total = entries.size, onLoadMore) }
        }
    }
}

@Composable
private fun LoadMore(
    shown: Int,
    total: Int,
    onLoadMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(CulinarySpacing.md)) {
        Text(
            text = stringResource(R.string.kitchen_directory_page_count, shown, total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CulinaryTextButton(
            text = stringResource(R.string.kitchen_directory_load_more),
            onClick = onLoadMore,
        )
    }
}

@Composable
private fun LegacyFlatList(
    entries: List<KitchenArchiveEntry>,
    limit: Int,
    onLoadMore: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    onAdd: () -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = CulinaryIcons.Library,
            title = stringResource(R.string.kitchen_directory_empty_title),
            description = stringResource(R.string.kitchen_directory_empty_body),
            actionLabel = stringResource(R.string.kitchen_directory_add),
            onAction = onAdd,
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries.take(limit), key = { it.id }) { entry ->
            EntryRow(entry = entry, onClick = { onOpenEntry(entry) })
        }
        if (limit < entries.size) {
            item { LoadMore(shown = limit.coerceAtMost(entries.size), total = entries.size, onLoadMore) }
        }
    }
}

private fun KitchenArchiveSection.title(): String =
    when (this) {
        KitchenArchiveSection.SAUCES -> "酱汁库"
        KitchenArchiveSection.INGREDIENTS -> "食材库"
        KitchenArchiveSection.RECIPES -> "菜谱库"
    }

private fun categoryPath(
    category: KitchenArchiveCategory,
    categories: List<KitchenArchiveCategory>,
): List<String> {
    val byId = categories.associateBy { it.id }
    val path = mutableListOf<String>()
    var cursor: KitchenArchiveCategory? = category
    var guard = 0
    while (cursor != null && guard++ < 32) {
        path.add(0, cursor.name)
        cursor = cursor.parentId?.let(byId::get)
    }
    return path
}
