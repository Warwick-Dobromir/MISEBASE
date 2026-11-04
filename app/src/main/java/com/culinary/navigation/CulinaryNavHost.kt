package com.culinary.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.culinary.R
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.feature.ingredients.IngredientDetailRoute
import com.culinary.feature.ingredients.IngredientEditorRoute
import com.culinary.feature.ingredients.IngredientsRoute
import com.culinary.feature.ingredients.IngredientsRoutes
import com.culinary.feature.inventory.InventoryDetailRoute
import com.culinary.feature.inventory.InventoryLedgerRoute
import com.culinary.feature.inventory.InventoryRoute
import com.culinary.feature.inventory.InventoryRoutes
import com.culinary.feature.inventory.RequirementPlanRoute
import com.culinary.feature.inventory.StockMovementRoute
import com.culinary.feature.recipes.RecipeDetailRoute
import com.culinary.feature.recipes.RecipeEditorRoute
import com.culinary.feature.recipes.RecipesRoute
import com.culinary.feature.recipes.RecipesRoutes
import com.culinary.feature.sauces.SauceDetailRoute
import com.culinary.feature.sauces.SauceEditorRoute
import com.culinary.feature.sauces.SaucesRoute
import com.culinary.feature.sauces.SaucesRoutes
import com.culinary.feature.scan.ScanConfirmRoute
import com.culinary.feature.scan.ScanRoute
import com.culinary.feature.scan.ScanRoutes
import com.culinary.ui.PlaceholderScreen

/**
 * 应用导航外壳（MB-D-009 方案 N1）。
 *
 * 结构：顶栏（页面标题 + 设置）+ 内容 + 底部五个一级入口。
 * 一级入口顺序固定；库存为 Stage 4 真实页面，扫码为 Stage 5 真实页面
 * （MB-D-004 ACCEPTED：扫码 UI 归 `feature:scan`，识别结果经 core:domain 端口交给业务模块）。
 */
@Composable
fun CulinaryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = CulinaryDestination.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = topLevel != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                currentRoute = currentRoute,
                topLevel = topLevel,
                showBack = !showBottomBar && currentRoute != null,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        },
        bottomBar = {
            if (showBottomBar) {
                CulinaryBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        AppNavGraph(
            navController = navController,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    currentRoute: String?,
    topLevel: CulinaryDestination?,
    showBack: Boolean,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = titleFor(currentRoute = currentRoute, topLevel = topLevel)) },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = CulinaryIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = CulinaryIcons.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = CulinaryDestination.START.route,
        modifier = modifier,
    ) {
        sauceGraph(navController)
        ingredientGraph(navController)
        recipeGraph(navController)
        inventoryGraph(navController)
        scanGraph(navController)
        settingsGraph()
    }
}

