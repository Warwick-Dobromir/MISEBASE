package com.culinary.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

/**
 * 设计 token 契约（MB-UI-001 / Editorial Minimalism + Swiss）。
 *
 * 只验证可程序化的约束：色值、对比度、字阶、数字样式、间距、圆角。
 * 页面截图与真机视觉验收仍属验收任务，不用本测试替代。
 */
class DesignSystemTokensTest {
    @Test
    fun `light scheme is paper white with charcoal ink and champagne accent`() {
        val scheme = CulinaryColorSchemes.light

        assertThat(scheme.background).isEqualTo(Color(0xFFFFFFFF))
        assertThat(scheme.surface).isEqualTo(Color(0xFFFFFFFF))
        assertThat(scheme.onBackground).isEqualTo(Color(0xFF1F1F1F))
        assertThat(scheme.onSurface).isEqualTo(Color(0xFF1F1F1F))
        assertThat(scheme.onSurfaceVariant).isEqualTo(Color(0xFF5C5C5C))
        assertThat(scheme.primary).isEqualTo(Color(0xFF1F1F1F))
        assertThat(scheme.secondary).isEqualTo(Color(0xFF8A6A2B))
        assertThat(scheme.secondaryContainer).isEqualTo(Color(0xFFF2EBDF))
        assertThat(scheme.outlineVariant).isEqualTo(Color(0xFFEDEDED))
    }

    @Test
    fun `accent gold is a fill colour and never carries small text`() {
        val accent = CulinaryPalette.Brass400
        val white = CulinaryPalette.Paper0

        // 品牌金只做填充/下划线：白底对比不足 4.5:1，必须由 Brass600/700 承载文字。
        assertThat(contrast(accent, white)).isLessThan(4.5)
        assertThat(contrast(CulinaryPalette.Brass600, white)).isAtLeast(4.5)
        assertThat(contrast(CulinaryPalette.Brass700, CulinaryPalette.Brass100)).isAtLeast(4.5)
    }

    @Test
    fun `dark scheme keeps neutral black surfaces and lifted champagne`() {
        val scheme = CulinaryColorSchemes.dark

        assertThat(scheme.background).isEqualTo(Color(0xFF121212))
        assertThat(scheme.background).isNotEqualTo(Color(0xFF000000))
        assertThat(scheme.surface).isEqualTo(Color(0xFF171717))
        assertThat(scheme.primary).isEqualTo(Color(0xFFF2F2F2))
        assertThat(scheme.secondary).isEqualTo(Color(0xFFDCC29A))
        assertThat(scheme.onSurface).isEqualTo(Color(0xFFF2F2F2))
    }

