package com.culinary.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.Eyebrow
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

/** 库存管理入口：三段编号目录（结构 / 记录 / 规则），每项一行，不使用卡片。 */
@Composable
fun InventoryManagementRoute(
    onBack: () -> Unit,
    onCategories: () -> Unit,
    onLocations: () -> Unit,
    onRecycle: () -> Unit,
    onLedger: () -> Unit,
    onFreeze: () -> Unit,
    onTemporary: () -> Unit,
    onRules: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ManagementBackBar(onBack = onBack)
        PageHeader(
            eyebrow = "MISEBASE",
            title = "库存管理",
            supporting = "INVENTORY SETTINGS",
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = CulinarySpacing.xl),
        ) {
            Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                ArchiveModuleHeader(
                    number = "01",
                    english = "STRUCTURE",
                    chinese = "库存结构",
                    subtitle = "分类与库位决定物品如何被归档与定位。",
                )
            }
            ArchiveListItem(
                title = "分类管理",
                subtitle = "新建、重命名、排序与删除库存分类",
                onClick = onCategories,
                trailing = { ManagementChevron() },
            )
            ArchiveListItem(
                title = "库位管理",
                subtitle = "维护储藏位置，供入库与批次转移选择",
                onClick = onLocations,
                trailing = { ManagementChevron() },
            )
            Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                ArchiveModuleHeader(
                    number = "02",
                    english = "RECORDS",
                    chinese = "库存记录",
                    subtitle = "数量与状态的每一次变化都留痕可追溯。",
                )
            }
            ArchiveListItem(
                title = "批次回收站",
                subtitle = "查看归零批次，在保留期内可恢复",
                onClick = onRecycle,
                trailing = { ManagementChevron() },
            )
            ArchiveListItem(
                title = "库存流水",
                subtitle = "按时间、类型与批次查看全部库存变动",
                onClick = onLedger,
                trailing = { ManagementChevron() },
            )
            Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                ArchiveModuleHeader(
                    number = "03",
                    english = "RULES",
                    chinese = "库存规则",
                    subtitle = "阈值与保留期直接决定提醒与清理时机。",
                )
            }
            ArchiveListItem(
                title = "冻结管理",
                subtitle = "查看当前冻结批次与冻结原因",
                onClick = onFreeze,
                trailing = { ManagementChevron() },
            )
            ArchiveListItem(
                title = "临时库存",
                subtitle = "按规则提醒存放过久的临时批次",
                onClick = onTemporary,
                trailing = { ManagementChevron() },
            )
            ArchiveListItem(
                title = "库存规则设置",
                subtitle = "临时库存提醒天数与回收站保留天数",
                onClick = onRules,
                trailing = { ManagementChevron() },
            )
            Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                Spacer(modifier = Modifier.height(CulinarySpacing.block))
                CulinaryRule()
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
                Eyebrow(text = "MISEBASE · INVENTORY MANAGEMENT")
                Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            }
        }
    }
}

@Composable
private fun ManagementChevron() {
    Icon(
        imageVector = CulinaryIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun ManagementBackBar(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CulinarySpacing.xxl)
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onBack)
                        .defaultMinSize(minHeight = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = CulinaryIcons.Back,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(CulinarySpacing.xs))
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        CulinaryRule()
    }
}
