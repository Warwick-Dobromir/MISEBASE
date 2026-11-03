package com.culinary.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色彩基线。
 *
 * 进度强调色使用 Orange（任务书 §九）；这里只定义主题需要的少量色阶，
 * 完整视觉系统在阶段 6 随导航与组件一起收敛，不提前堆色板。
 */
internal val Orange40 = Color(0xFFB4551F)
internal val Orange90 = Color(0xFFFFDBC8)
internal val Orange10 = Color(0xFF3A1100)

internal val Orange80 = Color(0xFFFFB68C)
internal val Orange30 = Color(0xFF7A3200)

internal val WarmNeutral40 = Color(0xFF6F5B4E)
internal val WarmNeutral90 = Color(0xFFF3E1D6)
internal val WarmNeutral10 = Color(0xFF28180E)

internal val SurfaceLight = Color(0xFFFFFBF8)
internal val SurfaceDark = Color(0xFF1A1210)

/** 主题内部使用的纯白，避免在多个文件重复字面量。 */
internal val ColorWhite = Color(0xFFFFFFFF)
