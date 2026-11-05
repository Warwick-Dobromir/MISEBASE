package com.culinary.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.icon.CulinaryIcons

private val BarHeight: Dp = 88.dp
private val BarBaseHeight: Dp = 68.dp
private val ScanButtonSize: Dp = 52.dp

/**
 * N3 底部导航（MB-UI-001 §3.9 / Stage 5）。
 *
 * 编辑式极简处理：
 * - 顶边一条 1dp 细线，无阴影、无选中胶囊；
 * - 选中项用香槟金下划线 + 主文字色，未选中用次级灰；
 * - 中央扫码保留凸起圆形主按钮（香槟金底 + 炭墨图标），不依赖阴影也能被识别。
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
        Box(modifier = Modifier.fillMaxWidth().height(BarHeight)) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(BarBaseHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CulinaryDestination.entries.forEach { destination ->
                    if (destination == CulinaryDestination.SCAN) {
                        // 中央位置留给凸起的扫码按钮。
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        BottomBarItem(
                            destination = destination,
                            selected = destination.matches(currentRoute),
                            onClick = { onSelect(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            ScanButton(
                selected = CulinaryDestination.SCAN.matches(currentRoute),
                onClick = { onSelect(CulinaryDestination.SCAN) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
                ).padding(top = 6.dp),
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

@Composable
private fun ScanButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(76.dp)
                .height(BarHeight)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(ScanButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CulinaryIcons.Scan,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(CulinaryDestination.SCAN.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
