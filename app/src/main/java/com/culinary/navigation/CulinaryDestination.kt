package com.culinary.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.culinary.R
import com.culinary.core.designsystem.icon.CulinaryIcons

/**
 * 五个底部一级入口（MB-D-009 方案 N1，顺序固定）。
 *
 * 设置不占底部栏，放在顶栏；路由字符串是稳定契约，不为了好看改名。
 * 顺序：酱汁 → 食材 → 菜谱 → 库存 → 扫码。
 */
enum class CulinaryDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    SAUCES("sauces", R.string.destination_sauces, CulinaryIcons.Sauce),
    INGREDIENTS("ingredients", R.string.destination_ingredients, CulinaryIcons.Ingredient),
    RECIPES("recipes", R.string.destination_recipes, CulinaryIcons.Recipe),
    INVENTORY("inventory", R.string.destination_inventory, CulinaryIcons.Inventory),
    SCAN("scan", R.string.destination_scan, CulinaryIcons.Scan),
    ;

    companion object {
        /** 启动页：N1 的第一个一级入口。 */
        val START: CulinaryDestination = SAUCES
    }
}

/** 顶栏设置入口；不属于底部一级导航。 */
const val SETTINGS_ROUTE: String = "settings"
