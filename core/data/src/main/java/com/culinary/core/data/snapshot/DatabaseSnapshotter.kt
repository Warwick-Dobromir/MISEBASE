package com.culinary.core.data.snapshot

import android.database.sqlite.SQLiteException
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 在线快照（E05 §6，G3-1）。
 *
 * 使用与写事务相同的 [Mutex] 作为写屏障：等待在途写事务结束、阻止新写事务进入，
 * 然后 `PRAGMA wal_checkpoint(TRUNCATE)` 把 WAL 合并回主库，再复制主库文件。
 *
 * **不需要关闭数据库**；快照完成后释放屏障，业务继续。
 * 复制期间只读查询不受影响，快照内容对应"屏障获取时刻已提交的数据"。
 */
class DatabaseSnapshotter(
    private val database: CulinaryDatabase,
    private val writeMutex: Mutex,
    private val databaseFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun snapshot(target: File): Outcome<Unit> =
        withContext(ioDispatcher) {
            try {
                writeMutex.withLock {
                    checkpoint()
                    target.parentFile?.mkdirs()
                    databaseFile.copyTo(target, overwrite = true)
                }
                if (!hasSqliteHeader(target)) {
                    return@withContext failure("snapshot is not a SQLite file")
                }
                Outcome.success(Unit)
            } catch (e: SQLiteException) {
                failure("cannot checkpoint before snapshot: ${e.message}", e)
            } catch (e: IOException) {
                failure("cannot write snapshot: ${e.message}", e)
            }
        }

    private fun checkpoint() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { cursor ->
                // 结果行是 busy/log/checkpointed，不需要读取；执行本身即生效。
                cursor.moveToFirst()
            }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { stream -> stream.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun failure(
        message: String,
        cause: Throwable? = null,
    ): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Data(
                message = message,
                code = ErrorCode.DB_002,
                retryable = true,
                cause = cause,
            ),
        )

    private companion object {
        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    }
}
