package com.culinary.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.culinary.debug.DebugScreen
import com.culinary.ui.HomeScreen
import com.culinary.ui.PlaceholderScreen

private const val DEBUG_ROUTE = "debug"

/**
 * 应用导航图（R01 阶段 6 的“导航占位”）。
 *
 * 阶段 7+ 会把各 feature 的 Route 替换进来；当前统一使用占位页，
 * 不伪造业务功能。
 */
@Composable
fun CulinaryNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = CulinaryDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(CulinaryDestination.HOME.route) {
            HomeScreen(
                onOpen = { destination -> navController.navigate(destination.route) },
                onOpenDebug = { navController.navigate(DEBUG_ROUTE) },
            )
        }
        composable(DEBUG_ROUTE) {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        CulinaryDestination.entries
            .filter { it != CulinaryDestination.HOME }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(
                        title = destination.titleRes,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
    }
}