    @Test
    fun `body text meets 4 point 5 to 1 contrast on light and dark surfaces`() {
        val light = CulinaryColorSchemes.light
        val dark = CulinaryColorSchemes.dark

        assertThat(contrast(light.onSurface, light.surface)).isAtLeast(4.5)
        assertThat(contrast(light.onSurface, light.background)).isAtLeast(4.5)
        assertThat(contrast(light.onSurface, light.surfaceContainer)).isAtLeast(4.5)
        assertThat(contrast(light.onSurfaceVariant, light.surface)).isAtLeast(4.5)
        assertThat(contrast(light.onSurfaceVariant, light.background)).isAtLeast(4.5)
        assertThat(contrast(light.onSurfaceVariant, light.surfaceContainer)).isAtLeast(4.5)

        assertThat(contrast(dark.onSurface, dark.surface)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurface, dark.background)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurfaceVariant, dark.surface)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurfaceVariant, dark.background)).isAtLeast(4.5)
    }

    @Test
    fun `primary and container pairs keep readable foregrounds`() {
        val light = CulinaryColorSchemes.light
        val dark = CulinaryColorSchemes.dark

        assertThat(contrast(light.onPrimary, light.primary)).isAtLeast(4.5)
        assertThat(contrast(light.onPrimaryContainer, light.primaryContainer)).isAtLeast(4.5)
        assertThat(contrast(light.onSecondary, light.secondary)).isAtLeast(4.5)
        assertThat(contrast(light.onSecondaryContainer, light.secondaryContainer)).isAtLeast(4.5)
        assertThat(contrast(light.onError, light.error)).isAtLeast(4.5)
        assertThat(contrast(light.onErrorContainer, light.errorContainer)).isAtLeast(4.5)

        assertThat(contrast(dark.onPrimary, dark.primary)).isAtLeast(4.5)
        assertThat(contrast(dark.onPrimaryContainer, dark.primaryContainer)).isAtLeast(4.5)
        assertThat(contrast(dark.onSecondary, dark.secondary)).isAtLeast(4.5)
        assertThat(contrast(dark.onSecondaryContainer, dark.secondaryContainer)).isAtLeast(4.5)
    }

    @Test
    fun `semantic colours are distinct and their surfaces carry readable text`() {
        val light = CulinaryColorSchemes.lightSemantic
        val dark = CulinaryColorSchemes.darkSemantic
        val scheme = CulinaryColorSchemes.light

        assertThat(
            setOf(light.success, light.warning, light.danger, light.info),
        ).hasSize(4)
        assertThat(light.danger).isNotEqualTo(scheme.primary)
        assertThat(light.danger).isNotEqualTo(scheme.secondary)

        assertThat(contrast(light.onSuccessSurface, light.successSurface)).isAtLeast(4.5)
        assertThat(contrast(light.onWarningSurface, light.warningSurface)).isAtLeast(4.5)
        assertThat(contrast(light.onDangerSurface, light.dangerSurface)).isAtLeast(4.5)
        assertThat(contrast(light.onInfoSurface, light.infoSurface)).isAtLeast(4.5)

        assertThat(contrast(dark.onSuccessSurface, dark.successSurface)).isAtLeast(4.5)
        assertThat(contrast(dark.onWarningSurface, dark.warningSurface)).isAtLeast(4.5)
        assertThat(contrast(dark.onDangerSurface, dark.dangerSurface)).isAtLeast(4.5)
        assertThat(contrast(dark.onInfoSurface, dark.infoSurface)).isAtLeast(4.5)
    }

    @Test
    fun `type scale follows the editorial hierarchy`() {
        val type = CulinaryTypography

        assertThat(type.displayLarge.fontSize.value).isEqualTo(40f)
        assertThat(type.displayLarge.lineHeight.value).isEqualTo(46f)
        assertThat(type.headlineLarge.fontSize.value).isEqualTo(24f)
        assertThat(type.headlineMedium.fontSize.value).isEqualTo(20f)
        assertThat(type.titleLarge.fontSize.value).isEqualTo(18f)
        assertThat(type.titleMedium.fontSize.value).isEqualTo(16f)
        assertThat(type.bodyLarge.fontSize.value).isEqualTo(15f)
        assertThat(type.bodyLarge.lineHeight.value).isEqualTo(24f)
        assertThat(type.labelMedium.fontSize.value).isEqualTo(12f)
        assertThat(type.labelSmall.fontSize.value).isEqualTo(11f)

        assertThat(CulinaryTextStyles.pageTitle.fontSize.value).isEqualTo(28f)
        assertThat(CulinaryTextStyles.sectionTitle.fontSize.value).isEqualTo(20f)
        assertThat(CulinaryTextStyles.eyebrow.fontSize.value).isEqualTo(11f)
    }

    @Test
    fun `numeric styles use tabular sans digits instead of monospace`() {
        val styles =
            listOf(
                CulinaryTextStyles.numeralXl,
                CulinaryTextStyles.numeralL,
                CulinaryTextStyles.numeralM,
                CulinaryTextStyles.numeralS,
            )

        styles.forEach { style ->
            assertThat(style.fontFeatureSettings).isEqualTo("tnum")
            assertThat(style.fontFamily).isEqualTo(FontFamily.SansSerif)
        }
        assertThat(CulinaryTextStyles.quantity.fontFeatureSettings).isEqualTo("tnum")
    }

    @Test
    fun `spacing and sizes stay on the four dp grid`() {
        val spacing =
            listOf(
                CulinarySpacing.xxs,
                CulinarySpacing.xs,
                CulinarySpacing.sm,
                CulinarySpacing.md,
                CulinarySpacing.lg,
                CulinarySpacing.xl,
                CulinarySpacing.xxl,
            )

        assertThat(spacing).containsExactly(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp, 48.dp)
        assertThat(CulinarySpacing.pageHorizontal).isEqualTo(20.dp)
        assertThat(CulinarySpacing.section).isEqualTo(40.dp)
        assertThat(CulinarySpacing.block).isEqualTo(24.dp)
        assertThat(CulinarySpacing.spacious).isEqualTo(64.dp)

        assertThat(CulinarySizes.touchTarget).isEqualTo(48.dp)
        assertThat(CulinarySizes.buttonHeight).isEqualTo(48.dp)
        assertThat(CulinarySizes.fieldHeight).isEqualTo(56.dp)
        assertThat(CulinaryStrokes.emphasis).isEqualTo(1.5.dp)
    }

    @Test
    fun `shapes stay restrained and avoid pill containers`() {
        val shapes = CulinaryShapes

        assertThat(shapes.extraSmall).isEqualTo(RoundedCornerShape(2.dp))
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(2.dp))
        assertThat(shapes.medium).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(shapes.large).isEqualTo(RoundedCornerShape(12.dp))
        assertThat(shapes.extraLarge).isEqualTo(RoundedCornerShape(16.dp))
    }

    private fun contrast(
        foreground: Color,
        background: Color,
    ): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
}
