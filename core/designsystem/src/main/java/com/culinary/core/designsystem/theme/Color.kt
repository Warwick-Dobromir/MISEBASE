package com.culinary.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token（MB-HOME-001「香槟金版」）。
 *
 * 暖白 / 浅米灰底色 + 深香槟金强调 + 暖炭灰正文 + 暖灰辅助。
 * 所有页面只允许通过 [MaterialTheme.colorScheme] 或 [CulinaryColorScheme] 取色，
 * 禁止写字面色值。深香槟金 [CulinaryPalette.Champagne40] 在暖白底上的对比度
 * ≥ 5.0:1，可直接用于正文与图标；浅香槟金只用于容器/装饰，不得承载正文。
 *
 * 改色必须同时更新 `docs/design/MISEBASE-UI-DESIGN-SPEC.md` 与本文件。
 */
internal object CulinaryPalette {
    // Champagne Gold：主操作 / 选中 / 关键强调（深色可读，浅色只做容器）
    val Champagne40 = Color(0xFF8A6A2B)
    val Champagne80 = Color(0xFFE8CD97)
    val Champagne90 = Color(0xFFF5E7CC)
    val Champagne10 = Color(0xFF2A2117)
    val Champagne30 = Color(0xFF5C4517)

    // 暖灰 / 暖炭：正文、次级文字、标签、辅助文字
    val WarmNeutral10 = Color(0xFF241F19)
    val WarmNeutral30 = Color(0xFF8C7E6D)
    val WarmNeutral40 = Color(0xFF6E6257)
    val WarmNeutral70 = Color(0xFFD6C7B4)
    val WarmNeutral80 = Color(0xFFE6DAC9)
    val WarmNeutral90 = Color(0xFFF1E7D9)
    val WarmNeutral95 = Color(0xFFF8F2E8)

    // 表面：暖白纸张感 / 暗色暖炭
    val SurfaceLight = Color(0xFFFBF7F0)
    val SurfaceLightRaised = Color(0xFFFFFDF9)
    val SurfaceDark = Color(0xFF171310)
    val SurfaceDarkRaised = Color(0xFF201B16)
    val SurfaceDarkVariant = Color(0xFF3A2F24)

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
