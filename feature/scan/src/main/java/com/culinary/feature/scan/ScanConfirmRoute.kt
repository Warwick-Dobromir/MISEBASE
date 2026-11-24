package com.culinary.feature.scan

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 导入确认页入口：把 ViewModel 的状态与用户动作接到 [ScanConfirmScreen]。 */
@Composable
fun ScanConfirmRoute(
    onBackToScan: () -> Unit,
    onHandoffToInventory: (ScanHandoff) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val handleBackToScan =
        remember(viewModel, onBackToScan) {
            {
                viewModel.onCancelImport()
                onBackToScan()
            }
        }
    // The system back key must run the same cancellation/resume path as the visible button.
    BackHandler(onBack = handleBackToScan)
    ScanConfirmScreen(
        state = state,
        onSelectIngredient = viewModel::onSelectIngredient,
        onCreateNewChange = viewModel::onCreateNewChange,
        onNewIngredientNameChange = viewModel::onNewIngredientNameChange,
        onQuantityChange = viewModel::onQuantityChange,
        // v9：识别结果只交给库存建档流程，扫码本身不再写库存余额。
        onConfirm = { viewModel.handoff()?.let(onHandoffToInventory) },
        onRetry = { viewModel.handoff()?.let(onHandoffToInventory) },
        onBackToScan = handleBackToScan,
        modifier = modifier,
    )
}
