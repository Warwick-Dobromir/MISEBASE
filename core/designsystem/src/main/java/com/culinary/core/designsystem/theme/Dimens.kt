package com.culinary.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * 间距、尺寸与描边 token（MB-UI-001 §3.5 / §3.6）。
 *
 * 基准 4dp。页面级间距使用具名 token，禁止页面内写魔法值：
 * - 页面左右边距 20dp（窄屏 16dp）；
 * - 一级区块之间 40dp，区块标题与内容 12dp；
 * - 列表行垂直 16dp，表单字段 20dp。
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

    /** 页面左右统一边距（≥360dp 宽）。 */
    val pageHorizontal = 20.dp

    /** 窄屏（<360dp）页面边距。 */
    val pageHorizontalCompact = 16.dp

    /** 一级区块之间。 */
    val section = 40.dp

    /** 区块内分组之间。 */
    val block = 24.dp

    /** 表单字段之间。 */
    val field = 20.dp

    /** 列表行垂直内边距。 */
    val row = 16.dp

    /** 超大留白，用于首页 Hero 与空状态。 */
    val spacious = 64.dp
}

object CulinarySizes {
    /** 触控目标下限（§18）。 */
    val touchTarget = 48.dp

    /** 主操作按钮高度。 */
    val buttonHeight = 48.dp

    /** 次级/行内按钮高度。 */
    val buttonHeightCompact = 40.dp

    /** 输入框高度。 */
    val fieldHeight = 56.dp

    val listRowSingle = 56.dp
    val listRowDouble = 72.dp
    val listRowTriple = 88.dp

    /** 列表底部安全留白，避免被底部导航遮挡。 */
    val listBottomInset = 96.dp

    val formMaxWidth = 720.dp
    val dialogMaxWidth = 560.dp

    val iconInline = 16.dp
    val iconDefault = 24.dp
    val iconLarge = 32.dp
    val iconDisplay = 40.dp
}

object CulinaryStrokes {
    /** 细分割线。 */
    val hairline = 1.dp

    /** 输入框聚焦、选中下划线、强调边界。 */
    val emphasis = 1.5.dp

    /** 图片/相机遮罩描边。 */
    val overlay = 2.dp
}
