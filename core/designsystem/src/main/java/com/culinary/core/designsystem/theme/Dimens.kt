package com.culinary.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * 间距与尺寸 token（UI 设计书 1.0.0 §5、§8、§17）。
 *
 * 基准 4dp，只允许 4/8/12/16/24/32/48；页面不使用魔法值。
 */
object CulinarySpacing {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object CulinarySizes {
    /** 触控目标下限（§17）。 */
    val touchTarget = 48.dp
    val buttonHeight = 48.dp
    val fieldHeight = 56.dp
    val listRowSingle = 56.dp
    val listRowDouble = 72.dp
    val listRowTriple = 88.dp
    val divider = 1.dp
    val listBottomInset = 88.dp
    val formMaxWidth = 720.dp
    val dialogMaxWidth = 560.dp
}
