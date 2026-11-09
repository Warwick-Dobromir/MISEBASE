package com.culinary.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 线框图标：制作模式与步骤时间使用。
 *
 * 与 [CulinaryIcons] 的填充图标分开，因为这些位置要求"线框"而不是实心字形；
 * 线宽与品牌厨师帽一致（1.6），不参与导航图标混用。
 */
object CulinaryOutlineIcons {
    /** 线框时钟：详情页"启动此阶段计时"与制作模式倒计时。 */
    val Clock: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Culinary.Clock",
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
                ) {
                    // 外圈
                    moveTo(12f, 3.2f)
                    curveTo(16.86f, 3.2f, 20.8f, 7.14f, 20.8f, 12f)
                    curveTo(20.8f, 16.86f, 16.86f, 20.8f, 12f, 20.8f)
                    curveTo(7.14f, 20.8f, 3.2f, 16.86f, 3.2f, 12f)
                    curveTo(3.2f, 7.14f, 7.14f, 3.2f, 12f, 3.2f)
                    close()
                    // 指针
                    moveTo(12f, 7f)
                    lineTo(12f, 12.3f)
                    lineTo(15.8f, 14.4f)
                }
            }.build()
    }

    /** 线框对勾：制作完成状态。 */
    val Check: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Culinary.CheckOutline",
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
                ) {
                    moveTo(4.5f, 12.5f)
                    lineTo(9.6f, 17.6f)
                    lineTo(19.5f, 7.2f)
                }
            }.build()
    }
}
