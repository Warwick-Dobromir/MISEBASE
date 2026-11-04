package com.culinary.core.data.backup

import com.culinary.core.common.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 获取写屏障的结果；调用方必须区分超时、嵌套与成功，不得把失败当成功。 */
enum class BarrierAcquireResult {
    ACQUIRED,
    TIMEOUT,
    ALREADY_HELD,
}

/** 写屏障诊断快照；只用于日志与测试，不参与业务判定。 */
data class WriteBarrierSnapshot(
    val activeWriters: Int,
    val queuedWriters: Int,
    val barrierHeld: Boolean,
)

/**
 * 应用级写屏障（E05 §6.3.1）。
 *
 * **单写者语义**：全应用只有这一把 [Mutex]。业务写经 [withWrite] 排队；备份/恢复经
 * [acquireForBackup] 独占并暂停新写；读事务不经过本类，因此备份期间读取继续。
 *
 * **不新增第二把写锁**：本类就是 `RoomTransactionRunner` 使用的同一把锁。
 * **非可重入**：持有屏障时再次获取返回 [BarrierAcquireResult.ALREADY_HELD]；持有屏障时
 * 禁止调用任何写入口（会自锁）。
 * **不持久化**：屏障是进程内内存状态；进程被杀后随进程消失，重启即恢复，不会留下永久锁。
 */
class WriteBarrier(
    private val mutex: Mutex = Mutex(),
    private val defaultTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val logger: AppLogger? = null,
) {
    private val activeWriters = AtomicInteger(0)
    private val queuedWriters = AtomicInteger(0)
    private val barrierHeld = AtomicBoolean(false)

    /** 业务写入口；与备份屏障共享同一把 [Mutex]。 */
    suspend fun <T> withWrite(block: suspend () -> T): T {
        queuedWriters.incrementAndGet()
        try {
            mutex.lock()
            activeWriters.incrementAndGet()
            try {
                return block()
            } finally {
                activeWriters.decrementAndGet()
                mutex.unlock()
            }
        } finally {
            queuedWriters.decrementAndGet()
        }
    }

    /**
     * 备份/恢复获取屏障。
     *
     * @return [BarrierAcquireResult.TIMEOUT] 时调用方必须失败返回，不得继续备份。
     */
    suspend fun acquireForBackup(timeoutMillis: Long = defaultTimeoutMillis): BarrierAcquireResult {
        require(timeoutMillis > 0) { "write barrier timeout must be positive" }
        if (barrierHeld.get()) return BarrierAcquireResult.ALREADY_HELD

        val acquired = withTimeoutOrNull(timeoutMillis) { mutex.lock() } != null
        if (!acquired) {
            logger?.warn(
                "write_barrier_timeout",
                fields = mapOf("timeout_ms" to timeoutMillis, "active_writers" to activeWriters.get()),
            )
            return if (barrierHeld.get()) BarrierAcquireResult.ALREADY_HELD else BarrierAcquireResult.TIMEOUT
        }
        if (!barrierHeld.compareAndSet(false, true)) {
            mutex.unlock()
            return BarrierAcquireResult.ALREADY_HELD
        }
        logger?.info(
            "write_barrier_acquired",
            fields = mapOf("active_writers" to activeWriters.get(), "queued_writers" to queuedWriters.get()),
        )
        return BarrierAcquireResult.ACQUIRED
    }

    /** 释放屏障；未持有时返回 false，绝不重复解锁。 */
    fun releaseForBackup(): Boolean {
        if (!barrierHeld.compareAndSet(true, false)) return false
        mutex.unlock()
        logger?.info("write_barrier_released")
        return true
    }

    fun snapshot(): WriteBarrierSnapshot =
        WriteBarrierSnapshot(
            activeWriters = activeWriters.get(),
            queuedWriters = queuedWriters.get(),
            barrierHeld = barrierHeld.get(),
        )

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 5_000
    }
}
