package com.culinary.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.culinary.core.designsystem.component.CulinarySegmentedControl
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 知识库聚合页外壳：顶部分段控件切换三个档案库，内容由 [content] 注入。
 *
 * 只做容器，不合并 feature 代码；每个 Tab 仍渲染原 feature 的列表 Route。
 * 分段控件用香槟金下划线表达选中，不使用填充胶囊。
 */
@Composable
fun LibraryScreen(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryTab) -> Unit,
) {
    val tabs = LibraryTab.entries
    Column(modifier = modifier.fillMaxSize()) {
        CulinarySegmentedControl(
            options = tabs.map { stringResource(it.labelRes) },
            selectedIndex = tabs.indexOf(selectedTab),
            onSelect = { index -> onSelectTab(tabs[index]) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.xs,
                    ),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content(selectedTab)
        }
    }
}
