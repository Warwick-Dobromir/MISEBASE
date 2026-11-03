package com.culinary.core.platform.scanner

import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Test

/** ML Kit 格式常量到稳定名称的映射；未知格式返回 null，不猜。 */
class BarcodeFormatsTest {
    @Test
    fun `maps known formats`() {
        assertThat(BarcodeFormats.name(Barcode.FORMAT_EAN_13)).isEqualTo("EAN13")
        assertThat(BarcodeFormats.name(Barcode.FORMAT_EAN_8)).isEqualTo("EAN8")
        assertThat(BarcodeFormats.name(Barcode.FORMAT_UPC_A)).isEqualTo("UPC_A")
        assertThat(BarcodeFormats.name(Barcode.FORMAT_CODE_128)).isEqualTo("CODE128")
        assertThat(BarcodeFormats.name(Barcode.FORMAT_QR_CODE)).isEqualTo("QR")
    }

    @Test
    fun `returns null for an unknown format`() {
        assertThat(BarcodeFormats.name(-1)).isNull()
    }
}
