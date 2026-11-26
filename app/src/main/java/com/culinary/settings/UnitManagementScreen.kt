package com.culinary.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.BrandPageHeader
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.Unit as MeasureUnit
import com.culinary.core.domain.model.UnitDimension

@Composable
fun UnitManagementRoute(
        viewModel: UnitManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MeasureUnit?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.md),
    ) {
        BrandPageHeader(title = "计量单位", supporting = "UNITS")
        CulinaryRule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeader(title = "单位数据库")
            Text(
                "＋ 添加单位",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { editing = null; showEditor = true }.padding(vertical = 8.dp),
            )
        }
        Text(
            "这里维护厨房与库存共用的计量单位。停用的单位不会出现在选择列表中，已有档案中的单位记录会保留。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CulinarySpacing.md))
        UnitDimension.entries.forEach { dimension ->
            val items = state.units.filter { it.dimension == dimension }
            if (items.isNotEmpty()) {
                Text(
                    dimension.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = CulinarySpacing.sm, bottom = CulinarySpacing.xs),
                )
                items.forEach { unit ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(unit.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                unit.symbol + if (unit.isActive) "" else " · 已停用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "编辑",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { editing = unit; showEditor = true }.padding(horizontal = 8.dp),
                        )
                        Text(
                            if (unit.isActive) "停用" else "启用",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable { viewModel.setActive(unit, !unit.isActive) }
                                .padding(start = 8.dp),
                        )
                    }
                    CulinaryRule()
                }
            }
        }
    }
    if (showEditor) {
        UnitEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, symbol, dimension, factor ->
                viewModel.save(editing?.id?.value, name, symbol, dimension, factor)
                showEditor = false
            },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("计量单位") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("确定") } },
        )
    }
}

@Composable
private fun UnitEditorDialog(
    initial: MeasureUnit?,
    onDismiss: () -> Unit,
    onSave: (String, String, UnitDimension, Double) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var symbol by remember(initial) { mutableStateOf(initial?.symbol.orEmpty()) }
    var dimension by remember(initial) { mutableStateOf(initial?.dimension ?: UnitDimension.COUNT) }
    var factor by remember(initial) { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加计量单位" else "编辑计量单位") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("单位名称") }, singleLine = true)
                OutlinedTextField(symbol, { symbol = it }, label = { Text("显示符号") }, singleLine = true)
                Text("计量维度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                UnitDimension.entries.forEach { item ->
                    Text(
                        (if (dimension == item) "● " else "○ ") + item.label(),
                        modifier = Modifier.fillMaxWidth().clickable(enabled = initial == null) { dimension = item }
                            .padding(vertical = 6.dp),
                        color = if (initial == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (initial == null && dimension != UnitDimension.COUNT) {
                    OutlinedTextField(
                        value = factor,
                        onValueChange = { factor = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) },
                        label = { Text(if (dimension == UnitDimension.MASS) "换算为克的数值" else "换算为毫升的数值") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && symbol.isNotBlank() &&
                    (dimension == UnitDimension.COUNT || initial != null || (factor.toDoubleOrNull() ?: 0.0) > 0.0),
                onClick = { onSave(name, symbol, dimension, factor.toDoubleOrNull() ?: 1.0) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun UnitDimension.label(): String =
    when (this) {
        UnitDimension.MASS -> "重量"
        UnitDimension.VOLUME -> "体积"
        UnitDimension.COUNT -> "数量"
    }

