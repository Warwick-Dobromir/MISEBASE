package com.culinary.feature.scan.recognize

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import com.culinary.core.domain.barcode.ScanCapture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * 单帧解码器（D-013：ML Kit 17.3.0 Bundled，本地识别、无网络）。
 *
 * 每帧调用 [decode]，成功/失败都通过回调返回，并且**总是关闭** [ImageProxy]，
 * 否则 ImageAnalysis 会因缓冲耗尽而停止出帧。
 */
internal class MlKitBarcodeDecoder(
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(),
) {
    @SuppressLint("UnsafeOptInUsageError")
    fun decode(
        imageProxy: ImageProxy,
        onResult: (List<ScanCapture>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(emptyList())
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner
            .process(image)
            .addOnSuccessListener { barcodes ->
                onResult(BarcodeValueMapper.toCaptures(barcodes.map { it.rawValue to it.format }))
            }.addOnFailureListener(onFailure)
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        scanner.close()
    }
}
