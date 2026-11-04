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

private val LightColorScheme =
    lightColorScheme(
        primary = CulinaryPalette.Orange40,
        onPrimary = CulinaryPalette.ColorWhite,
        primaryContainer = CulinaryPalette.Orange90,
        onPrimaryContainer = CulinaryPalette.Orange10,
        inversePrimary = CulinaryPalette.Orange80,
        secondary = CulinaryPalette.WarmNeutral40,
        onSecondary = CulinaryPalette.ColorWhite,
        secondaryContainer = CulinaryPalette.WarmNeutral90,
        onSecondaryContainer = CulinaryPalette.WarmNeutral10,
        tertiary = CulinaryPalette.WarmNeutral40,
        onTertiary = CulinaryPalette.ColorWhite,
        tertiaryContainer = CulinaryPalette.WarmNeutral95,
        onTertiaryContainer = CulinaryPalette.WarmNeutral10,
        background = CulinaryPalette.SurfaceLight,
        onBackground = CulinaryPalette.WarmNeutral10,
        surface = CulinaryPalette.SurfaceLightRaised,
        onSurface = CulinaryPalette.WarmNeutral10,
        surfaceVariant = CulinaryPalette.WarmNeutral90,
        onSurfaceVariant = CulinaryPalette.WarmNeutral40,
        surfaceTint = CulinaryPalette.Orange40,
        inverseSurface = CulinaryPalette.WarmNeutral10,
        inverseOnSurface = CulinaryPalette.WarmNeutral95,
        error = CulinaryPalette.DangerLight,
        onError = CulinaryPalette.ColorWhite,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = CulinaryPalette.WarmNeutral30,
        outlineVariant = CulinaryPalette.WarmNeutral80,
        scrim = CulinaryPalette.ColorBlack,
        surfaceBright = CulinaryPalette.SurfaceLightRaised,
        surfaceDim = CulinaryPalette.WarmNeutral95,
        surfaceContainer = CulinaryPalette.WarmNeutral95,
        surfaceContainerHigh = CulinaryPalette.WarmNeutral90,
        surfaceContainerHighest = CulinaryPalette.WarmNeutral80,
        surfaceContainerLow = CulinaryPalette.SurfaceLight,
        surfaceContainerLowest = CulinaryPalette.SurfaceLightRaised,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = CulinaryPalette.Orange80,
        onPrimary = CulinaryPalette.Orange10,
        primaryContainer = CulinaryPalette.Orange30,
        onPrimaryContainer = CulinaryPalette.Orange90,
        inversePrimary = CulinaryPalette.Orange40,
        secondary = CulinaryPalette.WarmNeutral70,
        onSecondary = CulinaryPalette.WarmNeutral10,
        secondaryContainer = CulinaryPalette.WarmNeutral40,
        onSecondaryContainer = CulinaryPalette.WarmNeutral90,
        tertiary = CulinaryPalette.WarmNeutral70,
        onTertiary = CulinaryPalette.WarmNeutral10,
        tertiaryContainer = CulinaryPalette.SurfaceDarkVariant,
        onTertiaryContainer = CulinaryPalette.WarmNeutral90,
        background = CulinaryPalette.SurfaceDark,
        onBackground = CulinaryPalette.WarmNeutral95,
        surface = CulinaryPalette.SurfaceDarkRaised,
        onSurface = CulinaryPalette.WarmNeutral95,
        surfaceVariant = CulinaryPalette.SurfaceDarkVariant,
        onSurfaceVariant = CulinaryPalette.WarmNeutral70,
        surfaceTint = CulinaryPalette.Orange80,
        inverseSurface = CulinaryPalette.WarmNeutral95,
        inverseOnSurface = CulinaryPalette.WarmNeutral10,
        error = CulinaryPalette.DangerDark,
        onError = CulinaryPalette.OnDangerDark,
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = CulinaryPalette.WarmNeutral70,
        outlineVariant = Color(0xFF55433A),
        scrim = CulinaryPalette.ColorBlack,
        surfaceBright = Color(0xFF3A2A22),
        surfaceDim = CulinaryPalette.SurfaceDark,
        surfaceContainer = Color(0xFF201713),
        surfaceContainerHigh = Color(0xFF2B201B),
        surfaceContainerHighest = Color(0xFF362923),
        surfaceContainerLow = CulinaryPalette.SurfaceDarkRaised,
        surfaceContainerLowest = Color(0xFF140E0B),
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
 * CULINARY 主题入口（MB-D-008 方向 A）。
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
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = CulinaryTypography,
            shapes = CulinaryShapes,
            content = content,
        )
    }
}
