package com.culinary.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 关联档案列表：复用档案行样式，点击进入目标详情。
 *
 * 空列表不渲染，避免没有关联的档案出现空分节。
 */
@Composable
fun CulinaryRelationSection(
    title: String,
    items: List<CulinaryRelationItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            subtitle = "共 ${items.size} 条",
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
        )
        items.forEachIndexed { index, item ->
            CulinaryListRow(
                title = item.title,
                eyebrow = item.eyebrow,
                meta = item.meta,
                trailing = {
                    Icon(
                        imageVector = CulinaryIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = item.onClick,
                showDivider = index != items.lastIndex,
            )
        }
    }
}
