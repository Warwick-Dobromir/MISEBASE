package com.culinary.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.designsystem.component.BrandBar
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 独立首页路由（MB-HOME-001 / MB-UI-001 Stage 3）。
 *
 * 冷启动默认进入；返回前台时静默刷新真实数据。
 */
@Composable
fun HomeRoute(
    onOpenInventory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onOpenSauces: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenScan: () -> Unit,
    onNewIngredient: () -> Unit,
    onNewRecipe: () -> Unit,
    onNewSauce: () -> Unit,
    onOpenHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    HomeScreen(
        state = state,
        onOpenInventory = onOpenInventory,
        onOpenReceipt = onOpenReceipt,
        onOpenSauces = onOpenSauces,
        onOpenIngredients = onOpenIngredients,
        onOpenRecipes = onOpenRecipes,
        onOpenScan = onOpenScan,
        onNewIngredient = onNewIngredient,
        onNewRecipe = onNewRecipe,
        onNewSauce = onNewSauce,
        onOpenHome = onOpenHome,
        onRetryInventory = viewModel::retryInventory,
        onRetryKnowledge = viewModel::retryKnowledge,
        modifier = modifier,
    )
}

/**
 * 首页视觉结构（Editorial Minimalism + Swiss）：
 *
 * 字标/日期 → 大标题 → 库存指标 → 快捷操作 → 知识库指标 → 页脚。
 * 区块之间用 1dp 细线和 40dp 留白分节，不使用卡片、阴影或装饰性渐变。
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onOpenInventory: () -> Unit,
    onOpenReceipt: () -> Unit,
    onOpenSauces: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenScan: () -> Unit,
    onNewIngredient: () -> Unit,
    onNewRecipe: () -> Unit,
    onNewSauce: () -> Unit,
    onOpenHome: () -> Unit,
    onRetryInventory: () -> Unit,
    onRetryKnowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = CulinarySpacing.xl),
    ) {
        item {
            BrandBar(
                brand = stringResource(R.string.home_brand),
                trailing = state.todayLabel,
                onClick = onOpenHome,
            )
        }
        if (state.isRefreshing) {
            item { RefreshBar() }
        }
        item { HomeHero() }
        item {
            InventorySection(
                state = state.inventory,
                onOpenInventory = onOpenInventory,
                onOpenReceipt = onOpenReceipt,
                onRetry = onRetryInventory,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
        }
        item {
            QuickActionsSection(
                onOpenScan = onOpenScan,
                onNewIngredient = onNewIngredient,
                onNewRecipe = onNewRecipe,
                onNewSauce = onNewSauce,
                modifier = Modifier.padding(top = CulinarySpacing.section),
            )
        }
        item {
            KnowledgeSection(
                state = state.knowledge,
                onOpenSauces = onOpenSauces,
                onOpenIngredients = onOpenIngredients,
                onOpenRecipes = onOpenRecipes,
                onNewSauce = onNewSauce,
                onNewIngredient = onNewIngredient,
                onNewRecipe = onNewRecipe,
                onRetry = onRetryKnowledge,
                modifier = Modifier.padding(top = CulinarySpacing.section),
            )
        }
        item { HomeFooter(modifier = Modifier.padding(top = CulinarySpacing.section)) }
    }
}

/** 刷新反馈：2dp 细进度条，不遮挡内容，也不替换已有数据。 */
@Composable
private fun RefreshBar() {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = Color.Transparent,
    )
}

@Composable
private fun HomeHero() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.xl,
                    bottom = CulinarySpacing.section,
                ),
    ) {
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = stringResource(R.string.home_headline),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .width(CulinarySpacing.xl)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.secondary),
            )
            Spacer(modifier = Modifier.width(CulinarySpacing.sm))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CulinaryRule()
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
