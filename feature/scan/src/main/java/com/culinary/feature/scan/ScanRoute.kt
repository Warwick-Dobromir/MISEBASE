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
 * 页面重新进入前台时显式恢复分析器；离开前台时暂停分析，避免相机状态与
 * NavHost 返回栈中的页面生命周期脱节。
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasCamera = remember(context) { CameraPermission.hasCamera(context) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }

    LaunchedEffect(Unit) {
        viewModel.onCameraAvailability(hasCamera)
        viewModel.onPermissionChecked(CameraPermission.isGranted(context))
        // LifecycleEventObserver 不会补发注册前已经发生的 ON_RESUME。
        // 首次进入必须主动恢复；之后由 ON_RESUME 处理从确认页返回。
        viewModel.onResumeScanning()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.onResumeScanning()
                    Lifecycle.Event.ON_PAUSE -> viewModel.onPauseScanning()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPauseScanning()
        }
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
