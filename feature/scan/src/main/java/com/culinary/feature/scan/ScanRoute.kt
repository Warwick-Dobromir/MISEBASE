package com.culinary.feature.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.feature.scan.camera.CameraBarcodePreview
import com.culinary.feature.scan.permission.CameraPermission

/**
 * 扫码页入口：把权限请求、相机预览与一次性导航信号接到 [ScanScreen]。
 *
 * 已有档案只在当前页面短暂提示；未建档物品通过 [ScanUiState.pendingCapture] 交给 app 直达新建档案。
 */
@Composable
fun ScanRoute(
    onCapture: (ScanCapture) -> Unit,
    onOpenArchive: (String) -> Unit,
    onOpenBatch: (String) -> Unit,
    onBack: () -> Unit,
    initialMode: ScanMode = ScanMode.STOCK_IN,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasCamera = remember(context) { CameraPermission.hasCamera(context) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }

    LaunchedEffect(initialMode) {
        viewModel.onSetMode(initialMode)
        viewModel.onCameraAvailability(hasCamera)
        viewModel.onPermissionChecked(CameraPermission.isGranted(context))
        viewModel.onResumeScanning()
    }

    // 从新建档案页返回扫码页时，恢复识别闸门并清除上次提示。
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumeScanning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.openArchiveId) {
        val archiveId = state.openArchiveId ?: return@LaunchedEffect
        viewModel.onArchiveNavigationConsumed()
        onOpenArchive(archiveId)
    }

    LaunchedEffect(state.openBatchId) {
        val batchId = state.openBatchId ?: return@LaunchedEffect
        viewModel.onBatchNavigationConsumed()
        onOpenBatch(batchId)
    }

    LaunchedEffect(state.pendingCapture) {
        val capture = state.pendingCapture ?: return@LaunchedEffect
        viewModel.onCaptureConsumed()
        onCapture(capture)
    }

    ScanScreen(
        state = state,
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        onOpenManualEntry = viewModel::onOpenManualEntry,
        onCloseManualEntry = viewModel::onCloseManualEntry,
        onManualCodeChange = viewModel::onManualCodeChange,
        onSubmitManualCode = viewModel::onSubmitManualCode,
        onToggleTorch = viewModel::onToggleTorch,
        onResumeScanning = viewModel::onResumeScanning,
        onBack = onBack,
        onToggleMode = viewModel::onToggleMode,
        onDismissTorchNotice = viewModel::onTorchNoticeShown,
        cameraContent = {
            // 查询已有档案时只暂停分析，不释放相机预览；匹配结果提示显示在预览上方。
            if (state.showCamera) {
                CameraBarcodePreview(
                    active = state.scanning,
                    torchOn = state.torchOn,
                    onCapture = viewModel::onCameraFrame,
                    onTorchResult = viewModel::onTorchResult,
                    onError = viewModel::onError,
                    modifier = Modifier,
                )
            }
        },
        modifier = modifier,
    )
}
