package com.culinary.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩 token（MB-UI-001 / Editorial Minimalism + Swiss，MB-D-008 Rev.2）。
 *
 * 视觉基线：
 * - 画布：纯白 [Paper0] 与极浅灰 [Paper50]，不用暖米色大面积铺底；
 * - 文字：深炭 [Ink900]，辅助 [Ink600]，弱化 [Ink500]；
 * - 品牌：香槟金 [Brass400] 只做填充、下划线、标记，**不承载正文**；
 *   强调文字使用 [Brass600]（白底 5.03:1）或 [Brass700]（浅金底 5.24:1）。
 *
 * 所有正文/容器文字对比度由 `DesignSystemTokensTest` 强制 ≥4.5:1。
 * 改色必须同时更新 `docs/design/MISEBASE-UI-DESIGN-SPEC.md` 与本文件。
 */
internal object CulinaryPalette {
    // ---- Paper：画布、分区、线条 ----
    val Paper0 = Color(0xFFFFFFFF)
    val Paper50 = Color(0xFFFAFAFA)
    val Paper100 = Color(0xFFF5F5F5)
    val Paper150 = Color(0xFFEFEFEF)
    val Paper200 = Color(0xFFEDEDED)
    val Paper250 = Color(0xFFE8E8E8)
    val Paper300 = Color(0xFFDCDCDC)

    // ---- Ink：文字层级 ----
    val Ink900 = Color(0xFF1F1F1F)
    val Ink700 = Color(0xFF3D3D3D)
    val Ink600 = Color(0xFF5C5C5C)
    val Ink500 = Color(0xFF707070)
    val Ink300 = Color(0xFFB3B3B3)

    // ---- Brass：香槟金 ----

    /** 品牌填充色；白底仅 2.69:1，**禁止作为文字颜色**。 */
    val Brass400 = Color(0xFFB8996D)

    /** 白底强调文字/图标（5.03:1）。 */
    val Brass600 = Color(0xFF8A6A2B)

    /** 浅金容器上的强调文字（5.24:1）。 */
    val Brass700 = Color(0xFF7A5C24)

    /** 选中/强调浅底。 */
    val Brass100 = Color(0xFFF2EBDF)
    val Brass200 = Color(0xFFE6D5B8)

    // ---- 语义色（亮色）----
    val Success600 = Color(0xFF2F6B47)
    val Success100 = Color(0xFFE8F2EC)
    val Warning700 = Color(0xFF7A4E00)
    val Warning100 = Color(0xFFFBF1E0)
    val Danger700 = Color(0xFFA32020)
    val Danger100 = Color(0xFFFBEAE8)
    val Info600 = Color(0xFF2F5D8A)
    val Info100 = Color(0xFFEDF3F9)

    // ---- 夜间模式：中性灰黑，不使用暖棕 ----
    val Night900 = Color(0xFF121212)
    val Night800 = Color(0xFF171717)
    val Night700 = Color(0xFF1C1C1C)
    val Night600 = Color(0xFF242424)
    val NightLine = Color(0xFF2E2E2E)
    val NightLineStrong = Color(0xFF3A3A3A)
    val Ink050 = Color(0xFFF2F2F2)
    val Ink200 = Color(0xFFC9C9C9)
    val Ink400 = Color(0xFF9A9A9A)

    val Brass300 = Color(0xFFDCC29A)
    val Brass800 = Color(0xFF3A3024)

    val Success300 = Color(0xFF7FD1A8)
    val Success900 = Color(0xFF16301F)
    val Warning300 = Color(0xFFF0C36A)
    val Warning900 = Color(0xFF33260C)
    val Danger300 = Color(0xFFFFB4AB)
    val Danger900 = Color(0xFF351312)
    val Info300 = Color(0xFFA8C8E8)
    val Info900 = Color(0xFF10222F)

    val ColorWhite = Color(0xFFFFFFFF)
    val ColorBlack = Color(0xFF000000)
}

/**
 * 语义色：Material 3 的 colorScheme 没有 success/warning/info 槽位，
 * 用 CompositionLocal 单独提供，避免页面各自定义颜色。
 *
 * 每个语义色提供三组：实心（[success]）、其上的文字（[onSuccess]）、
 * 浅色容器（[successSurface]）与容器文字（[onSuccessSurface]）。
 */
data class CulinarySemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successSurface: Color,
    val onSuccessSurface: Color,
    val warning: Color,
    val onWarning: Color,
    val warningSurface: Color,
    val onWarningSurface: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerSurface: Color,
    val onDangerSurface: Color,
    val info: Color,
    val onInfo: Color,
    val infoSurface: Color,
    val onInfoSurface: Color,
)

internal val LightSemanticColors =
    CulinarySemanticColors(
        success = CulinaryPalette.Success600,
        onSuccess = CulinaryPalette.ColorWhite,
        successSurface = CulinaryPalette.Success100,
        onSuccessSurface = CulinaryPalette.Success600,
        warning = CulinaryPalette.Warning700,
        onWarning = CulinaryPalette.ColorWhite,
        warningSurface = CulinaryPalette.Warning100,
        onWarningSurface = CulinaryPalette.Warning700,
        danger = CulinaryPalette.Danger700,
        onDanger = CulinaryPalette.ColorWhite,
        dangerSurface = CulinaryPalette.Danger100,
        onDangerSurface = CulinaryPalette.Danger700,
        info = CulinaryPalette.Info600,
        onInfo = CulinaryPalette.ColorWhite,
        infoSurface = CulinaryPalette.Info100,
        onInfoSurface = CulinaryPalette.Info600,
    )

internal val DarkSemanticColors =
    CulinarySemanticColors(
        success = CulinaryPalette.Success300,
        onSuccess = CulinaryPalette.Success900,
        successSurface = CulinaryPalette.Success900,
        onSuccessSurface = Color(0xFFA8E6C3),
        warning = CulinaryPalette.Warning300,
        onWarning = CulinaryPalette.Warning900,
        warningSurface = CulinaryPalette.Warning900,
        onWarningSurface = CulinaryPalette.Warning300,
        danger = CulinaryPalette.Danger300,
        onDanger = CulinaryPalette.Danger900,
        dangerSurface = CulinaryPalette.Danger900,
        onDangerSurface = Color(0xFFFFC9C4),
        info = CulinaryPalette.Info300,
        onInfo = CulinaryPalette.Info900,
        infoSurface = CulinaryPalette.Info900,
        onInfoSurface = Color(0xFFC3DCF2),
    )
