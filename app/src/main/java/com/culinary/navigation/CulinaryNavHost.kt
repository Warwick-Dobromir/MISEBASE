@file:Suppress("TooManyFunctions") // 导航图按功能域拆分 composable，集中注册必然超过函数阈值

package com.culinary.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.culinary.cooking.CookingModeRoute
import com.culinary.cooking.CookingRoute
import com.culinary.cooking.CookingSource
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.data.kitchen.KitchenReferenceType
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.domain.model.KitchenRecordKind
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
import com.culinary.home.HOME_PLACEHOLDER_ROUTE
import com.culinary.home.HOME_PLACEHOLDER_SECTION_ARG
import com.culinary.home.HOME_PURCHASE_DETAIL_ROUTE
import com.culinary.home.HOME_PURCHASE_ID_ARG
import com.culinary.home.HomePlaceholderRoute
import com.culinary.home.HomePlaceholderSection
import com.culinary.home.HomePurchasePlaceholderRoute
import com.culinary.home.HomeRoute
import com.culinary.home.homePlaceholderRoute
import com.culinary.home.homePurchaseDetailRoute
import com.culinary.kitchen.KitchenRoute
import com.culinary.kitchen.archive.ArchiveLibraryRoute
import com.culinary.kitchen.archive.KITCHEN_REFERENCE_DETAIL_ROUTE
import com.culinary.kitchen.archive.KITCHEN_REFERENCE_EDIT_ROUTE
import com.culinary.kitchen.archive.KITCHEN_REFERENCE_ID_ARG
import com.culinary.kitchen.archive.KITCHEN_REFERENCE_KIND_ARG
import com.culinary.kitchen.archive.KITCHEN_REFERENCE_NEW_ROUTE
import com.culinary.kitchen.archive.KitchenArchiveSection
import com.culinary.kitchen.archive.KitchenReferenceDetailRoute
import com.culinary.kitchen.archive.KitchenReferenceEditorRoute
import com.culinary.kitchen.archive.kitchenReferenceDetailRoute
import com.culinary.kitchen.archive.kitchenReferenceEditRoute
import com.culinary.kitchen.archive.kitchenReferenceNewRoute
import com.culinary.kitchen.archive.kitchenReferenceTypeFromRoute
import com.culinary.kitchen.archive.toArchiveSection
import com.culinary.kitchen.archive.toReferenceType
import com.culinary.settings.SettingsRoute

/**
 * 应用导航外壳。
 *
 * 底部五项：首页 / 厨房 / 扫码 / 库存 / 我的。
 * 厨房一级页自带品牌头；六个资料库页面保留底栏并显示“返回 + 厨房”顶栏。
 */
