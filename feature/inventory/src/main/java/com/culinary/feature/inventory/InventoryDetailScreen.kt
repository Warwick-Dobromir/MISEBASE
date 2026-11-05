package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryBatchSummary
import com.culinary.core.domain.model.InventoryLedgerEntry

/** 商品详情：全部批次、流水与关联菜谱；出库/调整从具体批次进入。 */
@Composable
fun InventoryDetailRoute(
    onIssueBatch: (String) -> Unit,
    onAdjustBatch: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryDetailScreen(
        state = state,
        onIssueBatch = onIssueBatch,
        onAdjustBatch = onAdjustBatch,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Suppress("LongMethod") // 详情页按批次/流水/关联菜谱三段纵向排列，状态集中在一处更易核对。
@Composable
internal fun InventoryDetailScreen(
    state: InventoryDetailUiState,
    onIssueBatch: (String) -> Unit,
    onAdjustBatch: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.inventory_retry),
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                EmptyState(
                    icon = com.culinary.core.designsystem.icon.CulinaryIcons.Inventory,
                    title = stringResource(R.string.inventory_empty_title),
                    description = stringResource(R.string.inventory_empty_body),
                )

            else -> {
                val detail = state.detail
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = CulinarySpacing.xl),
                ) {
                    val total = detail.batches.sumOf { it.item.available }
                    PageHeader(
                        eyebrow = "PRODUCT",
                        title = detail.product.name,
                        supporting = detail.ingredientName,
                        actions = {
                            QuantityText(
                                amount = formatAmount(total),
                                unit = detail.product.stockUnit.symbol,
                            )
                        },
                    )
                    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
                        SectionHeader(title = stringResource(R.string.inventory_batch_count, detail.batches.size))
                        if (detail.batches.isEmpty()) {
                            Text(
                                text = stringResource(R.string.inventory_no_batches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            detail.batches.forEach { batch ->
                                BatchRow(
                                    batch = batch,
                                    onIssue = { onIssueBatch(batch.item.batchId.value) },
                                    onAdjust = { onAdjustBatch(batch.item.batchId.value) },
                                )
                            }
                        }
                        SectionHeader(title = stringResource(R.string.inventory_ledger_title))
                        if (detail.ledger.isEmpty()) {
                            Text(
                                text = stringResource(R.string.inventory_ledger_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            detail.ledger.forEach { entry ->
                                LedgerDetailRow(entry = entry)
                                CulinaryRule()
                            }
                        }
                        SectionHeader(title = stringResource(R.string.inventory_related_recipes))
                        if (detail.relatedRecipeNames.isEmpty()) {
                            Text(
                                text = stringResource(R.string.inventory_no_related),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            detail.relatedRecipeNames.forEach { name ->
                                Text(
                                    text = "· $name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = CulinarySpacing.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(
    batch: InventoryBatchSummary,
    onIssue: () -> Unit,
    onAdjust: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        batch.batchCode?.takeIf { it.isNotBlank() } ?: batch.item.batchId.value
                            .take(8),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text =
                        listOfNotNull(
                            batch.locationName,
                            batch.item.expiresOn?.let { formatDate(it) },
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QuantityText(amount = formatAmount(batch.item.available), unit = batch.item.stockUnit.symbol)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
            CulinaryTextButton(
                text = stringResource(R.string.inventory_issue),
                onClick = onIssue,
            )
            CulinaryTextButton(
                text = stringResource(R.string.inventory_adjust),
                onClick = onAdjust,
            )
        }
        CulinaryRule()
    }
}

@Composable
private fun LedgerDetailRow(entry: InventoryLedgerEntry) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(
                text = ledgerTypeLabel(entry.type),
                tone = if (entry.onHandDelta >= 0.0) StatusTone.Success else StatusTone.Neutral,
            )
            Text(
                text = formatInstant(entry.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
            Text(
                text = signedAmount(entry.onHandDelta) + " " + entry.stockUnit.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = CulinarySpacing.xs),
            )
            Text(
                text = formatAmount(entry.onHandAfter) + " " + entry.stockUnit.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal fun signedAmount(value: Double): String = if (value > 0.0) "+${formatAmount(value)}" else formatAmount(value)
