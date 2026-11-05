package com.culinary.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing

// 反馈状态（MB-UI-001 §3.9 / §12–§14）。
//
// 统一四态：空、加载、错误、结果提示。空状态说明“为什么空 + 下一步”；
// 错误说明“发生了什么 + 能否重试”，内部错误码不进入用户界面。

/**
 * 空状态：图标 + 标题 + 一句解释 + 可选主操作。
 *
 * 居中布局在空状态下是合理的（任务书 §3.9 明确允许）。
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.lg, vertical = CulinarySpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(CulinarySpacing.block))
            CulinaryButton(
                text = actionLabel,
                onClick = onAction,
                variant = CulinaryButtonVariant.Secondary,
            )
        }
    }
}

/**
 * 页面级错误条：左侧 2dp 语义红边、浅红底、可选重试。
 *
 * [message] 只放面向用户的说明；错误码保留在诊断信息里，不在此渲染。
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(2.dp),
        color = CulinaryColorScheme.current.dangerSurface,
        contentColor = CulinaryColorScheme.current.onDangerSurface,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(CulinaryColorScheme.current.danger),
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = CulinarySpacing.sm, vertical = CulinarySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                    CulinaryTextButton(
                        text = actionLabel,
                        onClick = onAction,
                        danger = true,
                    )
                }
            }
        }
    }
}

/** 字段/行级错误：文字 + 图标，不只靠颜色。 */
@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = CulinarySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CulinaryIcons.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = CulinaryColorScheme.current.danger,
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = CulinaryColorScheme.current.danger,
        )
    }
}

/**
 * 首次加载占位：静态骨架行，不用全屏 spinner。
 *
 * 骨架块使用极浅灰，不脉冲闪烁；刷新时优先保留旧内容，只有首次进入才展示。
 */
@Composable
fun LoadingRows(
    modifier: Modifier = Modifier,
    rows: Int = 5,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.pageHorizontal),
    ) {
        repeat(rows) { index ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.row)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(if (index % 3 == 2) 0.42f else 0.62f)
                            .height(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.xs))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.34f else 0.46f)
                            .height(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(2.dp),
                            ),
                )
            }
            CulinaryRule()
        }
    }
}

/** 通用提示块：用于“已入库/已保存”等结果反馈，语义色由调用方决定。 */
@Composable
fun ResultPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Success,
) {
    val container =
        when (tone) {
            StatusTone.Success -> CulinaryColorScheme.current.successSurface
            StatusTone.Warning -> CulinaryColorScheme.current.warningSurface
            StatusTone.Danger -> CulinaryColorScheme.current.dangerSurface
            StatusTone.Accent -> MaterialTheme.colorScheme.secondaryContainer
            StatusTone.Neutral -> MaterialTheme.colorScheme.surfaceContainer
        }
    val content =
        when (tone) {
            StatusTone.Success -> CulinaryColorScheme.current.onSuccessSurface
            StatusTone.Warning -> CulinaryColorScheme.current.onWarningSurface
            StatusTone.Danger -> CulinaryColorScheme.current.onDangerSurface
            StatusTone.Accent -> MaterialTheme.colorScheme.onSecondaryContainer
            StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(CulinarySpacing.sm)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
