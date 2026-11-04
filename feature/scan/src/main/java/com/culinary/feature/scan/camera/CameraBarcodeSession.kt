package com.culinary.feature.scan.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.feature.scan.recognize.MlKitBarcodeDecoder
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX 扫码会话（D-013）。
 *
 * 绑定 `Preview` + `ImageAnalysis(KEEP_ONLY_LATEST)`：预览给用户看，分析帧交给
 * [MlKitBarcodeDecoder]。识别到结果只回调一次；真正的去抖与一次性消费在
 * `ScanFrameGate`（纯逻辑），本类只负责"暂停/恢复分析"的物理开关。
 *
 * 相机权限由调用方在进入本会话前取得；权限被拒绝时不应创建本类。
 */
internal class CameraBarcodeSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onCapture: (ScanCapture) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "culinary-scan-analysis") }
    private val decoder = MlKitBarcodeDecoder()
    private val analyzing = AtomicBoolean(false)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analysis: ImageAnalysis? = null

    private val analyzer =
        ImageAnalysis.Analyzer { image ->
            if (!analyzing.get()) {
                image.close()
                return@Analyzer
            }
            decoder.decode(
                imageProxy = image,
                onResult = { captures -> captures.firstOrNull()?.let(onCapture) },
                onFailure = { onError(ERROR_DECODE) },
            )
        }

    /** 绑定后置相机；返回 `false` 表示相机不可用（调用方应回退到手工输入）。 */
    suspend fun start(): Boolean {
        val cameraProvider =
            try {
                awaitProvider()
            } catch (e: ExecutionException) {
                onError("$ERROR_PROVIDER（${e.message ?: "provider error"}）")
                return false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                onError("$ERROR_PROVIDER（interrupted）")
                return false
            }
        provider = cameraProvider
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val imageAnalysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        imageAnalysis.setAnalyzer(executor, analyzer)
        analysis = imageAnalysis
        return try {
            cameraProvider.unbindAll()
            camera =
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            true
        } catch (e: IllegalArgumentException) {
            onError("$ERROR_BIND（${e.message ?: "configuration rejected"}）")
            false
        } catch (e: IllegalStateException) {
            onError("$ERROR_BIND（${e.message ?: "camera unavailable"}）")
            false
        }
    }

    /** 暂停/恢复分析：识别到结果后暂停，用户点"继续扫描"后恢复。 */
    fun setActive(active: Boolean) {
        analyzing.set(active)
        val imageAnalysis = analysis ?: return
        if (active) {
            imageAnalysis.setAnalyzer(executor, analyzer)
        } else {
            imageAnalysis.clearAnalyzer()
        }
    }

    /**
     * 开关手电筒。
     *
     * @return `false` 表示设备没有闪光灯或相机尚未就绪，调用方应提示而不假装成功。
     */
    fun setTorch(on: Boolean): Boolean {
        val activeCamera = camera ?: return false
        if (!activeCamera.cameraInfo.hasFlashUnit()) return false
        activeCamera.cameraControl.enableTorch(on)
        return true
    }

    /** 释放相机与分析资源；释放阶段的失败不影响页面退出，因此不向上抛出。 */
    @Suppress("SwallowedException")
    fun release() {
        analyzing.set(false)
        runCatching { analysis?.clearAnalyzer() }
        runCatching { provider?.unbindAll() }
        runCatching { decoder.close() }
        executor.shutdown()
        analysis = null
        provider = null
        camera = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider {
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (e: ExecutionException) {
                        continuation.resumeWithException(e)
                    } catch (e: InterruptedException) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { future.cancel(true) }
        }
    }

    private companion object {
        const val ERROR_PROVIDER = "相机服务不可用；可以改用手工输入。"
        const val ERROR_BIND = "无法打开后置相机；可以改用手工输入。"
        const val ERROR_DECODE = "识别失败，请保持稳定并重试，或改用手工输入。"
    }
}
