package com.culinary.kitchen.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryListRow
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinarySearchField
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import java.util.Locale

private const val CATEGORY_UNCATEGORIZED = "__uncategorized__"
private val RECIPE_CATEGORY_KEYS = listOf("STOCK", "PREP", "COMPONENT", "DISH", "OTHER")

private data class ArchiveCategoryFilter(
    val key: String,
    val label: String,
)

/**
 * 六个资料库的统一页面路由。
 *
 * 页面只负责当前库的搜索、分类筛选、新增和列表浏览；
 * 厨房全局搜索不在此页重复出现，底栏由外层导航外壳保留。
 */
@Composable
fun ArchiveLibraryRoute(
    section: KitchenArchiveSection,
    onAdd: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchiveLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArchiveLibraryScreen(
        section = section,
        state = state,
        onAdd = onAdd,
        onOpenEntry = onOpenEntry,
        modifier = modifier,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // 统一骨架同时承载筛选、搜索、列表与空态
@Composable
internal fun ArchiveLibraryScreen(
    section: KitchenArchiveSection,
    state: ArchiveLibraryUiState,
    onAdd: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(section) { mutableStateOf("") }
    var selectedCategory by rememberSaveable(section) { mutableStateOf<String?>(null) }

    val libraryEntries = state.entries.filter { it.kind == section.kind }
    val typeLabels = archiveKindLabels()
    val uncategorizedLabel = stringResource(R.string.kitchen_library_uncategorized)
    val entryCategories = libraryEntries.map { entry -> entry.normalizedCategory() }.distinct()
    val categoryKeys =
        if (section == KitchenArchiveSection.RECIPES) {
            RECIPE_CATEGORY_KEYS + if (entryCategories.contains(null)) listOf(null) else emptyList()
        } else {
            entryCategories
        }
    val categoryLabels =
        categoryKeys.associateWith { raw ->
            if (raw == null) uncategorizedLabel else categoryDisplayLabel(raw)
        }
    val categoryFilters =
        buildList {
            add(ArchiveCategoryFilter(key = "", label = stringResource(R.string.kitchen_library_all)))
            categoryLabels.forEach { (raw, label) ->
                add(
                    ArchiveCategoryFilter(
                        key = raw ?: CATEGORY_UNCATEGORIZED,
                        label = label,
                    ),
                )
            }
        }

    LaunchedEffect(categoryFilters) {
        if (selectedCategory != null && categoryFilters.none { it.key == selectedCategory }) {
            selectedCategory = null
        }
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filteredEntries =
        libraryEntries.filter { entry ->
            val categoryMatches =
                selectedCategory == null ||
                    entry.normalizedCategory().let { raw ->
                        (raw ?: CATEGORY_UNCATEGORIZED) == selectedCategory
                    }
            val categoryLabel = categoryLabels[entry.normalizedCategory()].orEmpty()
            val searchText =
                buildString {
                    append(entry.name)
                    append(' ')
                    entry.subtitle?.let { append(it).append(' ') }
                    append(categoryLabel)
                    append(' ')
                    append(typeLabels[entry.kind].orEmpty())
                    append(' ')
                    append(entry.searchText)
                }.lowercase(Locale.ROOT)
            categoryMatches && (normalizedQuery.isEmpty() || searchText.contains(normalizedQuery))
        }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(bottom = CulinarySpacing.xl),
    ) {
        item {
            LibraryHeader(
                section = section,
                totalCount = libraryEntries.size,
            )
        }
        item {
            CulinarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(section.searchHintRes),
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.xs,
                    ),
            )
        }
        item {
            CategoryAndAddRow(
                section = section,
                onAdd = onAdd,
            )
        }
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(
                            start = CulinarySpacing.pageHorizontal,
                            end = CulinarySpacing.pageHorizontal,
                            top = CulinarySpacing.xs,
                            bottom = CulinarySpacing.md,
                        ),
                horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
            ) {
                categoryFilters.forEach { filter ->
                    val selected = (selectedCategory ?: "") == filter.key
                    CulinaryFilterPill(
                        text = filter.label,
                        selected = selected,
                        onClick = { selectedCategory = filter.key.ifEmpty { null } },
                        modifier = Modifier.testTag("archive-filter-${filter.key.ifEmpty { "all" }}"),
                    )
                }
            }
        }
        when {
            state.isLoading ->
                item {
                    LibraryMessage(text = stringResource(R.string.kitchen_library_loading))
                }

            state.loadFailed ->
                item {
                    LibraryMessage(
                        text = stringResource(R.string.kitchen_library_load_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

            filteredEntries.isEmpty() ->
                item {
                    LibraryEmptyState(
                        hasQuery = normalizedQuery.isNotEmpty() || selectedCategory != null,
                    )
                }

            else ->
                items(filteredEntries, key = { "${it.kind}:${it.id}" }) { entry ->
                    ArchiveEntryRow(
                        entry = entry,
                        typeLabels = typeLabels,
                        categoryLabel = categoryLabels[entry.normalizedCategory()].orEmpty(),
                        onClick = { onOpenEntry(entry) },
                    )
                }
        }
        item {
            CulinaryRule()
            Text(
                text =
                    stringResource(
                        R.string.kitchen_library_display,
                        filteredEntries.size,
                        libraryEntries.size,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.md,
                        bottom = CulinarySpacing.xl,
                    ),
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    section: KitchenArchiveSection,
    totalCount: Int,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.lg,
                    bottom = CulinarySpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.titleRes),
                style = CulinaryTextStyles.pageTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(section.subtitleRes),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.12.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Text(
            text = stringResource(R.string.kitchen_library_total, totalCount),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryAndAddRow(
    section: KitchenArchiveSection,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.md,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.kitchen_library_category),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(section.newLabelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier =
                Modifier
                    .testTag("archive-add")
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(vertical = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun ArchiveEntryRow(
    entry: KitchenArchiveEntry,
    typeLabels: Map<KitchenArchiveKind, String>,
    categoryLabel: String,
    onClick: () -> Unit,
) {
    val typeLabel = typeLabels[entry.kind].orEmpty()
    CulinaryListRow(
        title = entry.name,
        modifier = Modifier.testTag("archive-entry-${entry.id}"),
        subtitle =
            entry.subtitle
                ?: stringResource(R.string.kitchen_archive_subtitle, typeLabel),
        meta = categoryLabel.ifBlank { stringResource(R.string.kitchen_library_uncategorized) },
        trailing = {
            Icon(
                imageVector = CulinaryIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
        minHeight = 72.dp,
    )
}

@Composable
private fun LibraryEmptyState(hasQuery: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CulinarySpacing.pageHorizontal,
                    vertical = CulinarySpacing.xl,
                ),
    ) {
        Text(
            text =
                stringResource(
                    if (hasQuery) R.string.kitchen_library_no_match else R.string.kitchen_library_empty,
                ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!hasQuery) {
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(
                text = stringResource(R.string.kitchen_library_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryMessage(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier =
            Modifier.padding(
                horizontal = CulinarySpacing.pageHorizontal,
                vertical = CulinarySpacing.xl,
            ),
    )
}

@Composable
private fun archiveKindLabels(): Map<KitchenArchiveKind, String> =
    KitchenArchiveKind.entries.associateWith { kind ->
        stringResource(kind.typeLabelRes())
    }

@Composable
private fun categoryDisplayLabel(raw: String): String {
    if (raw.startsWith("__")) return raw
    return recipeCategoryLabelRes(raw)?.let { stringResource(it) }
        ?: raw
}

private fun KitchenArchiveEntry.normalizedCategory(): String? = category?.trim()?.takeIf { it.isNotEmpty() }
