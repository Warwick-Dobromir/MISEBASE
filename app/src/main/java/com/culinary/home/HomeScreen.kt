package com.culinary.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 独立首页路由（MB-HOME-001）。
 *
 * 冷启动默认进入；不属于底部五个一级入口。返回前台时静默刷新真实数据。
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
        verticalArrangement = Arrangement.spacedBy(CulinarySpacing.md),
    ) {
        item {
            HomeHero(
                todayLabel = state.todayLabel,
                onOpenHome = onOpenHome,
            )
        }
        item {
            InventorySection(
                state = state.inventory,
                onOpenInventory = onOpenInventory,
                onOpenReceipt = onOpenReceipt,
                onRetry = onRetryInventory,
                modifier = Modifier.padding(horizontal = CulinarySpacing.md),
            )
        }
        item {
            QuickActionsSection(
                onOpenScan = onOpenScan,
                onNewIngredient = onNewIngredient,
                onNewRecipe = onNewRecipe,
                onNewSauce = onNewSauce,
                modifier = Modifier.padding(horizontal = CulinarySpacing.md),
            )
        }
        item {
            KnowledgeSection(
                state = state.knowledge,
                onOpenSauces = onOpenSauces,
                onOpenIngredients = onOpenIngredients,
                onOpenRecipes = onOpenRecipes,
                onRetry = onRetryKnowledge,
                modifier = Modifier.padding(horizontal = CulinarySpacing.md),
            )
        }
        item {
            HomeFooter(modifier = Modifier.padding(horizontal = CulinarySpacing.md))
        }
    }
}

@Composable
private fun HomeHero(
    todayLabel: String,
    onOpenHome: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Icon(
            imageVector = CulinaryIcons.ChefHat,
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(200.dp)
                    .padding(top = CulinarySpacing.sm)
                    .padding(end = CulinarySpacing.sm),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.md)
                    .padding(top = CulinarySpacing.xs, bottom = CulinarySpacing.sm),
        ) {
            BrandRow(onOpenHome = onOpenHome)
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            HeroGreeting(todayLabel = todayLabel)
        }
    }
}

@Composable
private fun HeroGreeting(todayLabel: String) {
    Text(
        text = stringResource(R.string.home_greeting),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.home_headline),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.xxs),
    )
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(CulinarySpacing.lg)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Text(
            text = todayLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun BrandRow(onOpenHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(CulinarySpacing.sm))
                    .clickable(role = Role.Button, onClick = onOpenHome)
                    .defaultMinSize(minHeight = CulinarySpacing.xxl)
                    .padding(end = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
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
                    imageVector = CulinaryIcons.ChefHat,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(CulinarySpacing.sm))
            Text(
                text = stringResource(R.string.home_brand),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.6.sp,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HomeFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
