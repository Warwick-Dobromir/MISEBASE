package com.culinary.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinarySearchField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.InventoryOverviewRow

/** 库存总览：搜索、缺货筛选、入库/流水/需求计划入口与商品列表。 */
@Composable
fun InventoryRoute(
    onOpenProduct: (String) -> Unit,
    onReceipt: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    InventoryOverviewScreen(
        state = state,
        onOpenProduct = onOpenProduct,
        onReceipt = onReceipt,
        onOpenLedger = onOpenLedger,
        onOpenPlan = onOpenPlan,
        onQueryChange = viewModel::onQueryChange,
        onToggleLowStock = viewModel::toggleLowStock,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Suppress("LongMethod") // 总览头部动作、搜索与列表属于同一屏状态，拆分收益低。
@Composable
internal fun InventoryOverviewScreen(
    state: InventoryOverviewUiState,
    onOpenProduct: (String) -> Unit,
    onReceipt: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenPlan: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLowStock: () -> Unit,
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
        PageHeader(
            eyebrow = "INVENTORY",
            title = stringResource(R.string.inventory_title),
            supporting = stringResource(R.string.inventory_count, state.rows.size),
            actions = {
                CulinaryButton(
                    text = stringResource(R.string.inventory_action_receipt),
                    onClick = onReceipt,
                    icon = CulinaryIcons.Add,
                )
            },
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        ) {
            CulinaryButton(
                text = stringResource(R.string.inventory_action_ledger),
                onClick = onOpenLedger,
                variant = com.culinary.core.designsystem.component.CulinaryButtonVariant.Secondary,
            )
            CulinaryButton(
                text = stringResource(R.string.inventory_action_plan),
                onClick = onOpenPlan,
                variant = com.culinary.core.designsystem.component.CulinaryButtonVariant.Secondary,
            )
        }
        CulinarySearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.inventory_search),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.md,
                    ),
        )
        CulinaryFilterPill(
            text = stringResource(R.string.inventory_low_only),
            selected = state.onlyLowStock,
            onClick = onToggleLowStock,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.sm,
                    bottom = CulinarySpacing.xs,
                ),
        )
        when {
            state.isLoading -> LoadingRows()
            state.rows.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Inventory,
                    title = stringResource(R.string.inventory_empty_title),
                    description = stringResource(R.string.inventory_empty_body),
                    actionLabel = stringResource(R.string.inventory_action_receipt),
                    onAction = onReceipt,
                )

            state.visibleRows.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Search,
                    title = stringResource(R.string.inventory_search),
                    description = stringResource(R.string.inventory_empty_body),
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.visibleRows, key = { it.productId.value }) { row ->
                        InventoryOverviewRowItem(row = row, onClick = { onOpenProduct(row.productId.value) })
                    }
                }
        }
    }
}

@Composable
private fun InventoryOverviewRowItem(
    row: InventoryOverviewRow,
    onClick: () -> Unit,
) {
    val batchLabel = stringResource(R.string.inventory_batch_count, row.batchCount)
    val expiryLabel =
        if (row.nearestExpiry == null) {
            stringResource(R.string.inventory_no_expiry)
        } else {
            stringResource(R.string.inventory_expiry) + " " + formatDate(row.nearestExpiry)
        }
    val quantityLabel =
        stringResource(R.string.inventory_on_hand) + " " + formatAmount(row.onHand) + " " +
            row.stockUnit.symbol + " · " + stringResource(R.string.inventory_reserved) + " " +
            formatAmount(row.reserved)
    val subtitle =
        listOfNotNull(
            row.ingredientName,
            quantityLabel,
            row.locationNames.takeIf { it.isNotEmpty() }?.joinToString(" / "),
            batchLabel,
            expiryLabel,
        ).joinToString(" · ")
    ArchiveListItem(
        title = row.productName,
        subtitle = subtitle,
        meta = null,
        onClick = onClick,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                QuantityText(amount = formatAmount(row.available), unit = row.stockUnit.symbol)
                if (row.available <= 0.0) {
                    StatusBadge(text = stringResource(R.string.inventory_low), tone = StatusTone.Danger)
                } else if (row.reserved > 0.0) {
                    StatusBadge(
                        text = stringResource(R.string.inventory_reserved) + " " + formatAmount(row.reserved),
                        tone = StatusTone.Warning,
                    )
                }
            }
        },
    )
}
