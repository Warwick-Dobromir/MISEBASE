package com.culinary.feature.scan

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 扫码路由契约：识别结果经 URL 编码往返，缺参/超长返回 null 而不是抛异常。 */
class ScanRoutesTest {
    @Test
    fun `confirm route carries code format and source`() {
        val capture = ScanCapture(rawValue = "6901234567892", format = "EAN13", source = ScanSource.CAMERA)

        val route = ScanRoutes.confirm(capture)

        assertThat(route).startsWith("scan/confirm?")
        assertThat(route).contains("code=6901234567892")
        assertThat(route).contains("format=EAN13")
        assertThat(route).contains("source=CAMERA")
    }

    @Test
    fun `encodes qr payloads that contain spaces slashes and ampersands`() {
        val raw = "https://example.test/a b?x=1&y=2"

        val route = ScanRoutes.confirm(ScanCapture(rawValue = raw, format = "QR"))

        assertThat(route).doesNotContain(" ")
        assertThat(route).contains("%20")
        assertThat(route).contains("%2F")
        assertThat(route).contains("%26")
    }

    @Test
    fun `restores a capture from decoded navigation arguments`() {
        val capture = ScanRoutes.captureFrom(code = " 6901234567892 ", format = "EAN13", source = "CAMERA")

        assertThat(capture?.rawValue).isEqualTo("6901234567892")
        assertThat(capture?.format).isEqualTo("EAN13")
        assertThat(capture?.source).isEqualTo(ScanSource.CAMERA)
    }

    @Test
    fun `defaults source to camera and format to null`() {
        val capture = ScanRoutes.captureFrom(code = "kitchen-qr", format = "", source = "unknown")

        assertThat(capture?.source).isEqualTo(ScanSource.CAMERA)
        assertThat(capture?.format).isNull()
    }

    @Test
    fun `manual source survives the round trip`() {
        val capture = ScanRoutes.captureFrom(code = "code-a", format = null, source = "MANUAL")

        assertThat(capture?.source).isEqualTo(ScanSource.MANUAL)
    }

    @Test
    fun `missing or oversized codes are rejected`() {
        assertThat(ScanRoutes.captureFrom(code = null, format = null, source = null)).isNull()
        assertThat(ScanRoutes.captureFrom(code = "   ", format = null, source = null)).isNull()
        assertThat(
            ScanRoutes.captureFrom(
                code = "a".repeat(ScanCapture.MAX_VALUE_LENGTH + 1),
                format = null,
                source = null,
            ),
        ).isNull()
    }
}
