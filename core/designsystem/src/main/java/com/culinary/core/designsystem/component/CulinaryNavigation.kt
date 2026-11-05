package com.culinary.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 页面头部（MB-UI-001 §3.4 / §3.5）。
 *
 * 每页只有一个主标题：全大写拉丁小标题（可选）+ 28sp 标题 + 一句说明 + 右侧动作。
 * 详情/编辑页在页面内容内使用本组件，顶部返回仍由 app 外壳的 TopBar 提供。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    supporting: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.lg,
                    bottom = CulinarySpacing.block,
                ),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Eyebrow(text = eyebrow)
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = CulinaryTextStyles.pageTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (actions != null) {
                actions()
            }
        }
        if (!supporting.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(CulinarySpacing.xs))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 顶部品牌条：左侧字标（可点击回首页），右侧日期/状态。 */
@Composable
fun BrandBar(
    brand: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CulinarySizes.touchTarget)
                .then(clickModifier)
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.md,
                    bottom = CulinarySpacing.xs,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = brand,
            style = CulinaryTextStyles.eyebrow,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = CulinaryTextStyles.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 聚合页中的区块头部：比 PageHeader 低一级。
 *
 * 知识库聚合页已有页面标题「知识库」，三个库列表不再重复 PageHeader，
 * 改用本组件形成「页面标题 → 分段控件 → 区块标题」的三级层级。
 */
@Composable
fun CollectionHeader(
    title: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.lg,
                    bottom = CulinarySpacing.sm,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CulinaryTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
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
