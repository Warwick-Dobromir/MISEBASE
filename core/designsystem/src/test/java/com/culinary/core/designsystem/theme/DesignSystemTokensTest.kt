package com.culinary.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

/**
 * 设计 token 契约（MB-HOME-001 香槟金版）。
 *
 * 只验证可程序化的约束：色值、对比度、字阶、间距、圆角。
 * 页面截图与真机视觉验收仍属验收任务，不用本测试替代。
 */
class DesignSystemTokensTest {
    @Test
    fun `light scheme uses the champagne gold and warm neutral baseline`() {
        val scheme = CulinaryColorSchemes.light

        assertThat(scheme.primary).isEqualTo(Color(0xFF8A6A2B))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFFFFFFFF))
        assertThat(scheme.primaryContainer).isEqualTo(Color(0xFFF5E7CC))
        assertThat(scheme.onPrimaryContainer).isEqualTo(Color(0xFF2A2117))
        assertThat(scheme.background).isEqualTo(Color(0xFFFBF7F0))
        assertThat(scheme.surface).isEqualTo(Color(0xFFFFFDF9))
        assertThat(scheme.onBackground).isEqualTo(Color(0xFF241F19))
        assertThat(scheme.onSurface).isEqualTo(Color(0xFF241F19))
        assertThat(scheme.secondary).isEqualTo(Color(0xFF6E6257))
        assertThat(scheme.surfaceVariant).isEqualTo(Color(0xFFF1E7D9))
        assertThat(scheme.outlineVariant).isEqualTo(Color(0xFFE6DAC9))
    }

    @Test
    fun `dark scheme keeps a warm black background and champagne accents`() {
        val scheme = CulinaryColorSchemes.dark

        assertThat(scheme.background).isEqualTo(Color(0xFF171310))
        assertThat(scheme.background).isNotEqualTo(Color(0xFF000000))
        assertThat(scheme.surface).isEqualTo(Color(0xFF201B16))
        assertThat(scheme.primary).isEqualTo(Color(0xFFE8CD97))
        assertThat(scheme.onPrimary).isEqualTo(Color(0xFF2A2117))
    }

    @Test
    fun `body text meets 4 point 5 to 1 contrast on light and dark surfaces`() {
        val light = CulinaryColorSchemes.light
        val dark = CulinaryColorSchemes.dark

        assertThat(contrast(light.onSurface, light.surface)).isAtLeast(4.5)
        assertThat(contrast(light.onSurface, light.background)).isAtLeast(4.5)
        assertThat(contrast(light.onSurfaceVariant, light.surface)).isAtLeast(4.5)
        assertThat(contrast(light.onSurfaceVariant, light.background)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurface, dark.surface)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurface, dark.background)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurfaceVariant, dark.surface)).isAtLeast(4.5)
        assertThat(contrast(dark.onSurfaceVariant, dark.background)).isAtLeast(4.5)
    }

    @Test
    fun `champagne gold is dark enough to carry text on light surfaces`() {
        val light = CulinaryColorSchemes.light

        // 浅香槟金只用于容器；正文只能使用深香槟金或暖炭灰。
        assertThat(contrast(light.primary, light.surface)).isAtLeast(4.5)
        assertThat(contrast(light.primary, light.background)).isAtLeast(4.5)
        assertThat(luminance(light.primary)).isLessThan(0.2)
    }

    @Test
    fun `primary and semantic colors keep readable foregrounds`() {
        val light = CulinaryColorSchemes.light
        val lightSemantic = CulinaryColorSchemes.lightSemantic
        val dark = CulinaryColorSchemes.dark
        val darkSemantic = CulinaryColorSchemes.darkSemantic

        assertThat(contrast(light.onPrimary, light.primary)).isAtLeast(4.5)
        assertThat(contrast(light.onPrimaryContainer, light.primaryContainer)).isAtLeast(4.5)
        assertThat(contrast(light.onSecondary, light.secondary)).isAtLeast(4.5)
        assertThat(contrast(light.onSecondaryContainer, light.secondaryContainer)).isAtLeast(4.5)
        assertThat(contrast(lightSemantic.onSuccess, lightSemantic.success)).isAtLeast(4.5)
        assertThat(contrast(lightSemantic.onWarning, lightSemantic.warning)).isAtLeast(4.5)
        assertThat(contrast(lightSemantic.onDanger, lightSemantic.danger)).isAtLeast(4.5)
        assertThat(contrast(lightSemantic.onInfo, lightSemantic.info)).isAtLeast(4.5)

        assertThat(contrast(dark.onPrimary, dark.primary)).isAtLeast(4.5)
        assertThat(contrast(dark.onPrimaryContainer, dark.primaryContainer)).isAtLeast(4.5)
        assertThat(contrast(dark.onSecondary, dark.secondary)).isAtLeast(4.5)
        assertThat(contrast(dark.onSecondaryContainer, dark.secondaryContainer)).isAtLeast(4.5)
        assertThat(contrast(darkSemantic.onSuccess, darkSemantic.success)).isAtLeast(4.5)
        assertThat(contrast(darkSemantic.onWarning, darkSemantic.warning)).isAtLeast(4.5)
        assertThat(contrast(darkSemantic.onDanger, darkSemantic.danger)).isAtLeast(4.5)
        assertThat(contrast(darkSemantic.onInfo, darkSemantic.info)).isAtLeast(4.5)
    }

    @Test
    fun `semantic colors are distinct and not reused for decoration`() {
        val semantic = CulinaryColorSchemes.lightSemantic

        assertThat(
            setOf(semantic.success, semantic.warning, semantic.danger, semantic.info),
        ).hasSize(4)
        assertThat(semantic.danger).isNotEqualTo(CulinaryColorSchemes.light.primary)
    }

    @Test
    fun `type scale matches the design spec`() {
        val type = CulinaryTypography

        assertThat(type.displaySmall.fontSize.value).isEqualTo(32f)
        assertThat(type.displaySmall.lineHeight.value).isEqualTo(40f)
        assertThat(type.headlineMedium.fontSize.value).isEqualTo(24f)
        assertThat(type.titleLarge.fontSize.value).isEqualTo(20f)
        assertThat(type.titleMedium.fontSize.value).isEqualTo(16f)
        assertThat(type.bodyLarge.fontSize.value).isEqualTo(14f)
        assertThat(type.bodyLarge.lineHeight.value).isEqualTo(22f)
        assertThat(type.labelMedium.fontSize.value).isEqualTo(12f)
        assertThat(type.labelSmall.fontSize.value).isEqualTo(11f)
    }

    @Test
    fun `quantity style uses monospaced tabular digits`() {
        assertThat(CulinaryTextStyles.quantity.fontFeatureSettings).isEqualTo("tnum")
        assertThat(CulinaryTextStyles.quantity.fontFamily).isEqualTo(
            androidx.compose.ui.text.font.FontFamily.Monospace,
        )
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
        assertThat(CulinarySizes.touchTarget).isEqualTo(48.dp)
        assertThat(CulinarySizes.buttonHeight).isEqualTo(48.dp)
        assertThat(CulinarySizes.fieldHeight).isEqualTo(56.dp)
    }

    @Test
    fun `shapes stay restrained`() {
        val shapes = CulinaryShapes

        assertThat(shapes.extraSmall).isEqualTo(RoundedCornerShape(4.dp))
        assertThat(shapes.small).isEqualTo(RoundedCornerShape(8.dp))
        assertThat(shapes.medium).isEqualTo(RoundedCornerShape(12.dp))
        assertThat(shapes.large).isEqualTo(RoundedCornerShape(16.dp))
        assertThat(shapes.extraLarge).isEqualTo(RoundedCornerShape(24.dp))
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
