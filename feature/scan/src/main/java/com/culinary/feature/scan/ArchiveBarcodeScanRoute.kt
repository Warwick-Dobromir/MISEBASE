package com.culinary.feature.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.culinary.core.common.Clock
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.feature.scan.camera.CameraBarcodePreview
import com.culinary.feature.scan.permission.CameraPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

/**
 * 档案条码专用识别器：只复用底层 CameraX/ML Kit、校验和帧去重，
 * 不进入主页扫码的档案查询、待入库或批次识别流程。
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

    fun resetFrames() = gate.reset()
}

@Composable
fun ArchiveBarcodeScanRoute(
    oldBarcode: String?,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ArchiveBarcodeScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val hasCamera = remember(context) { CameraPermission.hasCamera(context) }
    var permissionGranted by remember(context) { mutableStateOf(CameraPermission.isGranted(context)) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    var candidate by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel) { viewModel.begin() }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("更换商品条码", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(270.dp).background(Color.Black),
        ) {
            if (hasCamera && permissionGranted) {
                CameraBarcodePreview(
                    active = scanning,
                    torchOn = false,
                    onCapture = { capture ->
                        if (scanning) {
                            viewModel.accept(capture)?.let {
                                candidate = it.normalizedValue
                                selected = false
                                scanning = false
                            }
                        }
                    },
                    onTorchResult = {},
                    onError = {},
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    if (scanning) "将新条码对准扫描区域" else "识别完成 · 扫描已暂停",
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.58f)).padding(10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (!hasCamera) "此设备没有可用相机" else "需要相机权限才能扫描条码",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (hasCamera) "授予相机权限" else "返回",
                        modifier = Modifier.clickable {
                            if (hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA) else onBack()
                        }.padding(12.dp),
                        color = Color.White,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                if (scanning) "正在扫描" else "重新扫描",
                modifier = Modifier.clickable {
                    viewModel.resetFrames()
                    scanning = true
                }.padding(vertical = 6.dp, horizontal = 4.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("老码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    oldBarcode?.takeIf { it.isNotBlank() } ?: "暂无条码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (oldBarcode.isNullOrBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f).clickable { if (candidate != null) selected = true },
            ) {
                Text("新码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    candidate ?: "扫描后显示",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (candidate == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                if (candidate != null && selected) {
                    Text("已选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "取消",
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 10.dp, horizontal = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (selected && candidate != null) "确认替换" else "确认替换",
                modifier = Modifier.clickable(enabled = selected && candidate != null) {
                    candidate?.let(onConfirm)
                }.padding(vertical = 10.dp, horizontal = 2.dp),
                color = if (selected && candidate != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
