package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanSource
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Test

/** 原始识别值 → 领域识别结果：空值与超长值被丢弃，格式与来源被补齐。 */
class BarcodeValueMapperTest {
    @Test
    fun `keeps raw value and adds format name and source`() {
        val capture = BarcodeValueMapper.toCapture("6901234567892", "EAN13")

        assertThat(capture).isNotNull()
        assertThat(capture?.rawValue).isEqualTo("6901234567892")
        assertThat(capture?.format).isEqualTo("EAN13")
        assertThat(capture?.source).isEqualTo(ScanSource.CAMERA)
    }

    @Test
    fun `marks manual captures with manual source`() {
        val capture = BarcodeValueMapper.toCapture("kitchen-qr", null, ScanSource.MANUAL)

        assertThat(capture?.source).isEqualTo(ScanSource.MANUAL)
        assertThat(capture?.format).isNull()
    }

    @Test
    fun `drops blank values`() {
        assertThat(BarcodeValueMapper.toCapture(null, "QR")).isNull()
        assertThat(BarcodeValueMapper.toCapture("   ", "QR")).isNull()
    }

    @Test
    fun `drops values longer than the contract limit`() {
        val tooLong = "a".repeat(513)

        assertThat(BarcodeValueMapper.toCapture(tooLong, "QR")).isNull()
    }

    @Test
    fun `maps decode results and skips unusable entries`() {
        val captures =
            BarcodeValueMapper.toCaptures(
                listOf(
                    "6901234567892" to Barcode.FORMAT_EAN_13,
                    null to Barcode.FORMAT_QR_CODE,
                    "kitchen-qr" to Barcode.FORMAT_QR_CODE,
                ),
            )

        assertThat(captures).hasSize(2)
        assertThat(captures.map { it.format }).containsExactly("EAN13", "QR").inOrder()
    }
}
