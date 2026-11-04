package com.culinary.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.culinary.R
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

/** 库存概览：真实商品/批次/需关注数；空、加载、失败均局部处理。 */
@Composable
internal fun InventorySection(
    state: HomeSection<HomeInventorySummary>,
    onOpenInventory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier = modifier) {
        SectionHeader(
            icon = CulinaryIcons.Inventory,
            title = stringResource(R.string.home_inventory_title),
            onClick = if (state is HomeSection.Ready) onOpenInventory else null,
            action = {
                if (state is HomeSection.Ready) {
                    DetailsAction()
                }
            },
        )
        when (state) {
            HomeSection.Loading -> InventorySkeleton()
            is HomeSection.Failed ->
                SectionError(
                    title = stringResource(R.string.home_inventory_error),
                    message = state.message,
                    onRetry = onRetry,
                )

            is HomeSection.Ready ->
                if (state.value.isEmpty) {
                    HomeEmptyState(
                        icon = CulinaryIcons.Inventory,
                        title = stringResource(R.string.home_inventory_empty_title),
                        body = stringResource(R.string.home_inventory_empty_body),
                        actionLabel = stringResource(R.string.home_inventory_empty_action),
                        onAction = onOpenReceipt,
                    )
                } else {
                    InventoryMetrics(summary = state.value, onOpenInventory = onOpenInventory)
                }
        }
    }
}

@Composable
private fun InventoryMetrics(
    summary: HomeInventorySummary,
    onOpenInventory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricColumn(
            value = summary.productCount,
            label = stringResource(R.string.home_inventory_products),
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
        MetricDivider()
        MetricColumn(
            value = summary.batchCount,
            label = stringResource(R.string.home_inventory_batches),
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
        MetricDivider()
        MetricColumn(
            value = summary.attentionCount,
            label = stringResource(R.string.home_inventory_attention),
            highlight = summary.attentionCount > 0,
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricColumn(
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displaySmall,
                color =
                    if (highlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
            )
            if (highlight && value > 0) {
                Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
                AttentionMark()
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AttentionMark() {
    Box(
        modifier =
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 快捷操作：四个入口均连到真实路由，没有占位假动作。 */
@Composable
internal fun QuickActionsSection(
    onOpenScan: () -> Unit,
    onNewIngredient: () -> Unit,
    onNewRecipe: () -> Unit,
    onNewSauce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier = modifier) {
        SectionHeader(
            icon = CulinaryIcons.Bolt,
            title = stringResource(R.string.home_quick_title),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = CulinarySpacing.md, end = CulinarySpacing.md, bottom = CulinarySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        ) {
            QuickActionTile(
                icon = CulinaryIcons.Scan,
                label = stringResource(R.string.home_quick_scan),
                onClick = onOpenScan,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = CulinaryIcons.Ingredient,
                label = stringResource(R.string.home_quick_ingredient),
                onClick = onNewIngredient,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = CulinaryIcons.Recipe,
                label = stringResource(R.string.home_quick_recipe),
                onClick = onNewRecipe,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = CulinaryIcons.Sauce,
                label = stringResource(R.string.home_quick_sauce),
                onClick = onNewSauce,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = 88.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = CulinarySpacing.xxs, vertical = CulinarySpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** 知识库概览：三个真实在用档案数，各自可进入对应库。 */
@Composable
internal fun KnowledgeSection(
    state: HomeSection<HomeKnowledgeSummary>,
    onOpenSauces: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRecipes: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeCard(modifier = modifier) {
        SectionHeader(
            icon = CulinaryIcons.Recipe,
            title = stringResource(R.string.home_knowledge_title),
        )
        when (state) {
            HomeSection.Loading -> KnowledgeSkeleton()
            is HomeSection.Failed ->
                SectionError(
                    title = stringResource(R.string.home_knowledge_error),
                    message = state.message,
                    onRetry = onRetry,
                )

            is HomeSection.Ready ->
                if (state.value.isEmpty) {
                    HomeEmptyState(
                        icon = CulinaryIcons.Recipe,
                        title = stringResource(R.string.home_knowledge_empty_title),
                        body = stringResource(R.string.home_knowledge_empty_body),
                    )
                } else {
                    KnowledgeMetrics(
                        summary = state.value,
                        onOpenSauces = onOpenSauces,
                        onOpenIngredients = onOpenIngredients,
                        onOpenRecipes = onOpenRecipes,
                    )
                }
        }
    }
}

@Composable
private fun KnowledgeMetrics(
    summary: HomeKnowledgeSummary,
    onOpenSauces: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRecipes: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KnowledgeMetric(
            value = summary.sauceCount,
            label = stringResource(R.string.home_knowledge_sauces),
            onClick = onOpenSauces,
            modifier = Modifier.weight(1f),
        )
        MetricDivider()
        KnowledgeMetric(
            value = summary.ingredientCount,
            label = stringResource(R.string.home_knowledge_ingredients),
            onClick = onOpenIngredients,
            modifier = Modifier.weight(1f),
        )
        MetricDivider()
        KnowledgeMetric(
            value = summary.recipeCount,
            label = stringResource(R.string.home_knowledge_recipes),
            onClick = onOpenRecipes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KnowledgeMetric(
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val clickModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionIcon(icon = icon)
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun DetailsAction() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_action_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionIcon(icon: ImageVector) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SectionError(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(CulinarySpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun HomeEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        modifier = Modifier.fillMaxWidth().padding(CulinarySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = CulinarySpacing.xxs),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun InventorySkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(CulinarySpacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonBlock(width = 56.dp, height = 32.dp)
                    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                    SkeletonBlock(width = 44.dp, height = 10.dp)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(CulinarySpacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(3) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SkeletonBlock(width = 44.dp, height = 24.dp)
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                SkeletonBlock(width = 40.dp, height = 10.dp)
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 统一首页卡片：1dp 暖灰边框、16dp 圆角、无阴影。 */
@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}
