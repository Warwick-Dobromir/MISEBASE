package com.culinary.core.data.transaction

import androidx.room.withTransaction
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Room 事务协调器（E02 §4.2）。
 *
 * 锁顺序固定为 `writeMutex → SQLite 事务 → 行更新`；读操作不经过这里。
 * 非可重入：若在事务块内再次调用 [write] 会死锁，调用方必须避免嵌套。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：E03 §5 要求把未知异常映射为 CUL-UNKNOWN-001
class RoomTransactionRunner(
    private val database: CulinaryDatabase,
    private val dispatchers: DispatcherProvider,
    private val writeMutex: Mutex,
    private val retryPolicy: BusyRetryPolicy = BusyRetryPolicy(),
) : TransactionRunner {
    override suspend fun <T> write(block: suspend () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            writeMutex.withLock {
                try {
                    val value =
                        retryPolicy.retry {
                            database.withTransaction { block() }
                        }
                    Outcome.success(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome.failure(DbErrorMapper.map(e))
                }
            }
        }
}
