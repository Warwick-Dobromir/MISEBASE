package com.culinary.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 亮色方案：白纸 + 炭墨 + 香槟金。
 *
 * - `primary` 是近黑主操作色（Swiss 海报式 CTA），`secondary` 才是品牌金；
 * - `primaryContainer` 仅做浅金选中底，文字用 [CulinaryPalette.Ink900]；
 * - 所有 surface 容器为中性灰阶，不使用暖米色。
 */
private val LightColorScheme =
    lightColorScheme(
        primary = CulinaryPalette.Ink900,
        onPrimary = CulinaryPalette.Paper0,
        primaryContainer = CulinaryPalette.Brass100,
        onPrimaryContainer = CulinaryPalette.Ink900,
        inversePrimary = CulinaryPalette.Brass400,
        secondary = CulinaryPalette.Brass600,
        onSecondary = CulinaryPalette.Paper0,
        secondaryContainer = CulinaryPalette.Brass100,
        onSecondaryContainer = CulinaryPalette.Ink900,
        tertiary = CulinaryPalette.Ink600,
        onTertiary = CulinaryPalette.Paper0,
        tertiaryContainer = CulinaryPalette.Paper100,
        onTertiaryContainer = CulinaryPalette.Ink900,
        background = CulinaryPalette.Paper0,
        onBackground = CulinaryPalette.Ink900,
        surface = CulinaryPalette.Paper0,
        onSurface = CulinaryPalette.Ink900,
        surfaceVariant = CulinaryPalette.Paper100,
        onSurfaceVariant = CulinaryPalette.Ink600,
        surfaceTint = CulinaryPalette.Brass600,
        inverseSurface = CulinaryPalette.Ink900,
        inverseOnSurface = CulinaryPalette.Paper50,
        error = CulinaryPalette.Danger700,
        onError = CulinaryPalette.Paper0,
        errorContainer = CulinaryPalette.Danger100,
        onErrorContainer = CulinaryPalette.Danger700,
        outline = CulinaryPalette.Paper300,
        outlineVariant = CulinaryPalette.Paper200,
        scrim = CulinaryPalette.ColorBlack,
        surfaceBright = CulinaryPalette.Paper0,
        surfaceDim = CulinaryPalette.Paper100,
        surfaceContainerLowest = CulinaryPalette.Paper0,
        surfaceContainerLow = CulinaryPalette.Paper50,
        surfaceContainer = CulinaryPalette.Paper100,
        surfaceContainerHigh = CulinaryPalette.Paper150,
        surfaceContainerHighest = CulinaryPalette.Paper250,
    )

/**
 * 暗色方案：中性黑灰 + 提亮香槟金。
 *
 * 保留可用性，不是简单反色：语义色在暗底重新取值，容器用灰阶而非暖棕。
 */
private val DarkColorScheme =
    darkColorScheme(
        primary = CulinaryPalette.Ink050,
        onPrimary = Color(0xFF1A1A1A),
        primaryContainer = CulinaryPalette.Brass800,
        onPrimaryContainer = CulinaryPalette.Brass300,
        inversePrimary = CulinaryPalette.Brass400,
        secondary = CulinaryPalette.Brass300,
        onSecondary = Color(0xFF2A2117),
        secondaryContainer = CulinaryPalette.Brass800,
        onSecondaryContainer = CulinaryPalette.Brass300,
        tertiary = CulinaryPalette.Ink200,
        onTertiary = CulinaryPalette.Night900,
        tertiaryContainer = CulinaryPalette.Night600,
        onTertiaryContainer = CulinaryPalette.Ink050,
        background = CulinaryPalette.Night900,
        onBackground = CulinaryPalette.Ink050,
        surface = CulinaryPalette.Night800,
        onSurface = CulinaryPalette.Ink050,
        surfaceVariant = CulinaryPalette.Night600,
        onSurfaceVariant = CulinaryPalette.Ink200,
        surfaceTint = CulinaryPalette.Brass300,
        inverseSurface = CulinaryPalette.Ink050,
        inverseOnSurface = CulinaryPalette.Night900,
        error = CulinaryPalette.Danger300,
        onError = CulinaryPalette.Danger900,
        errorContainer = CulinaryPalette.Danger900,
        onErrorContainer = Color(0xFFFFC9C4),
        outline = CulinaryPalette.NightLineStrong,
        outlineVariant = CulinaryPalette.NightLine,
        scrim = CulinaryPalette.ColorBlack,
        surfaceBright = Color(0xFF2A2A2A),
        surfaceDim = CulinaryPalette.Night900,
        surfaceContainerLowest = Color(0xFF0E0E0E),
        surfaceContainerLow = CulinaryPalette.Night800,
        surfaceContainer = CulinaryPalette.Night700,
        surfaceContainerHigh = CulinaryPalette.Night600,
        surfaceContainerHighest = Color(0xFF2C2C2C),
    )

/** 语义色通过 CompositionLocal 提供；默认值为亮色，主题外不崩溃。 */
val LocalCulinarySemanticColors = staticCompositionLocalOf { LightSemanticColors }

/** 取语义色的唯一入口：`CulinaryColorScheme.current.danger`。 */
object CulinaryColorScheme {
    val current: CulinarySemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCulinarySemanticColors.current
}

/** 单元测试用：主题色板的只读快照（不依赖 Composable）。 */
internal object CulinaryColorSchemes {
    val light = LightColorScheme
    val dark = DarkColorScheme
    val lightSemantic = LightSemanticColors
    val darkSemantic = DarkSemanticColors
}

/**
 * CULINARY 主题入口（MB-UI-001 / Editorial Minimalism + Swiss）。
 *
 * 只做 token 装配；页面视觉不在这里实现。
 */
@Composable
fun CulinaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCulinarySemanticColors provides if (darkTheme) DarkSemanticColors else LightSemanticColors,
        content = {
            MaterialTheme(
                colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
                typography = CulinaryTypography,
                shapes = CulinaryShapes,
                content = content,
            )
        },
    )
}
