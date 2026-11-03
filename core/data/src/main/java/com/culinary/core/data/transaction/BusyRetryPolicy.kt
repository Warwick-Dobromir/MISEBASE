package com.culinary.core.data.transaction

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

/**
 * BUSY/LOCKED 的有限重试（E02 §8.2 / §8.3）。
 *
 * - 最多 3 次，退避 50/150/400 ms + 抖动；
 * - 包住**整个事务**，不在事务中间重试单条 SQL；
 * - `CancellationException` 直接抛出，不重试；
 * - 约束/损坏/磁盘满等错误不重试。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：E03 §5 要求把未知异常映射为 CUL-UNKNOWN-001
class BusyRetryPolicy(
    private val maxAttempts: Int = 3,
    private val backoffMillis: List<Long> = listOf(50L, 150L, 400L),
) {
    suspend fun <T> retry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!e.isBusySqliteError() || attempt >= maxAttempts - 1) throw e
                val base = backoffMillis.getOrElse(attempt) { backoffMillis.last() }
                delay(base + Random.nextLong(0, 50))
                attempt++
            }
        }
    }
}

/** SQLite 的 BUSY/LOCKED 在不同 API 上消息措辞不同，统一按文本判定。 */
internal fun Throwable.isBusySqliteError(): Boolean {
    val text = message?.lowercase(Locale.ROOT) ?: return false
    return text.contains("database is locked") ||
        text.contains("database is busy") ||
        text.contains("sqlite_busy") ||
        text.contains("sqlite_locked")
}
