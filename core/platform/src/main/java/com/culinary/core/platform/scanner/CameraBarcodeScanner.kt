package com.culinary.core.platform.scanner

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.BarcodeResult
import com.culinary.core.domain.barcode.BarcodeScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX 扫码会话（D-013）。
 *
 * 绑定 `ImageAnalysis`（`KEEP_ONLY_LATEST`）→ 单线程分析执行器 → [MlKitBarcodeDecoder]；
 * 识别到首个条码或超时即解绑并关闭资源。相机权限/生命周期由传入的 [LifecycleOwner] 提供。
 *
 * **本类只做到编译与资源管理；真机/模拟器扫码验证按批准延后到阶段 7。**
 */
class CameraBarcodeScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val decoder: MlKitBarcodeDecoder = MlKitBarcodeDecoder(),
    private val scanTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : BarcodeScanner {
    override suspend fun scan(): Outcome<BarcodeResult> {
        val provider = awaitProvider()
        val frames = Channel<ImageProxy>(Channel.CONFLATED)
        val analysisExecutor = newAnalysisExecutor()
        val analysis = buildAnalysis(frames, analysisExecutor)

        return try {
            bind(provider, analysis)
            awaitResult(frames)
        } catch (e: SecurityException) {
            failure("camera permission denied", ErrorCode.SCAN_001, retryable = false, cause = e)
        } catch (e: IllegalStateException) {
            failure("camera is unavailable", ErrorCode.SCAN_002, retryable = true, cause = e)
        } catch (e: IllegalArgumentException) {
            failure("camera configuration rejected", ErrorCode.SCAN_002, retryable = true, cause = e)
        } finally {
            release(provider, analysis, analysisExecutor, frames)
        }
    }

    private fun newAnalysisExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "culinary-barcode-analysis") }

    private fun buildAnalysis(
        frames: Channel<ImageProxy>,
        executor: ExecutorService,
    ): ImageAnalysis {
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        analysis.setAnalyzer(executor) { image ->
            if (frames.trySend(image).isFailure) {
                image.close()
            }
        }
        return analysis
    }

    private suspend fun bind(
        provider: ProcessCameraProvider,
        analysis: ImageAnalysis,
    ) {
        withContext(Dispatchers.Main) {
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        }
    }

    private suspend fun awaitResult(frames: Channel<ImageProxy>): Outcome<BarcodeResult> {
        val result =
            withTimeoutOrNull(scanTimeoutMillis) {
                for (frame in frames) {
                    when (val outcome = decoder.decode(frame)) {
                        is Outcome.Failure -> return@withTimeoutOrNull outcome
                        is Outcome.Success ->
                            outcome.value.firstOrNull()?.let { barcode ->
                                return@withTimeoutOrNull Outcome.success(barcode)
                            }
                    }
                }
                null
            }
        return result ?: failure("barcode scan timed out", ErrorCode.SCAN_002, retryable = true)
    }

    private suspend fun release(
        provider: ProcessCameraProvider,
        analysis: ImageAnalysis,
        executor: ExecutorService,
        frames: Channel<ImageProxy>,
    ) {
        withContext(Dispatchers.Main) { provider.unbindAll() }
        analysis.clearAnalyzer()
        executor.shutdown()
        frames.close()
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

    private fun failure(
        message: String,
        code: ErrorCode,
        retryable: Boolean,
        cause: Throwable? = null,
    ): Outcome<BarcodeResult> =
        Outcome.failure(
            CulinaryError.Platform(
                message = message,
                code = code,
                retryable = retryable,
                cause = cause,
            ),
        )

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 15_000
    }
}
