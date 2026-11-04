package com.culinary.core.domain.barcode

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 扫码帧去抖与一次性消费（MB-D-004，Stage 5）。
 *
 * 覆盖：连续帧不重复触发、一次性消费、去抖窗口、恢复后同码抑制、异码立即接受、重置。
 */
class ScanFrameGateTest {
    @Test
    fun `accepts the first frame of a code`() {
        val gate = ScanFrameGate(windowMillis = 2_000)

        assertThat(gate.offer("6901234567892", nowMillis = 1_000)).isTrue()
        assertThat(gate.isConsumed).isTrue()
        assertThat(gate.lastAcceptedValue).isEqualTo("6901234567892")
    }

    @Test
    fun `rejects every following frame until resumed`() {
        val gate = ScanFrameGate(windowMillis = 2_000)

        gate.offer("code-a", nowMillis = 1_000)

        assertThat(gate.offer("code-a", nowMillis = 1_010)).isFalse()
        assertThat(gate.offer("code-a", nowMillis = 1_050)).isFalse()
        assertThat(gate.offer("code-b", nowMillis = 1_060)).isFalse()
    }

    @Test
    fun `rejects the same code inside the debounce window after resume`() {
        val gate = ScanFrameGate(windowMillis = 2_000)
        gate.offer("code-a", nowMillis = 1_000)

        gate.resume()

        assertThat(gate.offer("code-a", nowMillis = 1_500)).isFalse()
        assertThat(gate.offer("code-a", nowMillis = 2_999)).isFalse()
    }

    @Test
    fun `accepts the same code again after the debounce window`() {
        val gate = ScanFrameGate(windowMillis = 2_000)
        gate.offer("code-a", nowMillis = 1_000)
        gate.resume()

        assertThat(gate.offer("code-a", nowMillis = 3_000)).isTrue()
    }

    @Test
    fun `accepts a different code immediately after resume`() {
        val gate = ScanFrameGate(windowMillis = 2_000)
        gate.offer("code-a", nowMillis = 1_000)
        gate.resume()

        assertThat(gate.offer("code-b", nowMillis = 1_001)).isTrue()
        assertThat(gate.lastAcceptedValue).isEqualTo("code-b")
    }

    @Test
    fun `normalizes surrounding whitespace before comparing`() {
        val gate = ScanFrameGate(windowMillis = 2_000)
        gate.offer("  code-a  ", nowMillis = 1_000)
        gate.resume()

        assertThat(gate.lastAcceptedValue).isEqualTo("code-a")
        assertThat(gate.offer("code-a", nowMillis = 1_100)).isFalse()
    }

    @Test
    fun `ignores blank values`() {
        val gate = ScanFrameGate(windowMillis = 2_000)

        assertThat(gate.offer("   ", nowMillis = 1_000)).isFalse()
        assertThat(gate.isConsumed).isFalse()
    }

    @Test
    fun `reset clears consumption and window memory`() {
        val gate = ScanFrameGate(windowMillis = 2_000)
        gate.offer("code-a", nowMillis = 1_000)

        gate.reset()

        assertThat(gate.isConsumed).isFalse()
        assertThat(gate.lastAcceptedValue).isNull()
        assertThat(gate.offer("code-a", nowMillis = 1_001)).isTrue()
    }
}
