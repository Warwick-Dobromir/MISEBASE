package com.culinary.feature.scan.recognize

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 真机解码验证（MB-D-011）。
 *
 * 用与相机分析路径相同的 ML Kit 17.3.0 Bundled 客户端，解码打包在 `androidTest/assets`
 * 的 EAN-13 / Code128 / 二维码图片，证明"识别能力在真实设备上可用"；
 * 相机取景与权限交互另见真机测试记录。
 */
class BarcodeDecodingInstrumentedTest {
    @Test
    fun decodesEan13Barcode() {
        assertDecodes(asset = "barcode_ean13.png", expected = "6901234567892", expectedFormat = Barcode.FORMAT_EAN_13)
    }

    @Test
    fun decodesCode128Barcode() {
        assertDecodes(asset = "barcode_code128.png", expected = "MISEBASE123", expectedFormat = Barcode.FORMAT_CODE_128)
    }

    @Test
    fun decodesQrText() {
        assertDecodes(asset = "qr_text.png", expected = "MISEBASE-QR-SCAN-001", expectedFormat = Barcode.FORMAT_QR_CODE)
    }

    @Test
    fun decodesQrUrl() {
        assertDecodes(
            asset = "qr_url.png",
            expected = "https://example.test/scan?item=soy sauce&lot=42",
            expectedFormat = Barcode.FORMAT_QR_CODE,
        )
    }

    private fun assertDecodes(
        asset: String,
        expected: String,
        expectedFormat: Int,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        checkNotNull(bitmap) { "cannot decode asset $asset" }
        val scanner = BarcodeScanning.getClient()
        val latch = CountDownLatch(1)
        var results: List<Barcode> = emptyList()
        var failure: Throwable? = null
        scanner
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                results = it
                latch.countDown()
            }.addOnFailureListener {
                failure = it
                latch.countDown()
            }
        assertTrue("decode timed out for $asset", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        failure?.let { throw AssertionError("decode failed for $asset", it) }
        val match = results.firstOrNull { it.rawValue == expected }
        assertTrue(
            "expected $expected in ${results.map { it.rawValue }}",
            match != null,
        )
        assertEquals("format of $asset", expectedFormat, match?.format)
        scanner.close()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}
