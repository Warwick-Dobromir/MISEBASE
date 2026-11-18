package com.culinary.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BarHeight: Dp = 68.dp

/**
 * 底部导航：首页 / 厨房 / 扫码 / 库存 / 我的。
 *
 * 五项使用完全相同的列宽、图标尺寸、文字样式和整行点击区域；
 * 扫码不再是凸起主按钮。选中态仅使用香槟金下划线。
 */
@Composable
fun CulinaryBottomBar(
    currentRoute: String?,
    onSelect: (CulinaryDestination) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CulinaryDestination.entries.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = destination.matches(currentRoute),
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: CulinaryDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor,
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .width(16.dp)
                    .height(2.dp)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            Color.Transparent
                        },
                    ),
        )
    }
}
