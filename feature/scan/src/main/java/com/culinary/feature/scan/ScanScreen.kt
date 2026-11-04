package com.culinary.feature.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.theme.CulinarySpacing

/**
 * 扫码页（纯 UI，可测试）。
 *
 * 相机预览以 [cameraContent] 槽位注入：Compose UI Test 传空实现即可验证权限说明、
 * 扫描框、手电筒、手工输入与返回；真实页面由 [ScanRoute] 注入 CameraX 预览。
 */
@Composable
internal fun ScanScreen(
    state: ScanUiState,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onCloseManualEntry: () -> Unit,
    onManualCodeChange: (String) -> Unit,
    onSubmitManualCode: () -> Unit,
    onToggleTorch: () -> Unit,
    onResumeScanning: () -> Unit,
    onBack: () -> Unit,
    onDismissTorchNotice: () -> Unit,
    cameraContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.showCamera -> {
                cameraContent()
                ScanFrameOverlay(modifier = Modifier.fillMaxSize())
                CameraActions(
                    state = state,
                    onToggleTorch = onToggleTorch,
                    onOpenManualEntry = onOpenManualEntry,
                    onResumeScanning = onResumeScanning,
                    onBack = onBack,
                )
            }

            state.manualEntry ->
                ManualEntryPane(
                    state = state,
                    onManualCodeChange = onManualCodeChange,
                    onSubmitManualCode = onSubmitManualCode,
                    onCloseManualEntry = onCloseManualEntry,
                    onBack = onBack,
                )

            else ->
                PermissionPane(
                    state = state,
                    onRequestPermission = onRequestPermission,
                    onOpenManualEntry = onOpenManualEntry,
                    onBack = onBack,
                )
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            state.error?.let { message ->
                ErrorBanner(
                    message = message,
                    actionLabel = stringResource(R.string.scan_retry),
                    onAction = onResumeScanning,
                )
            }
            state.torchNotice?.let { message ->
                ErrorBanner(
                    message = message,
                    actionLabel = stringResource(R.string.scan_retry),
                    onAction = onDismissTorchNotice,
                )
            }
            state.status?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(CulinarySpacing.md),
                )
            }
        }
    }
}

/** 扫描框：四块暗色遮罩 + 白色描边，框内不遮挡相机。 */
@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val scrim = Color.Black.copy(alpha = 0.45f)
        val frameWidth = size.width * FRAME_WIDTH_RATIO
        val frameHeight = minOf(frameWidth, size.height * FRAME_HEIGHT_RATIO)
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        drawRect(color = scrim, size = Size(size.width, top))
        drawRect(
            color = scrim,
            topLeft = Offset(0f, top + frameHeight),
            size = Size(size.width, size.height - top - frameHeight),
        )
        drawRect(color = scrim, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(
            color = scrim,
            topLeft = Offset(left + frameWidth, top),
            size = Size(size.width - left - frameWidth, frameHeight),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(FRAME_CORNER_RADIUS_PX, FRAME_CORNER_RADIUS_PX),
            style = Stroke(width = FRAME_STROKE_PX),
        )
    }
}

@Composable
private fun CameraActions(
    state: ScanUiState,
    onToggleTorch: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onResumeScanning: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.scan_frame_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm)) {
            Button(onClick = onToggleTorch) {
                Text(
                    text =
                        stringResource(
                            if (state.torchOn) R.string.scan_torch_on else R.string.scan_torch_off,
                        ),
                )
            }
            OutlinedButton(onClick = onOpenManualEntry) {
                Text(text = stringResource(R.string.scan_manual_open))
            }
            OutlinedButton(onClick = onBack) {
                Text(text = stringResource(R.string.scan_back))
            }
        }
        if (!state.scanning) {
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Button(onClick = onResumeScanning) {
                Text(text = stringResource(R.string.scan_resume))
            }
        }
    }
}

@Composable
private fun PermissionPane(
    state: ScanUiState,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    val title =
        when (state.cameraAccess) {
            CameraAccessState.DENIED -> stringResource(R.string.scan_permission_denied_title)
            CameraAccessState.UNAVAILABLE -> stringResource(R.string.scan_no_camera_title)
            else -> stringResource(R.string.scan_permission_title)
        }
    val body =
        when (state.cameraAccess) {
            CameraAccessState.DENIED -> stringResource(R.string.scan_permission_denied_body)
            CameraAccessState.UNAVAILABLE -> stringResource(R.string.scan_no_camera_body)
            else -> stringResource(R.string.scan_permission_body)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm)) {
            if (state.cameraAccess != CameraAccessState.UNAVAILABLE) {
                Button(onClick = onRequestPermission) {
                    Text(
                        text =
                            stringResource(
                                if (state.cameraAccess == CameraAccessState.DENIED) {
                                    R.string.scan_permission_request_again
                                } else {
                                    R.string.scan_permission_grant
                                },
                            ),
                    )
                }
            }
            OutlinedButton(onClick = onOpenManualEntry) {
                Text(text = stringResource(R.string.scan_manual_open))
            }
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.scan_back))
            }
        }
        if (state.cameraAccess == CameraAccessState.DENIED) {
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            Text(
                text = stringResource(R.string.scan_permission_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualEntryPane(
    state: ScanUiState,
    onManualCodeChange: (String) -> Unit,
    onSubmitManualCode: () -> Unit,
    onCloseManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CulinarySpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.scan_manual_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Text(
            text = stringResource(R.string.scan_manual_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        OutlinedTextField(
            value = state.manualCode,
            onValueChange = onManualCodeChange,
            label = { Text(text = stringResource(R.string.scan_manual_label)) },
            singleLine = true,
            isError = state.manualError != null,
            supportingText = {
                state.manualError?.let { Text(text = it) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm)) {
            Button(onClick = onSubmitManualCode, enabled = state.manualCode.isNotBlank()) {
                Text(text = stringResource(R.string.scan_manual_confirm))
            }
            OutlinedButton(onClick = onCloseManualEntry, enabled = state.hasCamera) {
                Text(text = stringResource(R.string.scan_manual_back_to_camera))
            }
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.scan_back))
            }
        }
    }
}

private const val FRAME_WIDTH_RATIO = 0.72f
private const val FRAME_HEIGHT_RATIO = 0.46f
private const val FRAME_CORNER_RADIUS_PX = 24f
private const val FRAME_STROKE_PX = 4f
