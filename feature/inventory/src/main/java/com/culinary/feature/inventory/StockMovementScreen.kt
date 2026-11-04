package com.culinary.feature.inventory

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.usecase.inventory.StockIssueReason

/** 入库 / 出库 / 盘点调整路由。 */
@Composable
fun StockMovementRoute(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockMovementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    StockMovementScreen(
        state = state,
        onSourceChange = viewModel::onSourceChange,
        onProductSelected = viewModel::onProductSelected,
        onIngredientSelected = viewModel::onIngredientSelected,
        onBatchSelected = viewModel::onBatchSelected,
        onQuantityChange = viewModel::onQuantityChange,
        onUnitChange = viewModel::onUnitChange,
        onReasonChange = viewModel::onReasonChange,
        onNoteChange = viewModel::onNoteChange,
        onBatchCodeChange = viewModel::onBatchCodeChange,
        onExpiryDateChange = viewModel::onExpiryDateChange,
        onLocationSelected = viewModel::onLocationSelected,
        onSubmit = viewModel::submit,
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )
}

private enum class MovementPicker {
    PRODUCT,
    INGREDIENT,
    BATCH,
    UNIT,
    LOCATION,
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // 三种模式共用字段选择器；拆分会重复状态与选择器逻辑。
@Composable
internal fun StockMovementScreen(
    state: StockMovementUiState,
    onSourceChange: (StockSource) -> Unit,
    onProductSelected: (com.culinary.core.domain.model.ProductId) -> Unit,
    onIngredientSelected: (String) -> Unit,
    onBatchSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onReasonChange: (StockIssueReason) -> Unit,
    onNoteChange: (String) -> Unit,
    onBatchCodeChange: (String) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    onLocationSelected: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picker by remember { mutableStateOf<MovementPicker?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message -> ErrorBanner(message = message, onAction = onDismissError) }
        if (state.isLoading) {
            LoadingRows()
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(CulinarySpacing.md),
            ) {
                Text(text = movementTitle(state.mode), style = MaterialTheme.typography.titleLarge)
                when (state.mode) {
                    StockMovementMode.RECEIPT ->
                        ReceiptFields(
                            state = state,
                            onSourceChange = onSourceChange,
                            onBatchCodeChange = onBatchCodeChange,
                            onExpiryDateChange = onExpiryDateChange,
                            onOpenPicker = { picker = it },
                        )

                    StockMovementMode.ISSUE, StockMovementMode.ADJUST -> {
                        BatchField(state) { picker = MovementPicker.BATCH }
                        if (state.mode == StockMovementMode.ISSUE) {
                            ReasonField(state.reason, onReasonChange)
                        }
                    }
                }
                SectionHeader(title = stringResource(R.string.inventory_quantity))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = onQuantityChange,
                        label = { Text(text = stringResource(R.string.inventory_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { picker = MovementPicker.UNIT },
                        modifier = Modifier.padding(start = CulinarySpacing.xs),
                    ) {
                        Text(text = state.unitCode)
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    label = { Text(text = stringResource(R.string.inventory_note)) },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
                )
                if (state.mode == StockMovementMode.ISSUE) {
                    val batch = state.selectedBatch
                    if (batch != null) {
                        StatusBadge(
                            text = "可用 ${formatAmount(batch.item.available)} ${batch.item.stockUnit.symbol}",
                            tone = StatusTone.Neutral,
                            modifier = Modifier.padding(top = CulinarySpacing.xs),
                        )
                    }
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg),
                ) {
                    Text(
                        text =
                            stringResource(
                                when (state.mode) {
                                    StockMovementMode.RECEIPT -> R.string.inventory_confirm_receipt
                                    StockMovementMode.ISSUE -> R.string.inventory_confirm_issue
                                    StockMovementMode.ADJUST -> R.string.inventory_confirm_adjust
                                },
                            ),
                    )
                }
            }
        }
    }

