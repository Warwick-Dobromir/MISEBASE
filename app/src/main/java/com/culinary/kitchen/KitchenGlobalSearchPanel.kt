package com.culinary.kitchen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.designsystem.component.CulinaryListRow
import com.culinary.core.designsystem.component.CulinarySearchField
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.kitchen.archive.recipeCategoryLabelRes
import com.culinary.kitchen.archive.typeLabelRes
import java.util.Locale

/**
 * 厨房全局搜索面板。
 *
 * 从右侧滑入；关闭按钮、系统返回和向右滑动都能退出。搜索只读全部六库，
 * 结果点击后交给导航层打开对应详情，不创建或修改档案。
 */
@Composable
internal fun KitchenGlobalSearchPanel(
    visible: Boolean,
    entries: List<KitchenArchiveEntry>,
    onDismiss: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        GlobalSearchContent(
            entries = entries,
            onDismiss = onDismiss,
            onOpenEntry = onOpenEntry,
        )
    }
}

@Suppress("LongMethod") // 搜索框、空态和结果列表属于同一滑入面板
@Composable
private fun GlobalSearchContent(
    entries: List<KitchenArchiveEntry>,
    onDismiss: () -> Unit,
    onOpenEntry: (KitchenArchiveEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val typeLabels =
        KitchenArchiveKind.entries.associateWith { kind ->
            stringResource(kind.typeLabelRes())
        }
    val uncategorized = stringResource(R.string.kitchen_library_uncategorized)
    val categoryLabels =
        entries
            .map { entry -> entry.category?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
            .associateWith { raw ->
                if (raw == null) {
                    uncategorized
                } else {
                    recipeCategoryLabelRes(raw)?.let { stringResource(it) } ?: raw
                }
            }

    fun categoryLabel(entry: KitchenArchiveEntry): String {
        val raw = entry.category?.trim()?.takeIf { it.isNotEmpty() }
        return categoryLabels[raw] ?: uncategorized
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val results =
        if (normalizedQuery.isEmpty()) {
            emptyList()
        } else {
            entries.filter { entry ->
                val searchText =
                    buildString {
                        append(entry.name)
                        append(' ')
                        entry.subtitle?.let { append(it).append(' ') }
                        append(categoryLabel(entry))
                        append(' ')
                        append(typeLabels[entry.kind].orEmpty())
                        append(' ')
                        append(entry.searchText)
                    }.lowercase(Locale.ROOT)
                searchText.contains(normalizedQuery)
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("kitchen-global-search")
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0f) horizontalDrag += dragAmount
                        },
                        onDragEnd = {
                            if (horizontalDrag > with(density) { 80.dp.toPx() }) {
                                onDismiss()
                            }
                            horizontalDrag = 0f
                        },
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.xs,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.xs,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("kitchen-search-close"),
            ) {
                Icon(
                    imageVector = CulinaryIcons.Close,
                    contentDescription = stringResource(R.string.kitchen_search_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Text(
                text = stringResource(R.string.kitchen_search_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        CulinarySearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.kitchen_search_hint),
            modifier =
                Modifier
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.sm,
                    ).focusRequester(focusRequester),
        )
        when {
            normalizedQuery.isEmpty() ->
                Text(
                    text = stringResource(R.string.kitchen_search_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            horizontal = CulinarySpacing.pageHorizontal,
                            vertical = CulinarySpacing.xl,
                        ),
                )

            results.isEmpty() ->
                Text(
                    text = stringResource(R.string.kitchen_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            horizontal = CulinarySpacing.pageHorizontal,
                            vertical = CulinarySpacing.xl,
                        ),
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            top = CulinarySpacing.sm,
                            bottom = CulinarySpacing.xl,
                        ),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.kitchen_search_results, results.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier.padding(
                                    horizontal = CulinarySpacing.pageHorizontal,
                                    vertical = CulinarySpacing.xs,
                                ),
                        )
                    }
                    items(results, key = { "${it.kind}:${it.id}" }) { entry ->
                        val typeLabel = typeLabels[entry.kind].orEmpty()
                        CulinaryListRow(
                            title = entry.name,
                            modifier = Modifier.testTag("search-result-${entry.kind}"),
                            eyebrow = typeLabel,
                            subtitle = categoryLabel(entry),
                            trailing = {
                                Icon(
                                    imageVector = CulinaryIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { onOpenEntry(entry) },
                            minHeight = 72.dp,
                        )
                    }
                }
        }
    }
}
