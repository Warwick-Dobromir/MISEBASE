package com.culinary.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.R
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.MetricValue
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing

// 首页区块（MB-UI-001 Stage 3）。
//
// 三个区块共享同一套编辑式结构：
// 拉丁小标题 + 中文区块标题 + 1dp 细线 → 内容（指标 / 操作 / 空状态）。
// 数字统一使用 MetricValue，库存与知识库不再出现两种字号。

/** 库存概览：真实商品/批次/需关注数；空、加载、失败均局部处理。 */
@Composable
internal fun InventorySection(
    state: HomeSection<HomeInventorySummary>,
    onOpenInventory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_inventory_title),
            eyebrow = stringResource(R.string.home_inventory_eyebrow),
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            action =
                if (state is HomeSection.Ready) {
                    {
                        DetailsAction(onClick = onOpenInventory)
                    }
                } else {
                    null
                },
        )
        when (state) {
            HomeSection.Loading -> MetricSkeleton()
            is HomeSection.Failed ->
                SectionError(
                    title = stringResource(R.string.home_inventory_error),
                    message = state.message,
                    onRetry = onRetry,
                )

            is HomeSection.Ready ->
                if (state.value.isEmpty) {
                    EmptyState(
                        icon = CulinaryIcons.Inventory,
                        title = stringResource(R.string.home_inventory_empty_title),
                        description = stringResource(R.string.home_inventory_empty_body),
                        actionLabel = stringResource(R.string.home_inventory_empty_action),
                        onAction = onOpenReceipt,
                    )
                } else {
                    InventoryMetrics(
                        summary = state.value,
                        onOpenInventory = onOpenInventory,
                    )
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
    ) {
        MetricValue(
            value = summary.productCount.toString(),
            label = stringResource(R.string.home_inventory_products),
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
        MetricValue(
            value = summary.batchCount.toString(),
            label = stringResource(R.string.home_inventory_batches),
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
        MetricValue(
            value = summary.attentionCount.toString(),
            label = stringResource(R.string.home_inventory_attention),
            valueColor =
                if (summary.attentionCount > 0) {
                    CulinaryColorScheme.current.warning
                } else {
                    null
                },
            onClick = onOpenInventory,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailsAction(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .defaultMinSize(minHeight = 40.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_action_details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_quick_title),
            eyebrow = stringResource(R.string.home_quick_eyebrow),
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickAction(
                icon = CulinaryIcons.Scan,
                label = stringResource(R.string.home_quick_scan),
                onClick = onOpenScan,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            QuickAction(
                icon = CulinaryIcons.Ingredient,
                label = stringResource(R.string.home_quick_ingredient),
                onClick = onNewIngredient,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            QuickAction(
                icon = CulinaryIcons.Recipe,
                label = stringResource(R.string.home_quick_recipe),
                onClick = onNewRecipe,
                modifier = Modifier.weight(1f),
            )
            ActionDivider()
            QuickAction(
                icon = CulinaryIcons.Sauce,
                label = stringResource(R.string.home_quick_sauce),
                onClick = onNewSauce,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = 72.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionDivider() {
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 知识库概览：三个真实在用档案数，各自可进入对应库。 */
@Composable
internal fun KnowledgeSection(
    state: HomeSection<HomeKnowledgeSummary>,
    onOpenSauces: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRecipes: () -> Unit,
    onNewSauce: () -> Unit,
    onNewIngredient: () -> Unit,
    onNewRecipe: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.home_knowledge_title),
            eyebrow = stringResource(R.string.home_knowledge_eyebrow),
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
        )
        when (state) {
            HomeSection.Loading -> MetricSkeleton()
            is HomeSection.Failed ->
                SectionError(
                    title = stringResource(R.string.home_knowledge_error),
                    message = state.message,
                    onRetry = onRetry,
                )

            is HomeSection.Ready ->
                if (state.value.isEmpty) {
                    KnowledgeEmptyState(
                        onNewSauce = onNewSauce,
                        onNewIngredient = onNewIngredient,
                        onNewRecipe = onNewRecipe,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
    ) {
        MetricValue(
            value = summary.sauceCount.toString(),
            label = stringResource(R.string.home_knowledge_sauces),
            onClick = onOpenSauces,
            modifier = Modifier.weight(1f),
        )
        MetricValue(
            value = summary.ingredientCount.toString(),
            label = stringResource(R.string.home_knowledge_ingredients),
            onClick = onOpenIngredients,
            modifier = Modifier.weight(1f),
        )
        MetricValue(
            value = summary.recipeCount.toString(),
            label = stringResource(R.string.home_knowledge_recipes),
            onClick = onOpenRecipes,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 知识库空状态：说明三个库都为空，并给出三个真实的创建入口。
 *
 * 与库存空状态（单一“去入库”按钮）结构不同，因为知识库有三个独立业务入口。
 */
@Composable
private fun KnowledgeEmptyState(
    onNewSauce: () -> Unit,
    onNewIngredient: () -> Unit,
    onNewRecipe: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = CulinaryIcons.Library,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Text(
            text = stringResource(R.string.home_knowledge_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = stringResource(R.string.home_knowledge_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xxs)) {
            CulinaryTextButton(
                text = stringResource(R.string.home_quick_sauce),
                onClick = onNewSauce,
            )
            CulinaryTextButton(
                text = stringResource(R.string.home_quick_ingredient),
                onClick = onNewIngredient,
            )
            CulinaryTextButton(
                text = stringResource(R.string.home_quick_recipe),
                onClick = onNewRecipe,
            )
        }
    }
}

/** 区块级错误：标题 + 说明 + 重试；错误码不进入界面。 */
@Composable
private fun SectionError(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = CulinaryIcons.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = CulinaryColorScheme.current.danger,
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
        CulinaryTextButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
        )
    }
}

/** 指标骨架：保持三列布局，避免加载时跳版。 */
@Composable
private fun MetricSkeleton() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
    ) {
        repeat(3) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .height(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                Box(
                    modifier =
                        Modifier
                            .width(40.dp)
                            .height(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
            }
        }
    }
}
