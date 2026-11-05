package com.culinary.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 图标体系（MB-UI-001 §3.8）。
 *
 * - 功能图标全部来自 **Material Symbols Outlined**（Apache-2.0），24dp 视口、
 *   统一的字形描边宽度；填充路径由 [Icon] 着色，选中态只改颜色 + 下划线，
 *   不做线性/填充混用，避免导航切换时视觉跳动。
 * - 品牌标记 [ChefHat] 是唯一的例外：它独立于功能图标设计，使用更柔和的光学线宽。
 * - 新增图标必须补进 `tools/icon_pipeline/convert_symbols.py` 并重新生成
 *   [MaterialSymbolPaths]，不得在页面内手绘临时图标。
 *
 * 来源与许可证：核心模块 `NOTICE`（Material Symbols, Apache License 2.0）。
 */
object CulinaryIcons {
    // ---- 导航 ----
    val Home: ImageVector by lazy { filledIcon("Culinary.Home") { homeSymbol() } }
    val Library: ImageVector by lazy { filledIcon("Culinary.Library") { librarySymbol() } }
    val Inventory: ImageVector by lazy { filledIcon("Culinary.Inventory") { inventorySymbol() } }
    val Scan: ImageVector by lazy { filledIcon("Culinary.Scan") { scanSymbol() } }
    val Settings: ImageVector by lazy { filledIcon("Culinary.Settings") { settingsSymbol() } }

    // ---- 知识库 ----
    val Ingredient: ImageVector by lazy { filledIcon("Culinary.Ingredient") { ingredientSymbol() } }
    val Sauce: ImageVector by lazy { filledIcon("Culinary.Sauce") { sauceSymbol() } }
    val Recipe: ImageVector by lazy { filledIcon("Culinary.Recipe") { recipeSymbol() } }

    // ---- 操作 ----
    val Add: ImageVector by lazy { filledIcon("Culinary.Add") { addSymbol() } }
    val Back: ImageVector by lazy { filledIcon("Culinary.Back") { backSymbol() } }
    val Edit: ImageVector by lazy { filledIcon("Culinary.Edit") { editSymbol() } }
    val Check: ImageVector by lazy { filledIcon("Culinary.Check") { checkSymbol() } }
    val CheckCircle: ImageVector by lazy { filledIcon("Culinary.CheckCircle") { checkCircleSymbol() } }
    val Close: ImageVector by lazy { filledIcon("Culinary.Close") { closeSymbol() } }
    val More: ImageVector by lazy { filledIcon("Culinary.More") { moreSymbol() } }
    val Search: ImageVector by lazy { filledIcon("Culinary.Search") { searchSymbol() } }
    val Filter: ImageVector by lazy { filledIcon("Culinary.Filter") { filterSymbol() } }
    val Sort: ImageVector by lazy { filledIcon("Culinary.Sort") { sortSymbol() } }
    val Archive: ImageVector by lazy { filledIcon("Culinary.Archive") { archiveSymbol() } }
    val Trash: ImageVector by lazy { filledIcon("Culinary.Trash") { trashSymbol() } }
    val ArrowForward: ImageVector by lazy { filledIcon("Culinary.ArrowForward") { arrowForwardSymbol() } }
    val History: ImageVector by lazy { filledIcon("Culinary.History") { historySymbol() } }

    // ---- 方向 ----
    val ChevronRight: ImageVector by lazy { filledIcon("Culinary.ChevronRight") { chevronRightSymbol() } }
    val ChevronDown: ImageVector by lazy { filledIcon("Culinary.ChevronDown") { chevronDownSymbol() } }

    // ---- 状态 ----
    val Warning: ImageVector by lazy { filledIcon("Culinary.Warning") { warningSymbol() } }
    val Info: ImageVector by lazy { filledIcon("Culinary.Info") { infoSymbol() } }
    val Bolt: ImageVector by lazy { filledIcon("Culinary.Bolt") { boltSymbol() } }
    val Flash: ImageVector by lazy { filledIcon("Culinary.Flash") { flashSymbol() } }

    // ---- 品牌 ----

    /** 品牌标记：厨师帽轮廓，用于首页与顶栏返回首页；不参与功能图标规范。 */
    val ChefHat: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Culinary.ChefHat",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = { chefHat() },
                )
            }.build()
    }
}

/** 功能图标统一装配：填充路径 + 24dp 视口，颜色由 `Icon(tint = …)` 决定。 */
private fun filledIcon(
    name: String,
    block: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathBuilder = block,
            )
        }.build()

/** 品牌标记轮廓：厨师帽。 */
private fun PathBuilder.chefHat() {
    moveTo(6.5f, 9.5f)
    curveTo(4.8f, 9.1f, 3.5f, 7.7f, 3.5f, 6f)
    curveTo(3.5f, 4f, 5.1f, 2.5f, 7f, 2.5f)
    curveTo(7.9f, 2.5f, 8.7f, 2.9f, 9.3f, 3.5f)
    curveTo(9.9f, 2.8f, 10.9f, 2.2f, 12f, 2.2f)
    curveTo(13.1f, 2.2f, 14.1f, 2.8f, 14.7f, 3.5f)
    curveTo(15.3f, 2.9f, 16.1f, 2.5f, 17f, 2.5f)
    curveTo(18.9f, 2.5f, 20.5f, 4f, 20.5f, 6f)
    curveTo(20.5f, 7.7f, 19.2f, 9.1f, 17.5f, 9.5f)
    lineTo(17.5f, 18.5f)
    curveTo(17.5f, 19.6f, 16.6f, 20.5f, 15.5f, 20.5f)
    lineTo(8.5f, 20.5f)
    curveTo(7.4f, 20.5f, 6.5f, 19.6f, 6.5f, 18.5f)
    close()
    moveTo(6.5f, 15.5f)
    lineTo(17.5f, 15.5f)
}
