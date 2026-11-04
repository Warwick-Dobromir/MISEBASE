package com.culinary.core.domain.scan

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 幂等键只由条码值决定：同码同键，不同来源/格式不影响幂等。 */
class ScanImportKeyTest {
    @Test
    fun `derives a stable key from the normalized value`() {
        val camera = ScanCapture(rawValue = "  6901234567892 ", format = "EAN13", source = ScanSource.CAMERA)
        val manual = ScanCapture(rawValue = "6901234567892", format = null, source = ScanSource.MANUAL)

        assertThat(ScanImportKey.of(camera)).isEqualTo("scan-import:6901234567892")
        assertThat(ScanImportKey.of(manual)).isEqualTo(ScanImportKey.of(camera))
    }

    @Test
    fun `different codes produce different keys`() {
        assertThat(ScanImportKey.of("code-a")).isNotEqualTo(ScanImportKey.of("code-b"))
    }

    @Test
    fun `raw string overload trims the value`() {
        assertThat(ScanImportKey.of("  kitchen-qr  ")).isEqualTo("scan-import:kitchen-qr")
    }
}
