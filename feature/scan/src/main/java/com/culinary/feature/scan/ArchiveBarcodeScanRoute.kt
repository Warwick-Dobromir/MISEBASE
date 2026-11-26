package com.culinary.feature.scan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.lifecycle.ViewModel
import com.culinary.core.common.Clock
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.feature.scan.camera.CameraBarcodePreview
import com.culinary.feature.scan.permission.CameraPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

/** 展示页内的条码更换弹层；识别结果留在弹层中，只有用户确认后才交回档案页。 */
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionGranted = it }
    var candidate by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel) { viewModel.begin() }

    val accent = Color(0xFF8A6A2B)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Text("更换商品条码", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text("扫码识别", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(188.dp)
                        .background(Color(0xFFF1F1F0), RoundedCornerShape(12.dp)),
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
                        if (!scanning) {
                            Text(
                                "识别完成 · 扫描已暂停",
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.58f)).padding(9.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().clickable {
                                if (hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("扫描区域", style = MaterialTheme.typography.titleMedium, color = muted)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (!hasCamera) "此设备没有可用相机" else "点击授予相机权限",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (scanning) "将新条码对准扫描区域" else "识别成功后暂停扫描，扫描区域保持显示",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                    Text(
                        if (scanning) "正在扫描" else "重新扫描",
                        modifier = Modifier.clickable {
                            candidate = null
                            selected = false
                            viewModel.resetFrames()
                            scanning = true
                        }.padding(start = 8.dp, vertical = 6.dp),
                        color = accent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("老码", style = MaterialTheme.typography.titleSmall, color = muted)
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            oldBarcode?.takeIf(String::isNotBlank) ?: "暂无条码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (oldBarcode.isNullOrBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f)
                            .border(
                                width = if (selected) 1.dp else 0.dp,
                                color = if (selected) accent else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable(enabled = candidate != null) { selected = true }
                            .padding(if (selected) 10.dp else 0.dp),
                    ) {
                        Text("新码", style = MaterialTheme.typography.titleSmall, color = muted)
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            candidate ?: "扫描后显示",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (candidate == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Spacer(Modifier.height(10.dp))
                            Text("✓  已选择", style = MaterialTheme.typography.labelMedium, color = accent)
                        } else if (candidate != null) {
                            Spacer(Modifier.height(10.dp))
                            Text("点击选择新码", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "取消",
                        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp),
                        color = muted,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "确认替换",
                        modifier = Modifier.clickable(enabled = selected && candidate != null) {
                            candidate?.let(onConfirm)
                        }.padding(vertical = 6.dp),
                        color = if (selected && candidate != null) accent else MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
