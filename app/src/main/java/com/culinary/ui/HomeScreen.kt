package com.culinary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.culinary.R
import com.culinary.navigation.CulinaryDestination

/**
 * 首页：八个业务入口 + 设置。
 *
 * 阶段 6 只做导航占位；真正的列表/详情在阶段 7+ 的 feature 中实现。
 */
@Composable
fun HomeScreen(
    onOpen: (CulinaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = CulinaryDestination.entries.filter { it != CulinaryDestination.HOME }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        items(entries) { destination ->
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(destination) }
                        .padding(vertical = 16.dp),
            )
            HorizontalDivider()
        }
    }
}
