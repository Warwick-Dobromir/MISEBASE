package com.culinary.core.data.backup

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.common.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 数据库文件级恢复（E05 §13）。
 *
 * 顺序：校验 SQLite 头 → 备份现有库到 rollback 目录 → 清除 -wal/-shm →
 * 临时文件 + fsync + 原子 rename。任一步失败都会把 rollback 复制回原位置。
 *
 * **调用方必须在数据库连接关闭时调用**；本类不负责关闭 Room。
 */
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
        return try {
            if (databaseFile.exists()) {
                databaseFile.copyTo(rollback, overwrite = true)
            }
            deleteSidecars()
            writeAtomically(databaseBytes)
            Outcome.success(rollback)
        } catch (e: IOException) {
            rollbackBack(rollback)
            failure("cannot restore database: ${e.message}", e)
        }
    }

    private fun writeAtomically(databaseBytes: ByteArray) {
        val temporary = File(databaseFile.parentFile, "${databaseFile.name}.restore.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(databaseBytes)
                output.flush()
                output.fd.sync()
            }
            moveIntoPlace(temporary)
        } finally {
            temporary.delete()
        }
    }

    @Suppress("SwallowedException") // ATOMIC_MOVE 不被支持时改用普通移动，属于预期降级
    private fun moveIntoPlace(temporary: File) {
        try {
            Files.move(
                temporary.toPath(),
                databaseFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteSidecars() {
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
    }

    @Suppress("SwallowedException") // 回滚是最后一步：失败也不能覆盖原始恢复错误
    private fun rollbackBack(rollback: File) {
        if (!rollback.exists()) return
        runCatching { rollback.copyTo(databaseFile, overwrite = true) }
    }

    private fun hasSqliteHeader(bytes: ByteArray): Boolean {
        if (bytes.size < SQLITE_HEADER.size) return false
        return SQLITE_HEADER.indices.all { index -> bytes[index] == SQLITE_HEADER[index] }
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
        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    }
}
