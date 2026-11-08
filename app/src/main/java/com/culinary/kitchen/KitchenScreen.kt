package com.culinary.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 厨房首页路由。
 *
 * 数字来自真实档案 Flow；页面不在本层缓存数量，新增/删除后自动刷新。
 */
@Composable
fun KitchenRoute(
    onOpenCategory: (KitchenCategory) -> Unit,
    onOpenSearchEntry: (KitchenArchiveEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitchenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val archiveEntries by viewModel.archiveEntries.collectAsStateWithLifecycle()

    KitchenScreen(
        state = state,
        archiveEntries = archiveEntries,
        onOpenCategory = onOpenCategory,
        onOpenSearchEntry = onOpenSearchEntry,
        modifier = modifier,
    )
}

/**
 * 厨房首页视觉结构：字标/标题 → 烹饪资料 → 工作标准。
 *
 * 顶部区固定，分类主体独立滚动；底栏由导航外壳提供，不参与本页滚动。
 * 右上角只有厨房全局搜索；搜索面板以覆盖层从右侧进入。
 */
@Composable
internal fun KitchenScreen(
    state: KitchenUiState,
    archiveEntries: List<KitchenArchiveEntry>,
    onOpenCategory: (KitchenCategory) -> Unit,
    onOpenSearchEntry: (KitchenArchiveEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            KitchenHeader(onOpenSearch = { isSearchOpen = true })
            CulinaryRule()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (state.loadFailed) {
                    Text(
                        text = stringResource(R.string.kitchen_count_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.padding(
                                start = CulinarySpacing.pageHorizontal,
                                end = CulinarySpacing.pageHorizontal,
                                top = CulinarySpacing.md,
                            ),
                    )
                }
                KitchenSectionBlock(
                    section = KitchenSection.REFERENCES,
                    state = state,
                    onOpenCategory = onOpenCategory,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
                KitchenSectionBlock(
                    section = KitchenSection.STANDARDS,
                    state = state,
                    onOpenCategory = onOpenCategory,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }
        }
        KitchenGlobalSearchPanel(
            visible = isSearchOpen,
            entries = archiveEntries,
            onDismiss = { isSearchOpen = false },
            onOpenEntry = { entry ->
                isSearchOpen = false
                onOpenSearchEntry(entry)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 顶部：小号 MISEBASE、厨房标题、CULINARY 副标题与厨房全局搜索入口。 */
@Composable
private fun KitchenHeader(onOpenSearch: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.xs,
                    top = CulinarySpacing.sm,
                    bottom = CulinarySpacing.sm,
                ).defaultMinSize(minHeight = 92.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_brand),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.18.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(
                text = stringResource(R.string.kitchen_title),
                style = CulinaryTextStyles.pageTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.kitchen_subtitle),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.16.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryIconButton(
            icon = CulinaryIcons.Search,
            contentDescription = stringResource(R.string.kitchen_search_entry),
            onClick = onOpenSearch,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun KitchenSectionBlock(
    section: KitchenSection,
    state: KitchenUiState,
    onOpenCategory: (KitchenCategory) -> Unit,
) {
    val categories = section.categories()
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.lg,
                        bottom = CulinarySpacing.xs,
                    ),
        ) {
            Text(
                text = stringResource(section.titleRes),
                style = CulinaryTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(section.eyebrowRes),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.12.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        categories.forEachIndexed { index, category ->
            KitchenCategoryRow(
                category = category,
                state = state,
                onClick = { onOpenCategory(category) },
            )
            if (index < categories.lastIndex) {
                CulinaryRule(
                    modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
                )
            }
        }
    }
}

/** 分类行：左侧双行文字，右侧“N 项 >”整体垂直居中。 */
@Composable
private fun KitchenCategoryRow(
    category: KitchenCategory,
    state: KitchenUiState,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("kitchen-category-${category.routeKey}")
                .clickable(role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 76.dp)
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.md,
                    bottom = CulinarySpacing.md,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
            Text(
                text = stringResource(category.subtitleRes),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.1.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Text(
            text = category.countLabel(state),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KitchenCategory.countLabel(state: KitchenUiState): String =
    when {
        state.isLoading || state.loadFailed -> stringResource(R.string.kitchen_count_unavailable)
        else -> stringResource(R.string.kitchen_count, state.counts.countFor(this))
    }
