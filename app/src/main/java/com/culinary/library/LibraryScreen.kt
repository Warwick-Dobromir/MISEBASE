package com.culinary.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 知识库聚合页外壳：顶部 Segment 切换三个档案库，内容由 [content] 注入。
 *
 * 只做容器，不合并 feature 代码；每个 Tab 仍渲染原 feature 的列表 Route。
 */
@Composable
fun LibraryScreen(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryTab) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LibraryTabSwitcher(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content(selectedTab)
        }
    }
}

@Composable
private fun LibraryTabSwitcher(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.xs),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(CulinarySpacing.xxs)
                    .selectableGroup(),
        ) {
            LibraryTab.entries.forEach { tab ->
                LibraryTabItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LibraryTabItem(
    tab: LibraryTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            modifier
                .height(48.dp)
                .clip(RoundedCornerShape(CulinarySpacing.xs))
                .background(background)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
