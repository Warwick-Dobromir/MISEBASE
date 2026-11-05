package com.culinary.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

// 共享组件（MB-UI-001，Editorial Minimalism + Swiss）。
//
// 只保留跨模块真正复用的结构件：区块标题、档案行、状态标记、表单分组、确认弹窗。
// 数据展示、按钮、输入、反馈、页面头部在各自文件中定义（见同包其它 Culinary*.kt）。

/**
 * 区块标题：细线 + 全大写小标题 + 主标题 + 右侧动作。
 *
 * 细线是编辑式排版的分节手段，优先于卡片；[showRule] 为 false 时用于弹窗/表单内部。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    eyebrow: String? = null,
    subtitle: String? = null,
    showRule: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showRule) {
            CulinaryRule()
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = CulinarySpacing.md, bottom = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!eyebrow.isNullOrBlank()) {
                    Eyebrow(text = eyebrow)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = title,
                    style = CulinaryTextStyles.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (action != null) {
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                action()
            }
        }
    }
}

/**
 * 档案列表行：标题 / 副标题 / 尾部元数据。
 *
 * 保留旧 API 供既有页面平滑迁移，内部统一走 [CulinaryListRow]。
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
    eyebrow: String? = null,
) {
    CulinaryListRow(
        title = title,
        modifier = modifier,
        eyebrow = eyebrow,
        subtitle = subtitle,
        meta = meta,
        trailing = trailing,
        onClick = onClick,
        showDivider = showDivider,
    )
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
    val semantic = CulinaryColorScheme.current
    val container =
        when (tone) {
            StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh
            StatusTone.Accent -> MaterialTheme.colorScheme.secondaryContainer
            StatusTone.Success -> semantic.successSurface
            StatusTone.Warning -> semantic.warningSurface
            StatusTone.Danger -> semantic.dangerSurface
        }
    val content =
        when (tone) {
            StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            StatusTone.Accent -> MaterialTheme.colorScheme.onSecondaryContainer
            StatusTone.Success -> semantic.onSuccessSurface
            StatusTone.Warning -> semantic.onWarningSurface
            StatusTone.Danger -> semantic.onDangerSurface
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier =
                Modifier.padding(
                    horizontal = 6.dp,
                    vertical = 2.dp,
                ),
        )
    }
}

/** 表单分组：标题 + 内容；标题细线在组之间形成节奏。 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = CulinarySpacing.block)) {
        SectionHeader(title = title)
        content()
    }
}

/** 破坏性/普通确认弹窗；文案必须说明后果（§11）。 */
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
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            CulinaryButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant =
                    if (destructive) {
                        CulinaryButtonVariant.Danger
                    } else {
                        CulinaryButtonVariant.Primary
                    },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel ?: "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
