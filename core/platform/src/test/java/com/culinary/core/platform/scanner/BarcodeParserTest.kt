package com.culinary.core.platform.scanner

import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 手工输入兜底：规范化与最小校验（D-013）。 */
class BarcodeParserTest {
    @Test
    fun `normalizes spaces and hyphens`() {
        val result = BarcodeParser.parse(" 690 123-456 ")

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val barcode = (result as Outcome.Success).value
        assertThat(barcode.rawValue).isEqualTo("690123456")
        assertThat(barcode.format).isEqualTo("MANUAL")
    }

    @Test
    fun `accepts alphanumeric code 128 input`() {
        val result = BarcodeParser.parse("CUL-2026-0001")

        assertThat((result as Outcome.Success).value.rawValue).isEqualTo("CUL20260001")
    }

    @Test
    fun `rejects a too short code`() {
        val result = BarcodeParser.parse("12")

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
    }

    @Test
    fun `rejects an over long code`() {
        val result = BarcodeParser.parse("1".repeat(65))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
    }

    @Test
    fun `rejects control characters`() {
        val result = BarcodeParser.parse("1234\u0001")

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-VAL-001")
    }
}
