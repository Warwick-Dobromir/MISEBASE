package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanCapture
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodeFrameConsensusTest {
    private fun ean(value: String = "6901234567892") = ScanCapture(value, "EAN13")

    @Test
    fun `requires three consecutive identical valid frames`() {
        val consensus = BarcodeFrameConsensus()

        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isEqualTo(ean())
    }

    @Test
    fun `resets consecutive count after an empty frame`() {
        val consensus = BarcodeFrameConsensus()

        consensus.offer(ean())
        consensus.offer(ean())
        consensus.offer(null)

        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isEqualTo(ean())
    }

    @Test
    fun `restarts count when barcode value or format changes`() {
        val consensus = BarcodeFrameConsensus()

        consensus.offer(ean())
        consensus.offer(ean())
        consensus.offer(ScanCapture("036000291452", "UPC_A"))
        assertThat(consensus.offer(ScanCapture("036000291452", "UPC_A"))).isNull()
        assertThat(consensus.offer(ScanCapture("036000291452", "UPC_A"))?.rawValue)
            .isEqualTo("036000291452")
    }

    @Test
    fun `rejects checksum failure and resets the sequence`() {
        val consensus = BarcodeFrameConsensus()

        consensus.offer(ean())
        consensus.offer(ean())
        assertThat(consensus.offer(ScanCapture("6901234567893", "EAN13"))).isNull()
        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isNull()
        assertThat(consensus.offer(ean())).isEqualTo(ean())
    }

    @Test
    fun `does not emit more than once until reset`() {
        val consensus = BarcodeFrameConsensus()

        repeat(3) { consensus.offer(ean()) }
        assertThat(consensus.offer(ean())).isNull()

        consensus.reset()
        assertThat(consensus.offer(ean())).isNull()
    }
}
