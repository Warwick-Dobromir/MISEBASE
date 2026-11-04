package com.culinary.navigation

import androidx.annotation.StringRes
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.culinary.R
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.feature.ingredients.IngredientDetailRoute
import com.culinary.feature.ingredients.IngredientEditorRoute
import com.culinary.feature.ingredients.IngredientsRoute
import com.culinary.feature.ingredients.IngredientsRoutes
import com.culinary.feature.recipes.RecipeDetailRoute
import com.culinary.feature.recipes.RecipeEditorRoute
import com.culinary.feature.recipes.RecipesRoute
import com.culinary.feature.recipes.RecipesRoutes
import com.culinary.feature.sauces.SauceDetailRoute
import com.culinary.feature.sauces.SauceEditorRoute
import com.culinary.feature.sauces.SaucesRoute
import com.culinary.feature.sauces.SaucesRoutes
import com.culinary.ui.PlaceholderScreen

/**
 * 应用导航外壳（MB-D-009 方案 N1）。
 *
 * 结构：顶栏（页面标题 + 设置）+ 内容 + 底部五个一级入口。
 * 一级入口顺序固定；库存与扫码在本阶段只显示占位内容，不实现业务。
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
private fun AppTopBar(
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
        composable(CulinaryDestination.INVENTORY.route) {
            PlaceholderScreen(
                title = stringResource(R.string.destination_inventory),
                description = stringResource(R.string.placeholder_inventory_body),
            )
        }
        composable(CulinaryDestination.SCAN.route) {
            PlaceholderScreen(
                title = stringResource(R.string.destination_scan),
                description = stringResource(R.string.placeholder_scan_body),
            )
        }
        composable(SETTINGS_ROUTE) {
            PlaceholderScreen(
                title = stringResource(R.string.destination_settings),
                description = stringResource(R.string.placeholder_settings_body),
            )
        }
    }
}

@Composable
private fun CulinaryBottomBar(
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
        currentRoute == SETTINGS_ROUTE -> stringResource(R.string.destination_settings)
        else -> stringResource(R.string.app_name)
    }

/** 功能入口占位：各 feature 接入真实页面时替换。 */
@Composable
private fun FeaturePlaceholder(
    @StringRes titleRes: Int,
) {
    PlaceholderScreen(
        title = stringResource(titleRes),
        description = stringResource(R.string.placeholder_feature_body),
    )
}
