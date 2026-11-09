package com.culinary.kitchen.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinarySearchField
import com.culinary.core.designsystem.component.CulinaryTextButton
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
    modifier: Modifier = Modifier,
) {
    var currentCategoryId by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable(section) { mutableStateOf(false) }
    var query by rememberSaveable(section) { mutableStateOf("") }
    var visibleLimit by rememberSaveable(section) { mutableIntStateOf(20) }

    val categories = state.categories
    val entries = state.entries.filter { it.kind == section.kind }
    val currentCategory = categories.firstOrNull { it.id == currentCategoryId }
    val children = categories.filter { it.parentId == currentCategoryId }
    val formalChildren = children.filter { !it.isTest }
    val testChildren = children.filter { it.isTest }
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
        if (categories.isEmpty()) {
            LegacyFlatList(
                entries = entries,
                limit = visibleLimit,
                onLoadMore = { visibleLimit += 20 },
                onOpenEntry = onOpenEntry,
                onAdd = onAdd,
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
                        onClick = { currentCategoryId = category.id },
                    )
                }
            }
            if (testChildren.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.kitchen_directory_test_section)) }
                items(testChildren, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        onClick = { currentCategoryId = category.id },
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
                CulinaryButton(
                    text = stringResource(section.newLabelRes),
                    onClick = action,
                    icon = CulinaryIcons.Add,
                )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (category.isTest) {
                    Text(
                        text = stringResource(R.string.kitchen_directory_test_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = CulinarySpacing.xs),
                    )
                }
            }
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
        KitchenArchiveSection.TECHNIQUES -> "烹饪技术"
        KitchenArchiveSection.PRODUCTION_STANDARDS -> "制作标准"
        KitchenArchiveSection.STORAGE_STANDARDS -> "储存标准"
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
