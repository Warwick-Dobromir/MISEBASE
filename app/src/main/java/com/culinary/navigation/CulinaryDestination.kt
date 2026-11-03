package com.culinary.navigation

import androidx.annotation.StringRes
import com.culinary.R

/**
 * 导航目的地（单 Activity + Navigation Compose，A01）。
 *
 * 路由字符串是稳定契约：feature 模块的阶段 7+ 实现会绑定到这些路由，
 * 因此不要为了“好看”改名。
 */
enum class CulinaryDestination(
    val route: String,
    @StringRes val titleRes: Int,
) {
    HOME("home", R.string.destination_home),
    INGREDIENTS("ingredients", R.string.destination_ingredients),
    SAUCES("sauces", R.string.destination_sauces),
    RECIPES("recipes", R.string.destination_recipes),
    PRODUCTS("products", R.string.destination_products),
    INVENTORY("inventory", R.string.destination_inventory),
    PLANNING("planning", R.string.destination_planning),
    PROCUREMENT("procurement", R.string.destination_procurement),
    SETTINGS("settings", R.string.destination_settings),
}
