package com.culinary.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.culinary.R
import com.culinary.core.designsystem.icon.CulinaryIcons

/**
 * 应用根级路由（N3，2026-11-05 负责人正式修订）。
 *
 * 顺序固定：首页 → 知识库 → 扫码（中央凸起）→ 库存 → 设置。
 * 知识库聚合页通过 [LIBRARY_TAB_ROUTE] 承载酱汁/食材/菜谱三个 Tab；
 * 三个 feature 模块的内部路由不变。
 */
enum class CulinaryDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HOME_ROUTE, R.string.destination_home, CulinaryIcons.Home),
    LIBRARY(LIBRARY_ROUTE, R.string.destination_library, CulinaryIcons.Library),
    SCAN(SCAN_ROUTE, R.string.destination_scan, CulinaryIcons.Scan),
    INVENTORY(INVENTORY_ROUTE, R.string.destination_inventory, CulinaryIcons.Inventory),
    SETTINGS(SETTINGS_ROUTE, R.string.destination_settings, CulinaryIcons.Settings),
    ;

    /** 知识库的第二个目的地（带 Tab 参数）也属于本一级入口。 */
    fun matches(currentRoute: String?): Boolean =
        currentRoute == route ||
            (this == LIBRARY && currentRoute == LIBRARY_TAB_ROUTE)

    companion object {
        /** 应用启动页：N3 的第一项首页。 */
        val START: CulinaryDestination = HOME

        fun forRoute(currentRoute: String?): CulinaryDestination? = entries.firstOrNull { it.matches(currentRoute) }
    }
}

/** 首页：全局工作台，也是冷启动目的地。 */
const val HOME_ROUTE: String = "home"

/** 知识库聚合页（默认 Tab 为酱汁）。 */
const val LIBRARY_ROUTE: String = "library"

/** 知识库带 Tab 参数的路由；[LIBRARY_TAB_ARG] 取 sauces / ingredients / recipes。 */
const val LIBRARY_TAB_ARG: String = "tab"
const val LIBRARY_TAB_ROUTE: String = "library/{$LIBRARY_TAB_ARG}"

/** 扫码：底部导航中央凸起主按钮。 */
const val SCAN_ROUTE: String = "scan"

/** 库存：N3 第四项。 */
const val INVENTORY_ROUTE: String = "inventory"

/** 设置：N3 第五项，已从顶栏移到底部导航。 */
const val SETTINGS_ROUTE: String = "settings"

/** 应用冷启动目的地：首页。 */
const val APP_START_ROUTE: String = HOME_ROUTE
