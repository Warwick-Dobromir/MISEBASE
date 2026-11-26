package com.culinary.feature.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.core.common.Clock
import com.culinary.feature.scan.camera.CameraBarcodePreview
import com.culinary.feature.scan.permission.CameraPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

/**
 * 档案条码更换专用扫描入口。
 *
 * 只复用底层 CameraX/ML Kit 识别器和帧去抖规则，不进入主页扫码的档案查询、
 * 待入库、批次识别或模式切换状态机。
 */
@HiltViewModel
class ArchiveBarcodeScanViewModel @Inject constructor(
    private val gate: ScanFrameGate,
    private val clock: Clock,
) : ViewModel() {
    fun begin() = gate.reset()

    fun accept(capture: ScanCapture): ScanCapture? {
        if (ScanCapture.validate(capture.rawValue) != null) return null
        if (!gate.offer(capture.normalizedValue, clock.now().toEpochMilli())) return null
        return capture.copy(rawValue = capture.normalizedValue)
    }
}

@Composable
fun ArchiveBarcodeScanRoute(
    onCapture: (ScanCapture) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchiveBarcodeScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val hasCamera = remember(context) { CameraPermission.hasCamera(context) }
    var permissionGranted by remember(context) {
        mutableStateOf(CameraPermission.isGranted(context))
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(viewModel) { viewModel.begin() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera && permissionGranted) {
            CameraBarcodePreview(
                active = true,
                torchOn = false,
                onCapture = { capture -> viewModel.accept(capture)?.let(onCapture) },
                onTorchResult = {},
                onError = {},
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("更换档案条码", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    TextButton(onClick = onBack) { Text("取消", color = Color.White) }
                }
                Text(
                    "将新条码对准扫描框。识别成功后返回档案并确认更换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Text(
                "仅扫描条码 · 不触发入库或商品查询",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (!hasCamera) "此设备没有可用相机" else "需要相机权限才能扫描条码",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                TextButton(
                    onClick = {
                        if (hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
                        else onBack()
                    },
                ) {
                    Text(if (hasCamera) "授予相机权限" else "返回档案", color = Color.White)
                }
                TextButton(onClick = onBack) { Text("取消", color = Color.White) }
            }
        }
    }
}
