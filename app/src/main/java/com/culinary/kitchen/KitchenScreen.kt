package com.culinary.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    modifier: Modifier = Modifier,
    viewModel: KitchenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    KitchenScreen(
        state = state,
        onOpenCategory = onOpenCategory,
        modifier = modifier,
    )
}

/**
 * 厨房首页视觉结构：字标/标题 → 烹饪资料 → 工作标准。
 *
 * 顶部区固定，分类主体独立滚动；底栏由导航外壳提供，不参与本页滚动。
 * 跨库搜索已按任务要求移除；六库各自在目录页提供单库搜索。
 */
@Composable
internal fun KitchenScreen(
    state: KitchenUiState,
    onOpenCategory: (KitchenCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        KitchenHeader()
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
}

/** 顶部：小号 MISEBASE、厨房标题与 CULINARY 副标题。 */
@Composable
private fun KitchenHeader() {
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
