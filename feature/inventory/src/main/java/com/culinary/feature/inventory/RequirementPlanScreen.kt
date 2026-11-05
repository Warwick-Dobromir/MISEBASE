package com.culinary.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.PurchaseListLine
import com.culinary.core.domain.model.RequirementPlan
import com.culinary.core.domain.model.RequirementPreviewLine
import com.culinary.core.domain.usecase.recipe.RecipeOption

/** 需求计划：菜谱/份数 → 缺口 → 采购清单 → 扣减/回放。 */
@Composable
fun RequirementPlanRoute(
    modifier: Modifier = Modifier,
    viewModel: RequirementPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val csvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            val csv = pendingCsv
            if (uri != null && csv != null) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csv.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            pendingCsv = null
            viewModel.clearCsv()
        }
    LaunchedEffect(state.csv) {
        val csv = state.csv
        if (csv != null) {
            pendingCsv = csv
            csvLauncher.launch("misebase-purchase-list.csv")
        }
    }
    RequirementPlanScreen(
        state = state,
        onSelectRecipe = viewModel::selectRecipe,
        onServingsChange = viewModel::onServingsChange,
        onCalculate = viewModel::calculate,
        onGenerateList = viewModel::generateList,
        onConfirmDeduction = viewModel::confirmDeduction,
        onExportCsv = viewModel::exportCsv,
        onReplay = viewModel::replay,
        onCloseReplay = viewModel::closeReplay,
        onDismissMessages = viewModel::clearMessages,
        modifier = modifier,
    )
}