    when (picker) {
        MovementPicker.PRODUCT ->
            SelectionDialog(
                title = stringResource(R.string.inventory_product),
                items = state.options.products,
                label = { it.name },
                onSelect = {
                    onProductSelected(it.id)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        MovementPicker.INGREDIENT ->
            SelectionDialog(
                title = stringResource(R.string.inventory_ingredient),
                items = state.options.ingredients,
                label = { it.name },
                onSelect = {
                    onIngredientSelected(it.id.value)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        MovementPicker.BATCH ->
            SelectionDialog(
                title = stringResource(R.string.inventory_batch),
                items = state.options.batches,
                label = { batchLabel(it) },
                onSelect = {
                    onBatchSelected(it.item.batchId.value)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        MovementPicker.UNIT ->
            SelectionDialog(
                title = stringResource(R.string.inventory_unit),
                items = state.options.units,
                label = { "${it.name}（${it.symbol}）" },
                onSelect = {
                    onUnitChange(it.code)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        MovementPicker.LOCATION ->
            SelectionDialog(
                title = stringResource(R.string.inventory_location),
                items = state.options.locations,
                label = { it.name },
                onSelect = {
                    onLocationSelected(it.id.value)
                    picker = null
                },
                onDismiss = { picker = null },
            )

        null -> Unit
    }
}

@Composable
private fun ReceiptFields(
    state: StockMovementUiState,
    onSourceChange: (StockSource) -> Unit,
    onBatchCodeChange: (String) -> Unit,
    onExpiryDateChange: (String) -> Unit,
    onOpenPicker: (MovementPicker) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
        FilterChip(
            selected = state.source == StockSource.PRODUCT,
            onClick = { onSourceChange(StockSource.PRODUCT) },
            label = { Text(text = stringResource(R.string.inventory_source_product)) },
        )
        FilterChip(
            selected = state.source == StockSource.INGREDIENT,
            onClick = { onSourceChange(StockSource.INGREDIENT) },
            label = { Text(text = stringResource(R.string.inventory_source_ingredient)) },
        )
    }
    if (state.source == StockSource.PRODUCT) {
        OutlinedButton(
            onClick = { onOpenPicker(MovementPicker.PRODUCT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = state.selectedProductName ?: stringResource(R.string.inventory_product))
        }
    } else {
        val ingredient =
            state.options.ingredients
                .firstOrNull { it.id.value == state.selectedIngredientId }
                ?.name
        OutlinedButton(
            onClick = { onOpenPicker(MovementPicker.INGREDIENT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = ingredient ?: stringResource(R.string.inventory_ingredient))
        }
    }
    OutlinedTextField(
        value = state.batchCode,
        onValueChange = onBatchCodeChange,
        label = { Text(text = stringResource(R.string.inventory_batch_code)) },
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
    )
    OutlinedTextField(
        value = state.expiryDate,
        onValueChange = onExpiryDateChange,
        label = { Text(text = stringResource(R.string.inventory_expiry_date)) },
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
    )
    OutlinedButton(
        onClick = { onOpenPicker(MovementPicker.LOCATION) },
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.xs),
    ) {
        val location =
            state.options.locations
                .firstOrNull { it.id.value == state.locationId }
                ?.name
        Text(text = location ?: stringResource(R.string.inventory_location))
    }
}

@Composable
private fun BatchField(
    state: StockMovementUiState,
    onOpen: () -> Unit,
) {
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text(text = state.selectedBatchLabel ?: stringResource(R.string.inventory_batch))
    }
}

@Composable
private fun ReasonField(
    reason: StockIssueReason,
    onReasonChange: (StockIssueReason) -> Unit,
) {
    SectionHeader(title = stringResource(R.string.inventory_reason))
    Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
        StockIssueReason.entries.forEach { value ->
            FilterChip(
                selected = reason == value,
                onClick = { onReasonChange(value) },
                label = { Text(text = issueReasonLabel(value)) },
            )
        }
    }
}

@Composable
private fun movementTitle(mode: StockMovementMode): String =
    stringResource(
        when (mode) {
            StockMovementMode.RECEIPT -> R.string.inventory_receipt_title
            StockMovementMode.ISSUE -> R.string.inventory_issue_title
            StockMovementMode.ADJUST -> R.string.inventory_adjust_title
        },
    )

@Composable
private fun batchLabel(batch: com.culinary.core.domain.model.InventoryBatchSummary): String {
    val code =
        batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.item.batchId.value
            .take(8)
    return "$code · ${formatAmount(batch.item.available)} ${batch.item.stockUnit.symbol}"
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = CulinarySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = label(item), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.inventory_cancel))
            }
        },
    )
}
