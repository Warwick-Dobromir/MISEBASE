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
 * 线性图标集（UI 设计书 §7：Material Symbols Outlined 风格，线宽统一）。
 *
 * 不引入第三方图标依赖：五个一级入口 + 顶栏设置 + 通用操作共 11 个图标，
 * 用 24dp 视口手绘，保证线宽与视觉重量一致。
 */
object CulinaryIcons {
    val Sauce: ImageVector by lazy { lineIcon("Culinary.Sauce") { sauceBottle() } }
    val Ingredient: ImageVector by lazy { lineIcon("Culinary.Ingredient") { leaf() } }
    val Recipe: ImageVector by lazy { lineIcon("Culinary.Recipe") { book() } }
    val Inventory: ImageVector by lazy { lineIcon("Culinary.Inventory") { box() } }
    val Scan: ImageVector by lazy { lineIcon("Culinary.Scan") { scanFrame() } }
    val Settings: ImageVector by lazy { lineIcon("Culinary.Settings") { sliders() } }
    val Add: ImageVector by lazy { lineIcon("Culinary.Add") { plus() } }
    val Back: ImageVector by lazy { lineIcon("Culinary.Back") { arrowBack() } }
    val Edit: ImageVector by lazy { lineIcon("Culinary.Edit") { pencil() } }
    val Check: ImageVector by lazy { lineIcon("Culinary.Check") { check() } }
    val ChevronRight: ImageVector by lazy { lineIcon("Culinary.ChevronRight") { chevron() } }
}

private fun lineIcon(
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
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

private fun PathBuilder.sauceBottle() {
    moveTo(9f, 3f)
    lineTo(15f, 3f)
    lineTo(15f, 6f)
    lineTo(16.5f, 8f)
    lineTo(16.5f, 20f)
    lineTo(7.5f, 20f)
    lineTo(7.5f, 8f)
    lineTo(9f, 6f)
    close()
    moveTo(7.5f, 12f)
    lineTo(16.5f, 12f)
}

private fun PathBuilder.leaf() {
    moveTo(5f, 19f)
    lineTo(5f, 11f)
    lineTo(9f, 10f)
    lineTo(12f, 6f)
    lineTo(15f, 10f)
    lineTo(19f, 11f)
    lineTo(19f, 19f)
    close()
    moveTo(12f, 6f)
    lineTo(12f, 19f)
}

private fun PathBuilder.book() {
    moveTo(5f, 4f)
    lineTo(16f, 4f)
    lineTo(19f, 7f)
    lineTo(19f, 20f)
    lineTo(5f, 20f)
    close()
    moveTo(16f, 4f)
    lineTo(16f, 7f)
    lineTo(19f, 7f)
    moveTo(8f, 12f)
    lineTo(16f, 12f)
    moveTo(8f, 16f)
    lineTo(14f, 16f)
}

private fun PathBuilder.box() {
    moveTo(4f, 8f)
    lineTo(12f, 4f)
    lineTo(20f, 8f)
    lineTo(20f, 17f)
    lineTo(12f, 21f)
    lineTo(4f, 17f)
    close()
    moveTo(4f, 8f)
    lineTo(12f, 12f)
    lineTo(20f, 8f)
    moveTo(12f, 12f)
    lineTo(12f, 21f)
}

private fun PathBuilder.scanFrame() {
    moveTo(4f, 9f)
    lineTo(4f, 4f)
    lineTo(9f, 4f)
    moveTo(15f, 4f)
    lineTo(20f, 4f)
    lineTo(20f, 9f)
    moveTo(20f, 15f)
    lineTo(20f, 20f)
    lineTo(15f, 20f)
    moveTo(9f, 20f)
    lineTo(4f, 20f)
    lineTo(4f, 15f)
    moveTo(4f, 12f)
    lineTo(20f, 12f)
}

private fun PathBuilder.sliders() {
    moveTo(4f, 7f)
    lineTo(20f, 7f)
    moveTo(4f, 12f)
    lineTo(20f, 12f)
    moveTo(4f, 17f)
    lineTo(20f, 17f)
    moveTo(9f, 5f)
    lineTo(9f, 9f)
    moveTo(15f, 10f)
    lineTo(15f, 14f)
    moveTo(9f, 15f)
    lineTo(9f, 19f)
}

private fun PathBuilder.plus() {
    moveTo(12f, 5f)
    lineTo(12f, 19f)
    moveTo(5f, 12f)
    lineTo(19f, 12f)
}

private fun PathBuilder.arrowBack() {
    moveTo(19f, 12f)
    lineTo(5f, 12f)
    moveTo(11f, 6f)
    lineTo(5f, 12f)
    lineTo(11f, 18f)
}

private fun PathBuilder.pencil() {
    moveTo(5f, 19f)
    lineTo(5f, 15f)
    lineTo(16f, 4f)
    lineTo(20f, 8f)
    lineTo(9f, 19f)
    close()
    moveTo(14f, 6f)
    lineTo(18f, 10f)
}

private fun PathBuilder.check() {
    moveTo(5f, 12f)
    lineTo(10f, 17f)
    lineTo(19f, 7f)
}

private fun PathBuilder.chevron() {
    moveTo(9f, 6f)
    lineTo(15f, 12f)
    lineTo(9f, 18f)
}
