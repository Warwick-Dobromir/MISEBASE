package com.culinary.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.culinary.home.HomeRoute
import com.culinary.library.LibraryRoute
import com.culinary.library.LibraryTab
import com.culinary.settings.SettingsRoute

/**
 * 应用导航外壳（N3，MB-HOME-002）。
 *
 * 底部五项：首页 / 知识库 / 扫码（凸起）/ 库存 / 设置。
 * 知识库是外层聚合页，内部 Tab 复用三个独立 feature；设置已从顶栏移到底部。
 */
@Composable
fun CulinaryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = CulinaryDestination.forRoute(currentRoute)
    val isHome = currentRoute == HOME_ROUTE
    val showBottomBar = topLevel != null

    // 冷启动（新进程）一律回到首页；同一进程内的旋转/重建仍保留用户当前路由。
    LaunchedEffect(navController) {
        if (NavigationProcessState.markStarted()) {
            navController.resetToHomeIfRestored()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 首页自带品牌区；其他页面保留返回/品牌 + 标题。
            if (!isHome) {
                AppTopBar(
                    currentRoute = currentRoute,
                    topLevel = topLevel,
                    showBack = !showBottomBar && currentRoute != null,
                    onOpenHome = { navController.openHome() },
                    onBack = { navController.popBackStack() },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                CulinaryBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination -> navController.navigateToTopLevel(destination.route) },
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
    onOpenHome: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = titleFor(currentRoute = currentRoute, topLevel = topLevel)) },
        navigationIcon = {
            when {
                showBack ->
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = CulinaryIcons.Back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }

                topLevel != null ->
                    IconButton(onClick = onOpenHome) {
                        Icon(
                            imageVector = CulinaryIcons.ChefHat,
                            contentDescription = stringResource(R.string.home_brand_home),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = APP_START_ROUTE,
        modifier = modifier,
    ) {
        homeGraph(navController)
        libraryGraph(navController)
        sauceGraph(navController)
        ingredientGraph(navController)
        recipeGraph(navController)
        inventoryGraph(navController)
        scanGraph(navController)
        settingsGraph()
    }
}

private fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    composable(HOME_ROUTE) {
        HomeRoute(
            onOpenInventory = { navController.navigateToTopLevel(CulinaryDestination.INVENTORY.route) },
            onOpenReceipt = { navController.navigate(InventoryRoutes.receipt()) },
            onOpenSauces = { navController.navigateToTopLevel(libraryRoute(LibraryTab.SAUCES)) },
            onOpenIngredients = { navController.navigateToTopLevel(libraryRoute(LibraryTab.INGREDIENTS)) },
            onOpenRecipes = { navController.navigateToTopLevel(libraryRoute(LibraryTab.RECIPES)) },
            onOpenScan = { navController.navigateToTopLevel(CulinaryDestination.SCAN.route) },
            onNewIngredient = { navController.navigate(IngredientsRoutes.edit(null)) },
            onNewRecipe = { navController.navigate(RecipesRoutes.edit(null)) },
            onNewSauce = { navController.navigate(SaucesRoutes.edit(null)) },
            onOpenHome = { navController.openHome() },
        )
    }
}

/**
 * 知识库聚合页：`library` 为默认（酱汁）Tab，`library/{tab}` 供首页数字直达。
 *
 * 两种目的地都复用 [LibraryRoute]；底层仍是三个独立 feature 的列表 Route。
 */
private fun NavGraphBuilder.libraryGraph(navController: NavHostController) {
    composable(LIBRARY_ROUTE) {
        libraryDestination(navController = navController, initialTab = LibraryTab.SAUCES)
    }
    composable(
        route = LIBRARY_TAB_ROUTE,
        arguments =
            listOf(
                navArgument(LIBRARY_TAB_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
        val tab = LibraryTab.fromRoute(entry.arguments?.getString(LIBRARY_TAB_ARG)) ?: LibraryTab.SAUCES
        libraryDestination(navController = navController, initialTab = tab)
    }
}

@Composable
private fun libraryDestination(
    navController: NavHostController,
    initialTab: LibraryTab,
) {
    LibraryRoute(
        initialTab = initialTab,
        onOpenSauce = { sauceId -> navController.navigate(SaucesRoutes.detail(sauceId)) },
        onAddSauce = { navController.navigate(SaucesRoutes.edit(null)) },
        onOpenIngredient = { id -> navController.navigate(IngredientsRoutes.detail(id)) },
        onAddIngredient = { navController.navigate(IngredientsRoutes.edit(null)) },
        onOpenRecipe = { id -> navController.navigate(RecipesRoutes.detail(id)) },
        onAddRecipe = { navController.navigate(RecipesRoutes.edit(null)) },
    )
}

private fun libraryRoute(tab: LibraryTab): String = "$LIBRARY_ROUTE/${tab.route}"

private fun NavGraphBuilder.sauceGraph(navController: NavHostController) {
    composable(SaucesRoutes.LIST) {
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
    composable(IngredientsRoutes.LIST) {
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
    composable(RecipesRoutes.LIST) {
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
    composable(INVENTORY_ROUTE) {
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
 * 扫码入口（N3 中央凸起按钮）。
 *
 * 识别结果通过路由参数交给确认页；确认页再调用 `feature:scan` 的 ViewModel，
 * 由 ViewModel 调用 `feature:products` / `feature:inventory` 的接口，app 不参与业务规则。
 */
private fun NavGraphBuilder.scanGraph(navController: NavHostController) {
    composable(SCAN_ROUTE) {
        ScanRoute(
            onCapture = { capture -> navController.navigate(ScanRoutes.confirm(capture)) },
            onBack = { navController.backOrStart(APP_START_ROUTE) },
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
        SettingsRoute()
    }
}

/** 一级入口没有可返回的页面时回到首页，避免"返回"变成无响应。 */
private fun NavHostController.backOrStart(startRoute: String) {
    if (!popBackStack()) {
        navigate(startRoute) { launchSingleTop = true }
    }
}

/** 从首页或任意一级页面进入 N3 一级入口，保持五入口的保存/恢复语义。 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 品牌标识返回首页；已在首页时只做 singleTop，不新建副本。 */
private fun NavHostController.openHome() {
    navigate(HOME_ROUTE) {
        popUpTo(HOME_ROUTE) { inclusive = false }
        launchSingleTop = true
    }
}

/**
 * 恢复的系统任务可能停在业务页；新进程第一次进入时回到首页。
 *
 * 同一进程内的配置变更不触发，因为 [NavigationProcessState] 已标记启动。
 */
private fun NavHostController.resetToHomeIfRestored() {
    if (currentDestination?.route == HOME_ROUTE) return
    if (popBackStack(HOME_ROUTE, inclusive = false)) return
    navigate(HOME_ROUTE) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/** 进程级一次性标记；进程被杀后自然重置。 */
private object NavigationProcessState {
    private var started = false

    fun markStarted(): Boolean {
        if (started) return false
        started = true
        return true
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
        currentRoute.startsWith(SCAN_ROUTE) -> stringResource(R.string.destination_scan)
        else -> stringResource(R.string.app_name)
    }
