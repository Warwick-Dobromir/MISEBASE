package com.culinary.feature.scan.recognize

import com.culinary.core.domain.barcode.ScanCapture

/**
 * 多帧一致性确认：同一条码必须连续出现在指定数量的有效分析帧中。
 *
 * 空帧、校验失败帧或内容/格式变化都会清空连续计数；确认一次后保持锁定，
 * 直到相机扫描会话重置，避免确认页导航前重复发出结果。
 */
internal class BarcodeFrameConsensus(
    private val requiredMatches: Int = DEFAULT_REQUIRED_MATCHES,
) {
    init {
        require(requiredMatches >= 1) { "requiredMatches must be >= 1" }
    }

    private var candidate: ScanCapture? = null
    private var consecutiveMatches = 0
    private var confirmed = false

    @Synchronized
    fun offer(frameCapture: ScanCapture?): ScanCapture? {
        if (confirmed) return null
        if (frameCapture == null || !BarcodeChecksumValidator.isValid(frameCapture)) {
            clearCandidate()
            return null
        }

        val previous = candidate
        if (previous != null &&
            previous.normalizedValue == frameCapture.normalizedValue &&
            previous.format == frameCapture.format
        ) {
            consecutiveMatches += 1
        } else {
            candidate = frameCapture
            consecutiveMatches = 1
        }

        if (consecutiveMatches < requiredMatches) return null
        confirmed = true
        return frameCapture
    }

    @Synchronized
    fun reset() {
        candidate = null
        consecutiveMatches = 0
        confirmed = false
    }

    private fun clearCandidate() {
        candidate = null
        consecutiveMatches = 0
    }

    companion object {
        const val DEFAULT_REQUIRED_MATCHES = 3
    }
}
