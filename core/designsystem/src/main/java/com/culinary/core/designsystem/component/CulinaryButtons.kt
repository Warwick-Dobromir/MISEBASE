package com.culinary.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySizes

/**
 * 按钮规范（MB-UI-001 §3.9 / §8.1）。
 *
 * - Primary：近黑实心，每屏最多一个主操作；
 * - Secondary：1dp 描边，次要操作；
 * - Ghost：文字操作，低优先级；
 * - Danger：破坏性操作，使用语义红而不是品牌金；
 * - 统一 2dp 圆角、48dp 高度、无阴影；loading 时保留文案并显示内联进度。
 *
 * 变体见 [CulinaryButtonVariant]。
 */
@Suppress("LongMethod") // 四个变体共享同一套尺寸/颜色约定，拆开反而重复判空与 loading 分支。
@Composable
fun CulinaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CulinaryButtonVariant = CulinaryButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val effectiveEnabled = enabled && !loading
    val shape = MaterialTheme.shapes.extraSmall
    val contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = LocalContentColor.current,
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }

    when (variant) {
        CulinaryButtonVariant.Primary ->
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeight),
                enabled = effectiveEnabled,
                shape = shape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                elevation = null,
                contentPadding = contentPadding,
                content = content,
            )

        CulinaryButtonVariant.Danger ->
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeight),
                enabled = effectiveEnabled,
                shape = shape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = CulinaryColorScheme.current.danger,
                        contentColor = CulinaryColorScheme.current.onDanger,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                elevation = null,
                contentPadding = contentPadding,
                content = content,
            )

        CulinaryButtonVariant.Secondary ->
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeight),
                enabled = effectiveEnabled,
                shape = shape,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (effectiveEnabled) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                    ),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        containerColor = Color.Transparent,
                    ),
                elevation = null,
                contentPadding = contentPadding,
                content = content,
            )

        CulinaryButtonVariant.Overlay ->
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeight),
                enabled = effectiveEnabled,
                shape = shape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1F1F1F),
                        disabledContainerColor = Color.White.copy(alpha = 0.38f),
                        disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.6f),
                    ),
                elevation = null,
                contentPadding = contentPadding,
                content = content,
            )

        CulinaryButtonVariant.OverlayOutline ->
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeight),
                enabled = effectiveEnabled,
                shape = shape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (effectiveEnabled) 1f else 0.5f)),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.5f),
                        containerColor = Color.Transparent,
                    ),
                elevation = null,
                contentPadding = contentPadding,
                content = content,
            )

        CulinaryButtonVariant.Ghost ->
            TextButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = CulinarySizes.buttonHeightCompact),
                enabled = effectiveEnabled,
                shape = shape,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                content = content,
            )
    }
}

/** 行内文字操作：与 [CulinaryButton] 的 Ghost 变体同规格，便于页面表达“次要动作”。 */
@Composable
fun CulinaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = CulinarySizes.buttonHeightCompact),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = if (danger) CulinaryColorScheme.current.danger else MaterialTheme.colorScheme.secondary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 图标操作：48dp 命中区，图标 24dp。 */
@Composable
fun CulinaryIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick, modifier = modifier.size(CulinarySizes.touchTarget), enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint =
                if (enabled) {
                    tint
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
        )
    }
}
