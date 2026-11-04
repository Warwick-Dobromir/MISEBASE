package com.culinary.core.domain.scan

import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 事件级幂等键（MB-S5-002）：同一次确认同一个键，不同次确认不同键。
 *
 * 覆盖：条码规范化、operation id 参与、转义（`%` / `:`）、前缀匹配、旧键兼容。
 */
class ScanImportKeyTest {
    @Test
    fun `same capture and operation id produce the same key across sources`() {
        val camera = ScanCapture(rawValue = "  6901234567892 ", format = "EAN13", source = ScanSource.CAMERA)
        val manual = ScanCapture(rawValue = "6901234567892", format = null, source = ScanSource.MANUAL)

        assertThat(ScanImportKey.of(camera, "op-1")).isEqualTo("scan-import:6901234567892:op-1")
        assertThat(ScanImportKey.of(manual, "op-1")).isEqualTo(ScanImportKey.of(camera, "op-1"))
    }

    @Test
    fun `different operation ids produce different keys for the same code`() {
        val capture = ScanCapture(rawValue = "6901234567892")

        val first = ScanImportKey.of(capture, "op-1")
        val second = ScanImportKey.of(capture, "op-2")

        assertThat(first).isNotEqualTo(second)
        assertThat(first).startsWith(ScanImportKey.prefixFor(capture))
        assertThat(second).startsWith(ScanImportKey.prefixFor(capture))
    }

    @Test
    fun `different codes produce different keys with the same operation id`() {
        assertThat(ScanImportKey.of(ScanCapture(rawValue = "code-a"), "op-1"))
            .isNotEqualTo(ScanImportKey.of(ScanCapture(rawValue = "code-b"), "op-1"))
    }

    @Test
    fun `operation id is trimmed and must not be blank`() {
        val capture = ScanCapture(rawValue = "code-a")

        assertThat(ScanImportKey.of(capture, "  op-1  ")).isEqualTo("scan-import:code-a:op-1")

        val error = runCatching { ScanImportKey.of(capture, "   ") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `escapes percent and colon so barcode prefixes cannot collide`() {
        val short = ScanCapture(rawValue = "https://a")
        val longer = ScanCapture(rawValue = "https://a:b")

        val shortKey = ScanImportKey.of(short, "op-1")
        val longerKey = ScanImportKey.of(longer, "op-1")

        assertThat(shortKey).isEqualTo("scan-import:https%3A//a:op-1")
        assertThat(ScanImportKey.isForBarcode(shortKey, short)).isTrue()
        assertThat(ScanImportKey.isForBarcode(longerKey, short)).isFalse()
        assertThat(ScanImportKey.isForBarcode(longerKey, longer)).isTrue()
    }

    @Test
    fun `matches only keys of the same barcode`() {
        val capture = ScanCapture(rawValue = "6901234567892")

        assertThat(ScanImportKey.isForBarcode("scan-import:6901234567892:op-1", capture)).isTrue()
        assertThat(ScanImportKey.isForBarcode("scan-import:6901234567893:op-1", capture)).isFalse()
    }

    @Test
    fun `legacy per-barcode key still counts as imported`() {
        val capture = ScanCapture(rawValue = "6901234567892")

        assertThat(ScanImportKey.isForBarcode("scan-import:6901234567892", capture)).isTrue()
        assertThat(ScanImportKey.isForBarcode("scan-import:6901234567893", capture)).isFalse()
    }
}
