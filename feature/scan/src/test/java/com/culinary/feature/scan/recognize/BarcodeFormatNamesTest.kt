package com.culinary.feature.scan.recognize

import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Test

/** ML Kit 格式常量 → 稳定名称；未知格式不猜名称。 */
class BarcodeFormatNamesTest {
    @Test
    fun `maps one dimensional formats`() {
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_EAN_13)).isEqualTo("EAN13")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_EAN_8)).isEqualTo("EAN8")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_UPC_A)).isEqualTo("UPC_A")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_CODE_128)).isEqualTo("CODE128")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_ITF)).isEqualTo("ITF")
    }

    @Test
    fun `maps two dimensional formats`() {
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_QR_CODE)).isEqualTo("QR")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_DATA_MATRIX)).isEqualTo("DATA_MATRIX")
        assertThat(BarcodeFormatNames.name(Barcode.FORMAT_PDF417)).isEqualTo("PDF417")
    }

    @Test
    fun `returns null for an unknown format`() {
        assertThat(BarcodeFormatNames.name(-1)).isNull()
    }
}
