package com.culinary.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
    lightColorScheme(
        primary = Orange40,
        onPrimary = ColorWhite,
        primaryContainer = Orange90,
        onPrimaryContainer = Orange10,
        secondary = WarmNeutral40,
        onSecondary = ColorWhite,
        secondaryContainer = WarmNeutral90,
        onSecondaryContainer = WarmNeutral10,
        background = SurfaceLight,
        surface = SurfaceLight,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Orange80,
        onPrimary = Orange10,
        primaryContainer = Orange30,
        onPrimaryContainer = Orange90,
        secondary = WarmNeutral90,
        onSecondary = WarmNeutral10,
        secondaryContainer = WarmNeutral40,
        onSecondaryContainer = WarmNeutral90,
        background = SurfaceDark,
        surface = SurfaceDark,
    )

/**
 * CULINARY 主题入口。
 *
 * 阶段 1 只保证主题可装配；字体层级、组件样式与深浅色细节在阶段 6 收敛。
 */
@Composable
fun CulinaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
