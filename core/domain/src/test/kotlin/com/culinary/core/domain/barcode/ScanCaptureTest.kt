package com.culinary.core.domain.barcode

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 识别结果契约：来源、格式标签与长度校验。 */
class ScanCaptureTest {
    @Test
    fun `keeps raw value and trims only for normalization`() {
        val capture = ScanCapture(rawValue = "  6901234567892 ")

        assertThat(capture.rawValue).isEqualTo("  6901234567892 ")
        assertThat(capture.normalizedValue).isEqualTo("6901234567892")
        assertThat(capture.source).isEqualTo(ScanSource.CAMERA)
    }

    @Test
    fun `reports unknown format label when format is missing`() {
        val capture = ScanCapture(rawValue = "https://example.test/a", format = null)

        assertThat(capture.formatLabel).isEqualTo(ScanCapture.UNKNOWN_FORMAT)
    }

    @Test
    fun `uses the ml kit format name when present`() {
        val capture = ScanCapture(rawValue = "6901234567892", format = "EAN13")

        assertThat(capture.formatLabel).isEqualTo("EAN13")
    }

    @Test
    fun `rejects blank raw values`() {
        val error = runCatching { ScanCapture(rawValue = "  ") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validates manual input length and blankness`() {
        assertThat(ScanCapture.validate("")).isNotNull()
        assertThat(ScanCapture.validate("   ")).isNotNull()
        assertThat(ScanCapture.validate("a".repeat(ScanCapture.MAX_VALUE_LENGTH))).isNull()
        assertThat(ScanCapture.validate("a".repeat(ScanCapture.MAX_VALUE_LENGTH + 1))).isNotNull()
    }
}