@Suppress("LongMethod", "CyclomaticComplexMethod", "ComplexCondition")
@Composable
fun CulinaryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = CulinaryDestination.forRoute(currentRoute)
    val isHome = currentRoute == HOME_ROUTE
    val isKitchen = currentRoute == LIBRARY_ROUTE
    val isKitchenArchive = currentRoute == LIBRARY_TAB_ROUTE
    val isSauceEditor = currentRoute == SaucesRoutes.EDIT
    val isCooking = currentRoute == CookingRoute.ROUTE
    val ownsTopBar = currentRoute == SaucesRoutes.DETAIL || currentRoute == RecipesRoutes.DETAIL
    val showBottomBar = topLevel != null
    val detailContext =
        when (currentRoute) {
            SaucesRoutes.DETAIL -> "酱汁库"
            IngredientsRoutes.DETAIL -> "食材库"
            RecipesRoutes.DETAIL -> "菜谱库"
            KITCHEN_REFERENCE_DETAIL_ROUTE ->
                when (
                    backStackEntry?.arguments?.getString(KITCHEN_REFERENCE_KIND_ARG)
                ) {
                    "TECHNIQUE", "technique" -> "烹饪技术"
                    "PRODUCTION_STANDARD", "production-standard" -> "制作标准"
                    "STORAGE_STANDARD", "storage-standard" -> "储存标准"
                    else -> "厨房"
                }
            else -> null
        }

    // 冷启动（新进程）一律回到首页；同一进程内的旋转/重建仍保留用户当前路由。
    LaunchedEffect(navController) {
        if (NavigationProcessState.markStarted()) {
            navController.resetToHomeIfRestored()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 首页、厨房首页、六库目录与酱汁编辑器都自带品牌/操作顶栏。
            if (!isHome && !isKitchen && !isKitchenArchive && !isSauceEditor && !isCooking && !ownsTopBar) {
                Column {
                    AppTopBar(
                        topLevel = topLevel,
                        showBack = (!showBottomBar && currentRoute != null) || isKitchenArchive,
                        title =
                            if (isKitchenArchive) {
                                stringResource(R.string.destination_kitchen)
                            } else {
                                detailContext
                            },
                        onOpenHome = { navController.openHome() },
                        onBack = { navController.popBackStack() },
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    topLevel: CulinaryDestination?,
    showBack: Boolean,
    onOpenHome: () -> Unit,
    onBack: () -> Unit,
    title: String? = null,
) {
    TopAppBar(
        title = {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        navigationIcon = {
            when {
                showBack ->
                    CulinaryIconButton(
                        icon = CulinaryIcons.Back,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )

                topLevel != null ->
                    CulinaryIconButton(
                        icon = CulinaryIcons.ChefHat,
                        contentDescription = stringResource(R.string.home_brand_home),
                        onClick = onOpenHome,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
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
        enterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
        exitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) },
        popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
        popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 120)) },
    ) {
        homeGraph(navController)
        homePlaceholderGraph()
        kitchenReferenceGraph(navController)
        libraryGraph(navController)
        sauceGraph(navController)
        ingredientGraph(navController)
        recipeGraph(navController)
        cookingGraph(navController)
        inventoryGraph(navController)
        scanGraph(navController)
        settingsGraph()
    }
}

private fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    composable(HOME_ROUTE) {
        HomeRoute(
            onOpenTimer = { navController.navigate(homePlaceholderRoute(HomePlaceholderSection.TIMER)) },
            onOpenSettings = { navController.navigateToTopLevel(SETTINGS_ROUTE) },
            onOpenAllPlans = { navController.navigate(homePlaceholderRoute(HomePlaceholderSection.PLANS)) },
            onAddPlan = { navController.navigate(homePlaceholderRoute(HomePlaceholderSection.PLAN_ADD)) },
            onNewPurchase = { navController.navigate(homePlaceholderRoute(HomePlaceholderSection.PURCHASE_ADD)) },
            onOpenAllPurchases = { navController.navigate(homePlaceholderRoute(HomePlaceholderSection.PURCHASES)) },
            onOpenPurchase = { purchaseId -> navController.navigate(homePurchaseDetailRoute(purchaseId)) },
        )
    }
}

/** 首页二级入口的明确占位页；本次不提前实现这些页面。 */
private fun NavGraphBuilder.homePlaceholderGraph() {
    composable(
        route = HOME_PLACEHOLDER_ROUTE,
        arguments =
            listOf(
                navArgument(HOME_PLACEHOLDER_SECTION_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
        val section =
            HomePlaceholderSection.fromRoute(
                entry.arguments?.getString(HOME_PLACEHOLDER_SECTION_ARG),
            ) ?: HomePlaceholderSection.TIMER
        HomePlaceholderRoute(section = section)
    }
    composable(
        route = HOME_PURCHASE_DETAIL_ROUTE,
        arguments =
            listOf(
                navArgument(HOME_PURCHASE_ID_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
        HomePurchasePlaceholderRoute(
            purchaseId = entry.arguments?.getString(HOME_PURCHASE_ID_ARG).orEmpty(),
        )
    }
}

/**
 * 厨房一级页与六个资料库页。
 *
 * `library` 显示厨房首页；`library/{tab}` 的 tab 取六个档案类型。
 * 资料库页共享统一骨架，底栏保持厨房选中；详情/编辑路由由各自能力处理。
 */
private fun NavGraphBuilder.libraryGraph(navController: NavHostController) {
    composable(LIBRARY_ROUTE) {
        KitchenRoute(
            onOpenCategory = { category ->
                navController.navigate("$LIBRARY_ROUTE/${category.toArchiveSection().routeValue}")
            },
        )
    }
    composable(
        route = LIBRARY_TAB_ROUTE,
        arguments =
            listOf(
                navArgument(LIBRARY_TAB_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
        val section =
            KitchenArchiveSection.fromRoute(entry.arguments?.getString(LIBRARY_TAB_ARG))
                ?: KitchenArchiveSection.SAUCES
        ArchiveLibraryRoute(
            section = section,
            onAdd = { navController.openArchiveAdd(section) },
            onOpenEntry = { archiveEntry -> navController.openArchiveEntry(archiveEntry) },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavHostController.openArchiveEntry(entry: KitchenArchiveEntry) {
    when (entry.kind) {
        KitchenArchiveKind.SAUCE -> navigate(SaucesRoutes.detail(entry.id))
        KitchenArchiveKind.INGREDIENT -> navigate(IngredientsRoutes.detail(entry.id))
        KitchenArchiveKind.RECIPE -> navigate(RecipesRoutes.detail(entry.id))
        KitchenArchiveKind.TECHNIQUE,
        KitchenArchiveKind.PRODUCTION_STANDARD,
        KitchenArchiveKind.STORAGE_STANDARD,
        ->
            entry.kind.toReferenceType()?.let { type ->
                navigate(kitchenReferenceDetailRoute(type, entry.id))
            }
    }
}

/** 关联跳转只复用既有详情路由，不引入新的页面层级。 */
private fun NavHostController.openKitchenRelated(
    kind: KitchenRecordKind,
    id: String,
) {
    when (kind) {
        KitchenRecordKind.SAUCE -> navigate(SaucesRoutes.detail(id))
        KitchenRecordKind.INGREDIENT -> navigate(IngredientsRoutes.detail(id))
        KitchenRecordKind.RECIPE -> navigate(RecipesRoutes.detail(id))
        KitchenRecordKind.TECHNIQUE ->
            navigate(kitchenReferenceDetailRoute(KitchenReferenceType.TECHNIQUE, id))

        KitchenRecordKind.PRODUCTION_STANDARD ->
            navigate(kitchenReferenceDetailRoute(KitchenReferenceType.PRODUCTION_STANDARD, id))

        KitchenRecordKind.STORAGE_STANDARD ->
            navigate(kitchenReferenceDetailRoute(KitchenReferenceType.STORAGE_STANDARD, id))
    }
}

private fun NavHostController.openArchiveAdd(section: KitchenArchiveSection) {
    when (section.kind) {
        KitchenArchiveKind.SAUCE -> navigate(SaucesRoutes.edit(null))
        KitchenArchiveKind.INGREDIENT -> navigate(IngredientsRoutes.edit(null))
        KitchenArchiveKind.RECIPE -> navigate(RecipesRoutes.edit(null))
        KitchenArchiveKind.TECHNIQUE,
        KitchenArchiveKind.PRODUCTION_STANDARD,
        KitchenArchiveKind.STORAGE_STANDARD,
        ->
            section.kind.toReferenceType()?.let { type ->
                navigate(kitchenReferenceNewRoute(type))
            }
    }
}

private fun NavGraphBuilder.kitchenReferenceGraph(navController: NavHostController) {
    composable(
        route = KITCHEN_REFERENCE_NEW_ROUTE,
        arguments =
            listOf(
                navArgument(KITCHEN_REFERENCE_KIND_ARG) { type = NavType.StringType },
            ),
    ) {
        KitchenReferenceEditorRoute(onSaved = { navController.popBackStack() })
    }
    composable(
        route = KITCHEN_REFERENCE_DETAIL_ROUTE,
        arguments =
            listOf(
                navArgument(KITCHEN_REFERENCE_KIND_ARG) { type = NavType.StringType },
                navArgument(KITCHEN_REFERENCE_ID_ARG) { type = NavType.StringType },
            ),
    ) { entry ->
        val type = kitchenReferenceTypeFromRoute(entry.arguments?.getString(KITCHEN_REFERENCE_KIND_ARG))
        val id = entry.arguments?.getString(KITCHEN_REFERENCE_ID_ARG).orEmpty()
        KitchenReferenceDetailRoute(
            onEdit = {
                if (type != null) {
                    navController.navigate(kitchenReferenceEditRoute(type, id))
                }
            },
            onArchived = { navController.popBackStack() },
            onOpenRelation = navController::openKitchenRelated,
        )
    }
    composable(
        route = KITCHEN_REFERENCE_EDIT_ROUTE,
        arguments =
            listOf(
                navArgument(KITCHEN_REFERENCE_KIND_ARG) { type = NavType.StringType },
                navArgument(KITCHEN_REFERENCE_ID_ARG) { type = NavType.StringType },
            ),
    ) {
        KitchenReferenceEditorRoute(onSaved = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.sauceGraph(navController: NavHostController) {
    composable(SaucesRoutes.LIST) {
        SaucesRoute(
            onOpenSauce = { sauceId -> navController.navigate(SaucesRoutes.detail(sauceId)) },
            onAddSauce = { navController.navigate(SaucesRoutes.edit(null)) },
        )
    }
    composable(SaucesRoutes.DETAIL) { entry ->
        val returnedStep by entry.savedStateHandle
            .getStateFlow<Int?>(DETAIL_RETURN_STEP_KEY, null)
            .collectAsStateWithLifecycle()
        SauceDetailRoute(
            onEdit = { sauceId -> navController.navigate(SaucesRoutes.edit(sauceId)) },
            onOpenRelation = navController::openKitchenRelated,
            onBack = { navController.popBackStack() },
            onStartTimer = { stepNo ->
                val sauceId = entry.arguments?.getString(SaucesRoutes.ARG_SAUCE_ID).orEmpty()
                navController.navigate(CookingRoute.route(CookingSource.SAUCE, sauceId, stepNo))
            },
            scrollToStep = returnedStep,
        )
    }
    composable(SaucesRoutes.EDIT) {
        SauceEditorRoute(
            onSaved = { navController.popBackStack() },
            onCancelled = { navController.popBackStack() },
            onOpenSauce = { sauceId -> navController.navigate(SaucesRoutes.detail(sauceId)) },
            onArchived = { navController.popBackStack() },
        )
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
            onOpenRelation = navController::openKitchenRelated,
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
    composable(RecipesRoutes.DETAIL) { entry ->
        val returnedStep by entry.savedStateHandle
            .getStateFlow<Int?>(DETAIL_RETURN_STEP_KEY, null)
            .collectAsStateWithLifecycle()
        RecipeDetailRoute(
            onEdit = { id -> navController.navigate(RecipesRoutes.edit(id)) },
            onOpenRelation = navController::openKitchenRelated,
            onBack = { navController.popBackStack() },
            onStartTimer = { stepNo ->
                val recipeId = entry.arguments?.getString(RecipesRoutes.ARG_RECIPE_ID).orEmpty()
                navController.navigate(CookingRoute.route(CookingSource.RECIPE, recipeId, stepNo))
            },
            scrollToStep = returnedStep,
        )
    }
    composable(RecipesRoutes.EDIT) {
        RecipeEditorRoute(onSaved = { navController.popBackStack() })
    }
}

/** 统一制作模式：从详情页的步骤进入，退出时把步骤序号带回详情页定位。 */
private fun NavGraphBuilder.cookingGraph(navController: NavHostController) {
    composable(
        route = CookingRoute.ROUTE,
        arguments =
            listOf(
                navArgument(CookingRoute.KIND_ARG) { type = NavType.StringType },
                navArgument(CookingRoute.ID_ARG) { type = NavType.StringType },
                navArgument(CookingRoute.STEP_ARG) { type = NavType.IntType },
            ),
    ) {
        CookingModeRoute(
            onExit = { stepNo ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(DETAIL_RETURN_STEP_KEY, stepNo)
                navController.popBackStack()
            },
        )
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

/**
 * 从任意页面进入五项一级入口。
 *
 * 一级入口必须可确定地打开自己的根页；这里不保存/恢复子页面状态，
 * 避免厨房根页与 `library/{tab}` 共用路由时被错误还原为上次的 Tab。
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
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
/** 制作模式返回详情页时携带的原始步骤序号（1 起）。 */
private const val DETAIL_RETURN_STEP_KEY: String = "detailReturnStep"

private object NavigationProcessState {
    private var started = false

    fun markStarted(): Boolean {
        if (started) return false
        started = true
        return true
    }
}
