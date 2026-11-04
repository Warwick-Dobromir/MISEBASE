package com.culinary.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.culinary.feature.ingredients.IngredientsRoute
import com.culinary.feature.recipes.RecipesRoute
import com.culinary.feature.sauces.SaucesRoute

/**
 * 知识库聚合路由。
 *
 * 三个 Tab 直接复用既有 feature 列表 Route，因此底层 ViewModel、Repository、
 * 详情/编辑路由都不需要改动；切换 Tab 只改变外层容器内容。
 */
@Composable
fun LibraryRoute(
    initialTab: LibraryTab,
    onOpenSauce: (String) -> Unit,
    onAddSauce: () -> Unit,
    onOpenIngredient: (String) -> Unit,
    onAddIngredient: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAddRecipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRoute by rememberSaveable(initialTab) { mutableStateOf(initialTab.route) }
    val selectedTab = LibraryTab.fromRoute(selectedRoute) ?: initialTab

    LibraryScreen(
        selectedTab = selectedTab,
        onSelectTab = { selectedRoute = it.route },
        modifier = modifier,
    ) { tab ->
        when (tab) {
            LibraryTab.SAUCES ->
                SaucesRoute(
                    onOpenSauce = onOpenSauce,
                    onAddSauce = onAddSauce,
                    modifier = Modifier.fillMaxSize(),
                )

            LibraryTab.INGREDIENTS ->
                IngredientsRoute(
                    onOpenIngredient = onOpenIngredient,
                    onAddIngredient = onAddIngredient,
                    modifier = Modifier.fillMaxSize(),
                )

            LibraryTab.RECIPES ->
                RecipesRoute(
                    onOpenRecipe = onOpenRecipe,
                    onAddRecipe = onAddRecipe,
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}
