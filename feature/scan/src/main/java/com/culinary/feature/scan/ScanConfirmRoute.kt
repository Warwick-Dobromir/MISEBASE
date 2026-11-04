package com.culinary.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 导入确认页入口：把 ViewModel 的状态与用户动作接到 [ScanConfirmScreen]。 */
@Composable
fun ScanConfirmRoute(
    onBackToScan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScanConfirmScreen(
        state = state,
        onSelectIngredient = viewModel::onSelectIngredient,
        onCreateNewChange = viewModel::onCreateNewChange,
        onNewIngredientNameChange = viewModel::onNewIngredientNameChange,
        onQuantityChange = viewModel::onQuantityChange,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::confirm,
        onBackToScan = {
            // 「返回扫码」= 取消本次确认：清掉未完成的 operation id，避免下一次确认沿用它。
            viewModel.onCancelImport()
            onBackToScan()
        },
        modifier = modifier,
    )
}