private fun NavGraphBuilder.sauceGraph(navController: NavHostController) {
    composable(CulinaryDestination.SAUCES.route) {
        SaucesRoute(
            onOpenSauce = { sauceId -> navController.navigate(SaucesRoutes.detail(sauceId)) },
            onAddSauce = { navController.navigate(SaucesRoutes.edit(null)) },
        )
    }
    composable(SaucesRoutes.DETAIL) {
        SauceDetailRoute(
            onEdit = { sauceId -> navController.navigate(SaucesRoutes.edit(sauceId)) },
        )
    }
    composable(SaucesRoutes.EDIT) {
        SauceEditorRoute(onSaved = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.ingredientGraph(navController: NavHostController) {
    composable(CulinaryDestination.INGREDIENTS.route) {
        IngredientsRoute(
            onOpenIngredient = { id -> navController.navigate(IngredientsRoutes.detail(id)) },
            onAddIngredient = { navController.navigate(IngredientsRoutes.edit(null)) },
        )
    }
    composable(IngredientsRoutes.DETAIL) {
        IngredientDetailRoute(
            onEdit = { id -> navController.navigate(IngredientsRoutes.edit(id)) },
            onArchived = { navController.popBackStack() },
        )
    }
    composable(IngredientsRoutes.EDIT) {
        IngredientEditorRoute(onSaved = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.recipeGraph(navController: NavHostController) {
    composable(CulinaryDestination.RECIPES.route) {
        RecipesRoute(
            onOpenRecipe = { id -> navController.navigate(RecipesRoutes.detail(id)) },
            onAddRecipe = { navController.navigate(RecipesRoutes.edit(null)) },
        )
    }
    composable(RecipesRoutes.DETAIL) {
        RecipeDetailRoute(
            onEdit = { id -> navController.navigate(RecipesRoutes.edit(id)) },
        )
    }
    composable(RecipesRoutes.EDIT) {
        RecipeEditorRoute(onSaved = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.inventoryGraph(navController: NavHostController) {
    composable(CulinaryDestination.INVENTORY.route) {
        InventoryRoute(
            onOpenProduct = { productId -> navController.navigate(InventoryRoutes.product(productId)) },
            onReceipt = { navController.navigate(InventoryRoutes.receipt()) },
            onOpenLedger = { navController.navigate(InventoryRoutes.ledger()) },
            onOpenPlan = { navController.navigate(InventoryRoutes.plan()) },
        )
    }
    composable(InventoryRoutes.PRODUCT) {
        InventoryDetailRoute(
            onIssueBatch = { batchId -> navController.navigate(InventoryRoutes.issue(batchId)) },
            onAdjustBatch = { batchId -> navController.navigate(InventoryRoutes.adjust(batchId)) },
        )
    }
    composable(InventoryRoutes.LEDGER) {
        InventoryLedgerRoute()
    }
    composable(InventoryRoutes.MOVEMENT) {
        StockMovementRoute(onSaved = { navController.popBackStack() })
    }
    composable(InventoryRoutes.PLAN) {
        RequirementPlanRoute()
    }
}

/**
 * 扫码入口（N1 第五入口，MB-D-004）。
 *
 * 识别结果通过路由参数交给确认页；确认页再调用 `feature:scan` 的 ViewModel，
 * 由 ViewModel 调用 `feature:products` / `feature:inventory` 的接口，app 不参与业务规则。
 */
private fun NavGraphBuilder.scanGraph(navController: NavHostController) {
    composable(CulinaryDestination.SCAN.route) {
        ScanRoute(
            onCapture = { capture -> navController.navigate(ScanRoutes.confirm(capture)) },
            onBack = { navController.backOrStart(CulinaryDestination.START.route) },
        )
    }
    composable(
        route = ScanRoutes.CONFIRM,
        arguments =
            listOf(
                navArgument(ScanRoutes.ARG_CODE) { type = NavType.StringType },
                navArgument(ScanRoutes.ARG_FORMAT) { type = NavType.StringType },
                navArgument(ScanRoutes.ARG_SOURCE) { type = NavType.StringType },
            ),
    ) {
        ScanConfirmRoute(onBackToScan = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.settingsGraph() {
    composable(SETTINGS_ROUTE) {
        PlaceholderScreen(
            title = stringResource(R.string.destination_settings),
            description = stringResource(R.string.placeholder_settings_body),
        )
    }
}

/** 一级入口没有可返回的页面时回到启动入口，避免"返回"变成无响应。 */
private fun NavHostController.backOrStart(startRoute: String) {
    if (!popBackStack()) {
        navigate(startRoute) { launchSingleTop = true }
    }
}

@Composable
internal fun CulinaryBottomBar(
    currentRoute: String?,
    onSelect: (CulinaryDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        CulinaryDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun titleFor(
    currentRoute: String?,
    topLevel: CulinaryDestination?,
): String =
    when {
        topLevel != null -> stringResource(topLevel.labelRes)
        currentRoute == null -> stringResource(R.string.app_name)
        currentRoute.startsWith("sauces") -> stringResource(R.string.destination_sauces)
        currentRoute.startsWith("ingredients") -> stringResource(R.string.destination_ingredients)
        currentRoute.startsWith("recipes") -> stringResource(R.string.destination_recipes)
        currentRoute.startsWith("inventory") -> stringResource(R.string.destination_inventory)
        currentRoute == ScanRoutes.CONFIRM -> stringResource(R.string.scan_confirm_title)
        currentRoute.startsWith(CulinaryDestination.SCAN.route) -> stringResource(R.string.destination_scan)
        currentRoute == SETTINGS_ROUTE -> stringResource(R.string.destination_settings)
        else -> stringResource(R.string.app_name)
    }
