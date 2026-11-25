package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodeChecksumValidatorTest {
    @Test
    fun `accepts valid EAN13 EAN8 UPC-A and UPC-E values`() {
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("6901234567892", "EAN13"))).isTrue()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("96385074", "EAN8"))).isTrue()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("036000291452", "UPC_A"))).isTrue()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("04252614", "UPC_E"))).isTrue()
    }

    @Test
    fun `rejects incorrect check digits and invalid numeric lengths`() {
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("6901234567893", "EAN13"))).isFalse()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("96385075", "EAN8"))).isFalse()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("036000291453", "UPC_A"))).isFalse()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("14252614", "UPC_E"))).isFalse()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("69012345678A2", "EAN13"))).isFalse()
    }

    @Test
    fun `does not apply GS1 checksum rules to other formats`() {
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("kitchen-qr", "QR"))).isTrue()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("ABC123", "CODE128"))).isTrue()
        assertThat(BarcodeChecksumValidator.isValid(ScanCapture("plain-value", null))).isTrue()
    }
}
