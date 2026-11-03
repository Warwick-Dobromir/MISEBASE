package com.culinary.core.platform.scanner

import android.annotation.SuppressLint
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.BarcodeResult
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit 条码解码器（D-013，bundled 模型，本地识别、无网络）。
 *
 * 只负责把一帧 [ImageProxy] 解成领域结果；相机绑定、预览与生命周期在 UI 层。
 * ML Kit 的 Task 失败映射为 `CUL-SCAN-002`（可重试），由 UI 决定重试或手工输入。
 */
class MlKitBarcodeDecoder(
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(),
) {
    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalGetImage::class)
    suspend fun decode(imageProxy: ImageProxy): Outcome<List<BarcodeResult>> =
        suspendCancellableCoroutine { continuation ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                continuation.resume(Outcome.success(emptyList()))
                return@suspendCancellableCoroutine
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner
                .process(image)
                .addOnSuccessListener { barcodes ->
                    continuation.resume(
                        Outcome.success(
                            barcodes.mapNotNull { map(it.rawValue, it.format) },
                        ),
                    )
                }.addOnFailureListener { error ->
                    continuation.resume(
                        Outcome.failure(
                            CulinaryError.Platform(
                                message = "barcode decode failed",
                                code = ErrorCode.SCAN_002,
                                retryable = true,
                                cause = error,
                            ),
                        ),
                    )
                }.addOnCompleteListener { imageProxy.close() }
        }

    companion object {
        internal fun map(
            rawValue: String?,
            format: Int,
        ): BarcodeResult? =
            rawValue
                ?.takeIf { it.isNotBlank() }
                ?.let { BarcodeResult(rawValue = it, format = BarcodeFormats.name(format)) }
    }
}
