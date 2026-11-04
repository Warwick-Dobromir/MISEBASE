package com.culinary.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/** 区块标题；层级靠字重与留白，不用卡片装饰。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = CulinarySpacing.lg, bottom = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/**
 * 档案列表行：标题 / 副标题 / 尾部元数据。
 *
 * 行高 56–72dp，触控目标 ≥48dp；分隔线左侧与文字对齐（UI 设计书 §9.1）。
 */
@Composable
fun ArchiveListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = CulinarySizes.listRowSingle)
                    .then(clickModifier)
                    .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!meta.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                trailing()
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = CulinarySpacing.md),
                thickness = CulinarySizes.divider,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/** 状态标记：颜色不是唯一信息载体，文字必须同时说明状态。 */
enum class StatusTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger,
}

@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val background =
        when (tone) {
            StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
            StatusTone.Accent -> MaterialTheme.colorScheme.primary
            StatusTone.Success -> CulinaryColorScheme.current.success
            StatusTone.Warning -> CulinaryColorScheme.current.warning
            StatusTone.Danger -> CulinaryColorScheme.current.danger
        }
    val content =
        when (tone) {
            StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            StatusTone.Accent -> MaterialTheme.colorScheme.onPrimary
            StatusTone.Success -> CulinaryColorScheme.current.onSuccess
            StatusTone.Warning -> CulinaryColorScheme.current.onWarning
            StatusTone.Danger -> CulinaryColorScheme.current.onDanger
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = background,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = CulinarySpacing.xs, vertical = 2.dp),
        )
    }
}

/** 数量 + 单位：数量等宽、单位弱化（UI 设计书 §4.3）。 */
@Composable
fun QuantityText(
    amount: String,
    unit: String?,
    modifier: Modifier = Modifier,
    emphasisColor: Color? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = amount,
            style = CulinaryTextStyles.quantity,
            color = emphasisColor ?: MaterialTheme.colorScheme.onSurface,
        )
        if (!unit.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** 空状态：说明为什么空 + 下一步做什么（UI 设计书 §12）。 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(CulinarySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            Button(onClick = onAction) { Text(text = actionLabel) }
        }
    }
}

/** 页面级错误条：说明发生了什么，并给出可执行动作。 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(text = actionLabel) }
            }
        }
    }
}

/** 首次加载占位：静态骨架行，不用全屏 spinner（UI 设计书 §13）。 */
@Composable
fun LoadingRows(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md)) {
        repeat(rows) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CulinarySizes.listRowSingle)
                        .padding(vertical = CulinarySpacing.sm)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ),
            )
        }
    }
}

/** 表单分组：标题 + 内容，组间距 24dp。 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title)
        content()
    }
}

/** 破坏性/普通确认弹窗；文案必须说明后果（UI 设计书 §11）。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = CulinaryColorScheme.current.danger,
                            contentColor = CulinaryColorScheme.current.onDanger,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = dismissLabel ?: "取消") }
        },
    )
}
