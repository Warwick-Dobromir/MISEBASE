package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryLedgerRow
import com.culinary.core.domain.model.LedgerEntryType

/** 库存流水：类型筛选、搜索、前后余额与来源。 */
@Composable
fun InventoryLedgerRoute(
    modifier: Modifier = Modifier,
    viewModel: InventoryLedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryLedgerScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onTypeFilterChange = viewModel::onTypeFilterChange,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
internal fun InventoryLedgerScreen(
    state: InventoryLedgerUiState,
    onQueryChange: (String) -> Unit,
    onTypeFilterChange: (LedgerTypeFilter) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(message = message, actionLabel = stringResource(R.string.inventory_retry), onAction = onRetry)
        }
        Text(
            text = stringResource(R.string.inventory_ledger_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(text = stringResource(R.string.inventory_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        ) {
            LedgerTypeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.typeFilter == filter,
                    onClick = { onTypeFilterChange(filter) },
                    label = {
                        Text(
                            text =
                                stringResource(
                                    when (filter) {
                                        LedgerTypeFilter.ALL -> R.string.inventory_filter_all
                                        LedgerTypeFilter.RECEIPT -> R.string.inventory_filter_receipt
                                        LedgerTypeFilter.ISSUE -> R.string.inventory_filter_issue
                                        LedgerTypeFilter.ADJUSTMENT -> R.string.inventory_filter_adjust
                                    },
                                ),
                        )
                    },
                )
            }
        }
        when {
            state.isLoading -> LoadingRows()
            state.visibleRows.isEmpty() ->
                EmptyState(
                    title = stringResource(R.string.inventory_ledger_empty),
                    description = stringResource(R.string.inventory_empty_body),
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.visibleRows, key = { it.entry.id.value }) { row ->
                        LedgerRow(row = row)
                    }
                }
        }
    }
}

@Composable
private fun LedgerRow(row: InventoryLedgerRow) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = ledgerTypeLabel(row.entry.type),
                tone = toneFor(row.entry.type),
            )
            Text(
                text = formatInstant(row.entry.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
            Text(
                text = signedAmount(row.entry.onHandDelta) + " " + row.entry.stockUnit.symbol,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "${row.productName} · ${row.ingredientName} · ${row.locationName}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.inventory_before_after,
                    formatAmount(row.entry.onHandAfter - row.entry.onHandDelta),
                    formatAmount(row.entry.onHandAfter),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!row.entry.referenceType.isNullOrBlank() || !row.entry.referenceId.isNullOrBlank()) {
            Text(
                text =
                    stringResource(R.string.inventory_reference) + "：" +
                        listOfNotNull(row.entry.referenceType, row.entry.referenceId).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun toneFor(type: LedgerEntryType): StatusTone =
    when (type) {
        LedgerEntryType.PURCHASE_RECEIPT, LedgerEntryType.ADJUSTMENT_IN, LedgerEntryType.RETURN ->
            StatusTone.Success
        LedgerEntryType.WASTE, LedgerEntryType.ADJUSTMENT_OUT -> StatusTone.Warning
        else -> StatusTone.Neutral
    }
