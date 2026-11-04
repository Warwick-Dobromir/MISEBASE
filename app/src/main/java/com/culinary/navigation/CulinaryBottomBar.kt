package com.culinary.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.icon.CulinaryIcons

private val BarHeight: Dp = 96.dp
private val BarBaseHeight: Dp = 80.dp
private val ScanButtonSize: Dp = 56.dp
private val ScanButtonLift: Dp = 16.dp

/**
 * N3 底部导航：首页 / 知识库 / 扫码（中央凸起）/ 库存 / 设置。
 *
 * 自定义布局而不是 Material NavigationBar：中央扫码按钮需要凸起圆形，
 * 但仍保留 5 个可选中项与 ≥48dp 的点击区域。
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
        Box(modifier = Modifier.fillMaxWidth().height(BarHeight)) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = ScanButtonLift),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
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
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(44.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
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
                .width(72.dp)
                .height(BarHeight - ScanButtonLift)
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
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
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
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(CulinaryDestination.SCAN.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
