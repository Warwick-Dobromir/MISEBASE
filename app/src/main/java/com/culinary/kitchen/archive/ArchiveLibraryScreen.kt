package com.culinary.kitchen.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val categories = state.categories
    val entries = state.entries.filter { it.kind == section.kind }
    val currentCategory = categories.firstOrNull { it.id == currentCategoryId }
    // 测试分类在数据库启动清理后不应再出现；页面同时不展示任何遗留测试节点。
    val children = categories.filter { it.parentId == currentCategoryId && !it.isTest }
    val formalChildren = children
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
    LaunchedEffect(currentCategoryId, query, searchOpen) {
        visibleLimit = 20
    }

    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = !searchOpen && currentCategoryId != null) {
        currentCategoryId = currentCategory?.parentId
    }
    BackHandler(enabled = !searchOpen && currentCategoryId == null) { onBack() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        ArchiveBrandHeader(
            section = section,
            isSearchOpen = searchOpen,
            onSearch = { searchOpen = true },
            onCloseSearch = { searchOpen = false },
        )
        AnimatedVisibility(
            visible = searchOpen,
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
        CulinaryRule()
        state.categoryError?.let { message ->
            ErrorBanner(
                message = message,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        state.categoryMessage?.let { message ->
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    onAdd = if (!showingSearch && currentCategoryId == null) onAdd else null,
                    onCreateSubcategory = {
                        newSubcategoryParentId = currentCategory?.id
                        newSubcategoryName = ""
                        showNewSubcategoryDialog = true
                    },
                    onBack = {
                        if (currentCategory == null) onBack() else currentCategoryId = currentCategory.parentId
                    },
                )
            }
            if (formalChildren.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.kitchen_directory_subcategories)) }
                items(formalChildren, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        canManage = currentCategoryId != null,
                        deleteMode = deleteModeCategoryId == category.id,
                        onClick = { currentCategoryId = category.id },
                        onRename = {
                            renameTargetId = category.id
                            renameName = category.name
                        },
                        onToggleDeleteMode = {
                            deleteModeCategoryId =
                                if (deleteModeCategoryId == category.id) null else category.id
                        },
                        onDelete = { deleteTargetId = category.id },
                        onMove = { direction ->
                            val from = formalChildren.indexOfFirst { it.id == category.id }
                            val to = from + direction
                            if (from >= 0 && to in formalChildren.indices) {
                                val ordered =
                                    formalChildren.toMutableList().apply {
                                        add(to, removeAt(from))
                                    }
                                onReorderSubcategories(ordered.map { it.id })
                            }
                        },
                        onAddChild = {
                            newSubcategoryParentId = category.id
                            newSubcategoryName = ""
                            showNewSubcategoryDialog = true
                        },
                    )
                }
            }
            if (directEntries.isNotEmpty()) {
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
    isSearchOpen: Boolean,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (isSearchOpen) {
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
    directCount: Int,
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).clickable(onClick = onBack),
            )
            Text(
                text = parentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xxs),
            )
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
        Text(
            text = stringResource(R.string.kitchen_directory_counts, childCount, directCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg)) {
        CulinaryRule()
        Text(
            text = text,
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.sm),
        )
    }
}

@Composable
private fun CategoryRow(
    category: KitchenArchiveCategory,
    canManage: Boolean,
    deleteMode: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onToggleDeleteMode: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onAddChild: () -> Unit,
) {
    val currentOnMove by rememberUpdatedState(onMove)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = if (canManage) onRename else null,
                    ),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.kitchen_directory_category_meta,
                        category.childCount,
                        category.directCount,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canManage) {
            Icon(
                imageVector = CulinaryIcons.Sort,
                contentDescription = "长按拖动排序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(28.dp)
                        .padding(4.dp)
                        .pointerInput(category.id) {
                            var distance = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { distance = 0f },
                                onDragEnd = { distance = 0f },
                                onDragCancel = { distance = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    distance += dragAmount.y
                                    val threshold = 44.dp.toPx()
                                    if (distance >= threshold) {
                                        currentOnMove(1)
                                        distance -= threshold
                                    } else if (distance <= -threshold) {
                                        currentOnMove(-1)
                                        distance += threshold
                                    }
                                },
                            )
                        },
            )
        }
        if (deleteMode) {
            Icon(
                imageVector = CulinaryIcons.Trash,
                contentDescription = "删除子库；长按取消",
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(40.dp)
                        .combinedClickable(
                            onClick = onDelete,
                            onLongClick = onToggleDeleteMode,
                        ).padding(10.dp),
            )
        } else {
            Text(
                text = "＋ 新建子库",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = onAddChild,
                            onLongClick = if (canManage) onToggleDeleteMode else null,
                        ).padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
            )
        }
        Text(
            text = category.descendantCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(start = 4.dp),
        )
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
