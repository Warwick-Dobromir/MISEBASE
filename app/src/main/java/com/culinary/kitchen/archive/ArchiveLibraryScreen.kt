package com.culinary.kitchen.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.delay
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinarySearchField
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
    val isScrolled by remember { derivedStateOf { categoryListState.firstVisibleItemIndex > 0 || categoryListState.firstVisibleItemScrollOffset > 72 } }
    var workingOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var pendingSave by rememberSaveable(section, currentCategoryId) { mutableStateOf(false) }
    var dragStartOrder by remember { mutableStateOf(emptyList<String>()) }
    var submittedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var showSortExitDialog by rememberSaveable(section) { mutableStateOf(false) }
    var lastSortSaveVersion by rememberSaveable(section) { mutableIntStateOf(state.categorySortSaveVersion) }
    val sortSnackbarHostState = remember { SnackbarHostState() }

    val categories = state.categories
    val entries = state.entries.filter { it.kind == section.kind && it.testBatch == null }
    val currentCategory = categories.firstOrNull { it.id == currentCategoryId }
    // 测试分类在数据库启动清理后不应再出现；页面同时不展示任何遗留测试节点。
    val children = categories.filter { it.parentId == currentCategoryId && !it.isTest }
    val formalChildren = children
    val displayedChildren = if (sorting && workingOrder.isNotEmpty()) {
        workingOrder.mapNotNull { id -> children.firstOrNull { it.id == id } }
    } else children
    val sortDirty = sorting && workingOrder != savedOrder
    val directEntries = entries.filter { entry -> entry.categoryId == currentCategoryId }
    val recursiveCount = currentCategory?.descendantCount
        ?: (categories.filter { it.parentId == null && !it.isTest }.sumOf { it.descendantCount } + directEntries.size)
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
        }
    }
    fun finishSorting() {
        workingOrder = savedOrder
        sorting = false
        pendingSave = false
        showSortExitDialog = false
    }
    fun requestSortReturn() {
        if (sortDirty) {
            showSortExitDialog = true
        } else {
            finishSorting()
        }
    }

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && sorting) { requestSortReturn() }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId != null) {
        currentCategoryId = currentCategory?.parentId
    }
    BackHandler(enabled = !searchOpen && !sorting && currentCategoryId == null) { onBack() }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if ((!isScrolled && currentCategoryId == null) || sorting) {
            ArchiveBrandHeader(
                section = section,
                showBrand = currentCategoryId == null,
                isSearchOpen = searchOpen,
                sorting = sorting,
                onSearch = { searchOpen = true },
                onCloseSearch = { searchOpen = false },
                onSaveSort = {
                    if (sorting && !pendingSave && sortDirty) {
                        submittedOrder = workingOrder
                        pendingSave = true
                        onReorderSubcategories(submittedOrder)
                    }
                },
            )
        }
        AnimatedVisibility(
            visible = searchOpen && !isScrolled,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            CulinarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.kitchen_search_global_hint),
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.sm,
                    ),
            )
        }
        if ((!isScrolled && currentCategoryId == null) || sorting) CulinaryRule()
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
                    recursiveCount = recursiveCount,
                    sorting = sorting,
                    onSearch = if (currentCategoryId != null) {
                        { searchOpen = !searchOpen }
                    } else {
                        null
                    },
                    onAdd = if (!showingSearch && currentCategoryId == null) onAdd else null,
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
                        showSortControl = currentCategoryId != null,
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
                        onDragStart = { dragStartOrder = workingOrder },
                        onDragCancel = { workingOrder = dragStartOrder },
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
        if (sorting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable {
                                            showSortExitDialog = true
                        }
                        .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
                )
            }
        }
        SnackbarHost(
            hostState = sortSnackbarHostState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AnimatedVisibility(
        visible = isScrolled && !searchOpen && !sorting,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 16.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.82f),
        exit = fadeOut() + scaleOut(targetScale = 0.82f),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable { searchOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CulinaryIcons.Search,
                contentDescription = stringResource(R.string.kitchen_search_global_hint),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    AnimatedVisibility(
        visible = isScrolled && searchOpen,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
        enter = expandIn(expandFrom = Alignment.TopEnd) + fadeIn(),
        exit = shrinkOut(shrinkTowards = Alignment.TopEnd) + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .sizeIn(maxWidth = 340.dp)
                .fillMaxWidth(0.92f)
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CulinarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.kitchen_search_global_hint),
                modifier = Modifier.weight(1f),
            )
            CulinaryTextButton(
                text = stringResource(R.string.kitchen_search_close),
                onClick = { searchOpen = false },
            )
        }
    }
    }

    if (showSortExitDialog) {
        AlertDialog(
            onDismissRequest = { showSortExitDialog = false },
            title = {
                Text(if (sortDirty) "本次排序尚未保存" else "确认要返回吗？")
            },
            text = {
                Text(
                    if (sortDirty) {
                        "当前还有未保存的调整，返回后将恢复上次保存的顺序。确定返回吗？"
                    } else {
                        "当前排序没有未保存的修改，确认退出排序吗？"
                    },
                )
            },
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
private fun ArchiveBrandHeader(
    section: KitchenArchiveSection,
    showBrand: Boolean,
    isSearchOpen: Boolean,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    sorting: Boolean = false,
    onSaveSort: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBrand) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MISEBASE",
                    style = CulinaryTextStyles.eyebrow,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(section.subtitleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (sorting) {
            CulinaryTextButton(
                text = "保存",
                onClick = onSaveSort,
            )
        } else if (isSearchOpen) {
            CulinaryTextButton(
                text = stringResource(R.string.kitchen_search_close),
                onClick = onCloseSearch,
            )
        } else {
            CulinaryIconButton(
                icon = CulinaryIcons.Search,
                contentDescription = stringResource(R.string.kitchen_search_global_hint),
                onClick = onSearch,
                tint = MaterialTheme.colorScheme.onSurface,
            )
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
    recursiveCount: Int,
    sorting: Boolean,
    onSearch: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    onCreateSubcategory: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CulinaryIcons.Back,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack).padding(11.dp),
            )
            Text(
                text = parentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xxs).weight(1f),
            )
            onSearch?.let { action ->
                CulinaryIconButton(
                    icon = CulinaryIcons.Search,
                    contentDescription = stringResource(R.string.kitchen_search_global_hint),
                    onClick = action,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
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
                text = stringResource(R.string.kitchen_directory_counts, childCount, recursiveCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    showSortControl: Boolean = false,
    onSort: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        CulinaryRule()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = CulinarySpacing.xs),
            )
            if (showSortControl) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                        .clickable(onClick = onSort),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CulinaryIcons.Sort,
                        contentDescription = "排列下级分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
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
    onDragStart: () -> Unit,
    onDragCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val actionInteractionSource = remember(category.id) { MutableInteractionSource() }
    val actionPressed by actionInteractionSource.collectIsPressedAsState()
    var dragging by remember(category.id) { mutableStateOf(false) }
    var dragOffsetY by remember(category.id) { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(category.id) { mutableIntStateOf(0) }
    val currentOnMove by rememberUpdatedState(onMove)
    // 按住边缘时持续缓慢滚动，而不是依赖手指继续移动产生新的拖动事件。
    LaunchedEffect(dragging, sorting) {
        while (dragging && sorting) {
            val viewport = listState.layoutInfo
            val draggedCenter = viewport.visibleItemsInfo
                .firstOrNull { it.key == category.id }
                ?.let { it.offset + rowHeightPx / 2f + dragOffsetY }
            if (draggedCenter != null) {
                val edge = 104f
                val topDistance = draggedCenter - viewport.viewportStartOffset
                val bottomDistance = viewport.viewportEndOffset - draggedCenter
                val delta = when {
                    topDistance < edge -> -(6f + (1f - (topDistance / edge).coerceIn(0f, 1f)) * 18f)
                    bottomDistance < edge -> 6f + (1f - (bottomDistance / edge).coerceIn(0f, 1f)) * 18f
                    else -> 0f
                }
                if (delta != 0f) {
                    val consumed = listState.scrollBy(delta)
                    dragOffsetY += consumed
                }
            }
            delay(16)
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
                                onDragStart()
                                dragging = true
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                dragging = false
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                onDragCancel()
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
                    Modifier.clickable(onClick = onClick)
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
                stringResource(R.string.kitchen_directory_category_meta, category.childCount, category.descendantCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (deleteMode) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        interactionSource = actionInteractionSource,
                        indication = null,
                        onClick = onDelete,
                    )
                    .shadow(if (actionPressed) 3.dp else 0.dp, RectangleShape)
                    .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    CulinaryIcons.Trash,
                    contentDescription = "删除子库",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
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
