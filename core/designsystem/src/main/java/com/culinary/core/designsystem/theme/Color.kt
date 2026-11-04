package com.culinary.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token（MB-D-008 方向 A「专业厨房档案」）。
 *
 * 暖中性底色 + Orange 强调 + 暖灰辅助。所有页面只允许通过
 * [MaterialTheme.colorScheme] 或 [CulinaryColorScheme] 取色，禁止写字面色值。
 * 具体色值与 UI 设计书 1.0.0 §3.1 一致；改色必须同时更新设计书与本文件。
 */
internal object CulinaryPalette {
    // Orange：主操作 / 选中 / 关键数字
    val Orange40 = Color(0xFFB4551F)
    val Orange80 = Color(0xFFFFB68C)
    val Orange90 = Color(0xFFFFDBC8)
    val Orange10 = Color(0xFF3A1100)
    val Orange30 = Color(0xFF7A3200)

    // 暖灰：次级操作 / 标签 / 辅助文字
    val WarmNeutral10 = Color(0xFF28180E)
    val WarmNeutral30 = Color(0xFF8A7263)
    val WarmNeutral40 = Color(0xFF6F5B4E)
    val WarmNeutral70 = Color(0xFFD8C2B4)
    val WarmNeutral80 = Color(0xFFE4D3C7)
    val WarmNeutral90 = Color(0xFFF3E1D6)
    val WarmNeutral95 = Color(0xFFFAF1EB)

    // 表面：亮色纸张感 / 暗色暖黑
    val SurfaceLight = Color(0xFFFFFBF8)
    val SurfaceLightRaised = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF1A1210)
    val SurfaceDarkRaised = Color(0xFF241A16)
    val SurfaceDarkVariant = Color(0xFF3A2A22)

    // 语义色（不用于装饰）
    val SuccessLight = Color(0xFF2E6B4F)
    val SuccessDark = Color(0xFF7FD1A8)
    val WarningLight = Color(0xFF8A5A00)
    val WarningDark = Color(0xFFF0C36A)
    val DangerLight = Color(0xFFA32020)
    val DangerDark = Color(0xFFFFB4AB)
    val InfoLight = Color(0xFF2F5D8A)
    val InfoDark = Color(0xFFA8C8E8)

    /** 语义色容器上的文字：亮色统一深色，暗色统一暖黑。 */
    val OnSuccessLight = Color(0xFFFFFFFF)
    val OnWarningLight = Color(0xFFFFFFFF)
    val OnDangerLight = Color(0xFFFFFFFF)
    val OnInfoLight = Color(0xFFFFFFFF)
    val OnSuccessDark = Color(0xFF0B2A1B)
    val OnWarningDark = Color(0xFF3A2600)
    val OnDangerDark = Color(0xFF3A0A08)
    val OnInfoDark = Color(0xFF0A1B2A)

    val ColorWhite = Color(0xFFFFFFFF)
    val ColorBlack = Color(0xFF000000)
}

/**
 * 语义色：Material 3 的 colorScheme 没有 success/warning/info 槽位，
 * 用 CompositionLocal 单独提供，避免页面各自定义颜色。
 */
data class CulinarySemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val info: Color,
    val onInfo: Color,
)

internal val LightSemanticColors =
    CulinarySemanticColors(
        success = CulinaryPalette.SuccessLight,
        onSuccess = CulinaryPalette.OnSuccessLight,
        warning = CulinaryPalette.WarningLight,
        onWarning = CulinaryPalette.OnWarningLight,
        danger = CulinaryPalette.DangerLight,
        onDanger = CulinaryPalette.OnDangerLight,
        info = CulinaryPalette.InfoLight,
        onInfo = CulinaryPalette.OnInfoLight,
    )

internal val DarkSemanticColors =
    CulinarySemanticColors(
        success = CulinaryPalette.SuccessDark,
        onSuccess = CulinaryPalette.OnSuccessDark,
        warning = CulinaryPalette.WarningDark,
        onWarning = CulinaryPalette.OnWarningDark,
        danger = CulinaryPalette.DangerDark,
        onDanger = CulinaryPalette.OnDangerDark,
        info = CulinaryPalette.InfoDark,
        onInfo = CulinaryPalette.OnInfoDark,
    )
