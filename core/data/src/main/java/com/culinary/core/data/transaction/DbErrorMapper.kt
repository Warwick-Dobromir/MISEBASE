package com.culinary.core.data.transaction

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import java.io.IOException

/**
 * 数据库异常 → 错误码（E03 §5 / E02 §8.2）。
 *
 * - 约束冲突：`CUL-CON-001`，不自动重试；
 * - BUSY/LOCKED：`CUL-DB-001`，可重试；
 * - 损坏：`CUL-DB-002`，进入恢复，不删文件；
 * - 磁盘满/IO：`CUL-FILE-001`；
 * - 其他：`CUL-UNKNOWN-001`，必须记录堆栈。
 */
object DbErrorMapper {
    fun map(error: Throwable): CulinaryError =
        when {
            error is SQLiteConstraintException ->
                CulinaryError.Conflict(
                    entity = "database",
                    code = ErrorCode.CON_001,
                    retryable = false,
                    cause = error,
                )

            error.isBusySqliteError() ->
                CulinaryError.Data(
                    message = "database is busy",
                    code = ErrorCode.DB_001,
                    retryable = true,
                    cause = error,
                )

            error is SQLiteDatabaseCorruptException ->
                CulinaryError.Data(
                    message = "database is corrupt",
                    code = ErrorCode.DB_002,
                    retryable = false,
                    cause = error,
                )

            error is SQLiteFullException || error is SQLiteDiskIOException || error is IOException ->
                CulinaryError.Data(
                    message = "storage failure",
                    code = ErrorCode.FILE_001,
                    retryable = true,
                    cause = error,
                )

            else ->
                CulinaryError.Unexpected(
                    message = error.message ?: error::class.java.simpleName,
                    cause = error,
                )
        }
}
