package com.culinary.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角 token（UI 设计书 1.0.0 §6）。
 *
 * 克制使用圆角：标签 4dp、按钮/输入 8dp、卡片 12dp、浮层 16dp。
 * 列表与卡片用 1dp 边框/色差，不用阴影。
 */
internal val CulinaryShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
