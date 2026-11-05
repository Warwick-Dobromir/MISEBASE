package com.culinary.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角 token（MB-UI-001 §3.7）。
 *
 * 编辑式极简要求“克制”：数据容器与列表行不使用大圆角；
 * 只有浮层（弹窗 12dp / 底部弹层 16dp）保留必要的柔和边界。
 *
 * - extraSmall 2dp：输入框、按钮、标签、状态块
 * - small 2dp：行内容器
 * - medium 4dp：需要边界的轻容器
 * - large 12dp：弹窗
 * - extraLarge 16dp：底部弹层、成像遮罩
 */
internal val CulinaryShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(2.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
