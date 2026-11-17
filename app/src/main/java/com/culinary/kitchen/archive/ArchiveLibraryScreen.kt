package com.culinary.kitchen.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var sorting by rememberSaveable(section) { mutableStateOf(false) }
    var workingOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var savedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var pendingSave by rememberSaveable(section, currentCategoryId) { mutableStateOf(false) }
    var submittedOrder by rememberSaveable(section, currentCategoryId) { mutableStateOf(emptyList<String>()) }
    var showSortExitDialog by rememberSaveable(section) { mutableStateOf(false) }
    var sortExitHasUnsavedChanges by rememberSaveable(section) { mutableStateOf(false) }
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
            sortExitHasUnsavedChanges = true
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

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        ArchiveBrandHeader(
            section = section,
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
        LazyColumn(modifier = Modifier.weight(1f)) {
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
                if (!sorting) item { SectionLabel(stringResource(R.string.kitchen_directory_subcategories)) }
                items(displayedChildren, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        sorting = sorting,
                        deleteMode = sorting,
                        onClick = { currentCategoryId = category.id },
                        onLongPress = { beginSorting() },
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
                        onAddChild = {
                            newSubcategoryParentId = category.id
                            newSubcategoryName = ""
                            showNewSubcategoryDialog = true
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
                            sortExitHasUnsavedChanges = true
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
private fun ArchiveBrandHeader(
    section: KitchenArchiveSection,
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
    directCount: Int,
    sorting: Boolean,
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
    sorting: Boolean,
    deleteMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val actionInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
    val actionPressed by actionInteractionSource.collectIsPressedAsState()
    val sortMotion = rememberInfiniteTransition()
    val sortWobble by sortMotion.animateFloat(
        initialValue = -1.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 105, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    var dragDistance by rememberSaveable(category.id) { mutableIntStateOf(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationZ = if (sorting) sortWobble else 0f
                scaleX = if (sorting) 1.008f else 1f
                scaleY = if (sorting) 1.008f else 1f
            }
            .then(
                if (sorting) {
                    Modifier.pointerInput(category.id, sorting) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragDistance = 0 },
                            onDragEnd = { dragDistance = 0 },
                            onDragCancel = { dragDistance = 0 },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount.y.toInt()
                                val threshold = 28.dp.toPx().toInt().coerceAtLeast(1)
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
                } else {
                    Modifier
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                            onDoubleClick = onRename,
                            onLongClick = onLongPress,
                        )
                        .shadow(if (pressed) 3.dp else 0.dp, RectangleShape)
                },
            )
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            category.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.kitchen_directory_category_meta, category.childCount, category.directCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(
                    interactionSource = actionInteractionSource,
                    indication = null,
                    onClick = if (deleteMode) onDelete else onAddChild,
                )
                .shadow(if (actionPressed) 3.dp else 0.dp, RectangleShape)
                .padding(horizontal = CulinarySpacing.xs, vertical = CulinarySpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            if (deleteMode) {
                Icon(CulinaryIcons.Trash, contentDescription = "删除子库", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            } else {
                Text("＋ 新建子库", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
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
