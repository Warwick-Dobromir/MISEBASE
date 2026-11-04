package com.culinary.core.domain.barcode

/**
 * 扫码帧去抖闸门（一次性消费）。
 *
 * 相机以远高于业务需要的帧率回调识别结果，同一个码会在连续帧里反复出现。规则：
 *
 * 1. **一次性消费**：接受一个码后闸门进入 `consumed`，在 [resume] 之前拒绝一切结果，
 *    避免"识别 → 导航 → 返回"期间又触发一次导入；
 * 2. **相同码去抖窗口**：[resume] 之后，同一个码在 [windowMillis] 内仍被拒绝，
 *    解决"页面恢复后同一帧再度命中"的重复触发；
 * 3. **不同码立即接受**：换一个码重新扫码不需要等待窗口；
 * 4. [reset] 清空历史（离开扫码页或主动重扫）。
 *
 * 本类是纯逻辑，不依赖 Android/ML Kit，时间由调用方传入，便于用固定时钟测试。
 */
class ScanFrameGate(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    init {
        require(windowMillis >= 0L) { "windowMillis must be >= 0, was $windowMillis" }
    }

    private var lastValue: String? = null
    private var lastAcceptedAtMillis: Long = Long.MIN_VALUE
    private var consumed: Boolean = false

    /** 当前是否已经被消费（等待 [resume] 或 [reset]）。 */
    val isConsumed: Boolean get() = consumed

    /** 最近一次被接受的码值；从未接受过为 `null`。 */
    val lastAcceptedValue: String? get() = lastValue

    /**
     * 提出一帧识别结果。
     *
     * @return `true` 表示本次应被业务消费（调用方随后必须停止分析或导航）。
     */
    fun offer(
        value: String,
        nowMillis: Long,
    ): Boolean {
        val candidate = value.trim()
        if (candidate.isEmpty() || consumed) return false
        val previous = lastValue
        if (previous != null &&
            previous == candidate &&
            nowMillis - lastAcceptedAtMillis < windowMillis
        ) {
            return false
        }
        lastValue = candidate
        lastAcceptedAtMillis = nowMillis
        consumed = true
        return true
    }

    /** 恢复扫码：解除一次性消费，但保留去抖窗口记忆。 */
    fun resume() {
        consumed = false
    }

    /** 完全重置：清空消费状态与去抖窗口。 */
    fun reset() {
        consumed = false
        lastValue = null
        lastAcceptedAtMillis = Long.MIN_VALUE
    }

    companion object {
        /** 默认去抖窗口：2 秒内同一码不重复触发。 */
        const val DEFAULT_WINDOW_MILLIS: Long = 2_000L
    }
}
