package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.common.SystemClock
import java.io.File
import java.io.IOException

/**
 * 数据库文件级恢复（E05 §13）。
 *
 * 顺序：校验 SQLite 头 → 备份现有库到 rollback 目录并校验 → 清除 -wal/-shm →
 * 同目录临时文件 + fsync + 原子 rename + fsync 目录。任一步失败都会把 rollback 复制回原位置。
 *
 * **调用方必须在数据库连接关闭时调用**；本类不负责关闭 Room。
 */
@Suppress("TooGenericExceptionCaught")
class DatabaseRestorer(
    private val databaseFile: File,
    private val rollbackDirectory: File,
    private val clock: Clock = SystemClock,
) {
    fun restore(databaseBytes: ByteArray): Outcome<File> {
        if (!hasSqliteHeader(databaseBytes)) {
            return failure("restored database is not a SQLite file")
        }
        if (!rollbackDirectory.isDirectory && !rollbackDirectory.mkdirs()) {
            return failure("cannot create rollback directory")
        }

        val rollback = File(rollbackDirectory, "culinary-${clock.now().toEpochMilli()}.db")
        var installAttempted = false
        return try {
            if (databaseFile.exists() && databaseFile.length() > 0L) {
                AtomicFiles.copyAtomically(databaseFile, rollback)
                verifyDatabaseFile(rollback, "rollback copy")
            }
            deleteSidecars()
            installAttempted = true
            AtomicFiles.writeBytesAtomically(databaseFile, databaseBytes)
            Outcome.success(rollback)
        } catch (e: IOException) {
            if (installAttempted) rollbackBack(rollback)
            failure("cannot restore database: ${e.message}", e)
        }
    }

    @Suppress("ThrowsCount") // 每个校验失败点都需要明确标签
    private fun verifyDatabaseFile(
        file: File,
        label: String,
    ) {
        if (!file.isFile) throw IOException("$label is missing")
        if (!hasSqliteHeader(file)) throw IOException("$label is not a SQLite file")
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val quickCheck =
                database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            if (quickCheck != QUICK_CHECK_OK) throw IOException("$label failed quick_check")
        }
    }

    private fun deleteSidecars() {
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
    }

    @Suppress("SwallowedException") // 回滚是最后一步：失败也不能覆盖原始恢复错误
    private fun rollbackBack(rollback: File) {
        if (!rollback.exists()) return
        runCatching {
            AtomicFiles.copyAtomically(rollback, databaseFile)
            deleteSidecars()
        }
    }

    private fun hasSqliteHeader(bytes: ByteArray): Boolean {
        if (bytes.size < SQLITE_HEADER.size) return false
        return SQLITE_HEADER.indices.all { index -> bytes[index] == SQLITE_HEADER[index] }
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
            CulinaryError.Transfer(
                message = message,
                code = ErrorCode.BACKUP_003,
                cause = cause,
            ),
        )

    private companion object {
        const val QUICK_CHECK_OK = "ok"

        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    }
}