@Suppress("LongMethod") // 单页承载计划/清单/记录三个纵向区块，拆分 Composable 不改变状态耦合。
@Composable
internal fun RequirementPlanScreen(
    state: RequirementPlanUiState,
    onSelectRecipe: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onCalculate: () -> Unit,
    onGenerateList: () -> Unit,
    onConfirmDeduction: () -> Unit,
    onExportCsv: () -> Unit,
    onReplay: (CalculationRecord) -> Unit,
    onCloseReplay: () -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRecipePicker by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                onAction = onDismissMessages,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        state.message?.let { message ->
            StatusBadge(
                text = message,
                tone = StatusTone.Success,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal, vertical = CulinarySpacing.xs),
            )
        }
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = CulinarySpacing.xl),
            ) {
                PageHeader(
                    eyebrow = "PLAN",
                    title = stringResource(R.string.inventory_plan_title),
                )
                Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                    CulinaryButton(
                        text = state.selectedRecipeName ?: stringResource(R.string.inventory_no_recipe),
                        onClick = { showRecipePicker = true },
                        variant = CulinaryButtonVariant.Secondary,
                        icon = CulinaryIcons.ChevronDown,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CulinaryTextField(
                        value = state.servings,
                        onValueChange = onServingsChange,
                        label = { Text(text = stringResource(R.string.inventory_servings)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.padding(top = CulinarySpacing.md),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
                        modifier = Modifier.padding(top = CulinarySpacing.sm),
                    ) {
                        CulinaryButton(
                            text = stringResource(R.string.inventory_calculate),
                            onClick = onCalculate,
                            variant = CulinaryButtonVariant.Secondary,
                            enabled = !state.isWorking,
                        )
                        CulinaryButton(
                            text = stringResource(R.string.inventory_generate_list),
                            onClick = onGenerateList,
                            enabled = !state.isWorking,
                            loading = state.isWorking,
                        )
                    }
                    state.plan?.let { plan ->
                        PlanContent(
                            plan = plan,
                            ingredientNames = state.ingredientNames,
                            isWorking = state.isWorking,
                            onConfirmDeduction = onConfirmDeduction,
                            onExportCsv = onExportCsv,
                        )
                    }
                    SectionHeader(title = stringResource(R.string.inventory_records))
                    if (state.records.isEmpty()) {
                        Text(
                            text = stringResource(R.string.inventory_records_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        state.records.forEach { record ->
                            RecordRow(
                                record = record,
                                onReplay = { onReplay(record) },
                            )
                            CulinaryRule()
                        }
                    }
                }
            }
        }
    }
    if (showRecipePicker) {
        RecipePickerDialog(
            recipes = state.recipes,
            onSelect = {
                onSelectRecipe(it.recipe.id.value)
                showRecipePicker = false
            },
            onDismiss = { showRecipePicker = false },
        )
    }
    state.replayedRecord?.let { record ->
        ReplayDialog(record = record, ingredientNames = state.ingredientNames, onDismiss = onCloseReplay)
    }
}

@Composable
private fun PlanContent(
    plan: RequirementPlan,
    ingredientNames: Map<String, String>,
    isWorking: Boolean,
    onConfirmDeduction: () -> Unit,
    onExportCsv: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.inventory_required_gap))
    plan.requiredLines.forEach { line ->
        RequirementRow(line = line)
    }
    if (plan.optionalLines.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.inventory_optional_gap))
        plan.optionalLines.forEach { line ->
            RequirementRow(line = line)
        }
    }
    SectionHeader(title = stringResource(R.string.inventory_purchase_list))
    if (plan.purchaseList.isEmpty) {
        Text(
            text = stringResource(R.string.inventory_purchase_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        plan.purchaseList.lines.forEach { line ->
            PurchaseRow(line = line, name = ingredientNames[line.ingredientId.value] ?: line.ingredientId.value)
        }
        CulinaryButton(
            text = stringResource(R.string.inventory_export_csv),
            onClick = onExportCsv,
            variant = CulinaryButtonVariant.Secondary,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
    CulinaryButton(
        text = stringResource(R.string.inventory_confirm_deduction),
        onClick = onConfirmDeduction,
        enabled = !isWorking && !plan.hasRequiredShortfall,
        loading = isWorking,
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.block),
    )
    if (plan.hasRequiredShortfall) {
        Text(
            text = "存在必需缺口，补齐库存后才能确认扣减。",
            style = MaterialTheme.typography.bodySmall,
            color = CulinaryColorScheme.current.danger,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun RequirementRow(line: RequirementPreviewLine) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line.ingredientName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(
                text =
                    stringResource(
                        if (line.isOptional) R.string.inventory_optional_short else R.string.inventory_required_short,
                    ),
                tone = if (line.isOptional) StatusTone.Warning else StatusTone.Neutral,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text =
                    "需求 ${formatAmount(line.required.amount)} ${line.required.unit.symbol} · " +
                        "可用 ${formatAmount(line.available.amount)} ${line.available.unit.symbol}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!line.isCovered) {
                QuantityText(
                    amount = formatAmount(line.shortfall.amount),
                    unit = line.shortfall.unit.symbol,
                    emphasisColor = CulinaryColorScheme.current.danger,
                )
            }
        }
        CulinaryRule()
    }
}

@Composable
private fun PurchaseRow(
    line: PurchaseListLine,
    name: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "需求 ${formatAmount(line.required.amount)} · 可用 ${formatAmount(line.available.amount)} " +
                        line.required.unit.symbol,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuantityText(amount = formatAmount(line.toPurchase.amount), unit = line.toPurchase.unit.symbol)
    }
    CulinaryRule()
}

@Composable
private fun RecordRow(
    record: CalculationRecord,
    onReplay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatInstant(record.confirmedAt ?: record.createdAt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.inventory_payload_hash) + " " + record.payloadHash.take(12),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CulinaryTextButton(
            text = stringResource(R.string.inventory_replay),
            onClick = onReplay,
        )
    }
}

@Composable
private fun RecipePickerDialog(
    recipes: List<RecipeOption>,
    onSelect: (RecipeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.inventory_recipe),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                recipes.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = CulinarySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                option.recipe.name +
                                    if (option.currentVersion == null) "（无当前版本）" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    CulinaryRule()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.inventory_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun ReplayDialog(
    record: CalculationRecord,
    ingredientNames: Map<String, String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.inventory_replay_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.inventory_payload_hash) + "：" + record.payloadHash,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.lines.forEach { line ->
                    Text(
                        text =
                            "${ingredientNames[line.ingredientId.value] ?: line.ingredientId.value}  " +
                                "${formatAmount(line.quantity.amount)} ${line.quantity.unit.symbol}" +
                                if (line.isOptional) "  (可选)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = CulinarySpacing.xxs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.inventory_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
