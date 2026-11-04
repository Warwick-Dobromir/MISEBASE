package com.culinary.feature.scan.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.culinary.core.domain.barcode.ScanCapture

/**
 * CameraX 预览 + 识别（真实相机入口）。
 *
 * 不放进 [com.culinary.feature.scan.ScanScreen]，因为 Compose UI Test 里没有相机：
 * 页面用槽位注入，测试传空实现，真机与截图走本组件。
 */
@Composable
internal fun CameraBarcodePreview(
    active: Boolean,
    torchOn: Boolean,
    onCapture: (ScanCapture) -> Unit,
    onTorchResult: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCapture by rememberUpdatedState(onCapture)
    val currentOnTorchResult by rememberUpdatedState(onTorchResult)
    val currentOnError by rememberUpdatedState(onError)
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val session =
        remember(previewView) {
            CameraBarcodeSession(
                context = context.applicationContext,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onCapture = { capture -> currentOnCapture(capture) },
                onError = { message -> currentOnError(message) },
            )
        }

    LaunchedEffect(session) {
        if (session.start()) {
            session.setActive(active)
            if (torchOn && !session.setTorch(true)) {
                currentOnTorchResult(false)
            }
        }
    }
    LaunchedEffect(active) { session.setActive(active) }
    LaunchedEffect(torchOn) {
        if (torchOn) {
            if (!session.setTorch(true)) currentOnTorchResult(false)
        } else {
            session.setTorch(false)
        }
    }
    DisposableEffect(session) {
        onDispose { session.release() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
