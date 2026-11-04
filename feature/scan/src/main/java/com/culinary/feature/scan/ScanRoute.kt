package com.culinary.feature.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.feature.scan.camera.CameraBarcodePreview
import com.culinary.feature.scan.permission.CameraPermission

/**
 * 扫码页入口：把权限请求、相机预览与一次性导航信号接到 [ScanScreen]。
 *
 * 识别被接受后页面暂停分析并发出 [ScanUiState.pendingCapture]，由 app 导航到确认页；
 * 返回本页时 `LaunchedEffect(Unit)` 重新执行，恢复分析（去抖窗口仍在）。
 */
@Composable
fun ScanRoute(
    onCapture: (ScanCapture) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasCamera = remember(context) { CameraPermission.hasCamera(context) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }

    LaunchedEffect(Unit) {
        viewModel.onCameraAvailability(hasCamera)
        viewModel.onPermissionChecked(CameraPermission.isGranted(context))
        viewModel.onResumeScanning()
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
        onDismissTorchNotice = viewModel::onTorchNoticeShown,
        cameraContent = {
            CameraBarcodePreview(
                active = state.scanning,
                torchOn = state.torchOn,
                onCapture = viewModel::onCameraFrame,
                onTorchResult = viewModel::onTorchResult,
                onError = viewModel::onError,
                modifier = Modifier,
            )
        },
        modifier = modifier,
    )
}
