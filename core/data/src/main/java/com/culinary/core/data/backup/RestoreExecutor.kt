package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Provider

/** 成功恢复后的结果；调用方必须重建数据库连接（进程重启或新建实例）。 */
data class RestoreOutcome(
    val restoreId: String,
    val rollbackFile: File,
)

/** 启动时恢复现场的结果。 */
sealed interface RecoveryOutcome {
    data object NothingToRecover : RecoveryOutcome

    data class RolledBack(
        val restoreId: String,
    ) : RecoveryOutcome
}

/**
 * 完整恢复流程（E05 §13）：关闭连接 → 备份 → 装库 → 校验 → journal。
 *
 * **不包含** UI 与归档选择；归档校验/预检由调用方先完成，本类接收已解出的 DB 字节。
 * 失败会回滚到恢复前的库；回滚也失败则 journal 标记 `RECOVERY_REQUIRED`。
 *
 * 恢复成功后连接保持关闭：调用方必须重建 [CulinaryDatabase]（当前 App 走重启）。
 */
class RestoreExecutor(
    private val databaseProvider: Provider<CulinaryDatabase>,
    private val databaseFile: File,
    private val journalStore: RestoreJournalStore,
    private val rollbackDirectory: File,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 仅测试注入：在装库成功后执行，用于验证失败回滚；生产为 null。 */
    private val afterInstallHook: (() -> Unit)? = null,
) {
    @Suppress("TooGenericExceptionCaught") // E05 §13：恢复边界必须捕获任何失败并回滚
    suspend fun restoreDatabase(
        databaseBytes: ByteArray,
        restoreId: String,
    ): Outcome<RestoreOutcome> =
        withContext(ioDispatcher) {
            if (!hasSqliteHeader(databaseBytes)) {
                return@withContext failure("restored database is not a SQLite file")
            }
            if (hasUnfinishedJournal()) {
                return@withContext failure("an unfinished restore exists; recover it first")
            }

            val rollbackFile = File(rollbackDirectory, "$restoreId.db")
            writeJournal(restoreId, RestorePhase.PREPARED)

            try {
                closeDatabaseQuietly()
                writeJournal(restoreId, RestorePhase.DB_CLOSING)

                if (!rollbackDirectory.isDirectory && !rollbackDirectory.mkdirs()) {
                    throw IOException("cannot create rollback directory")
                }
                if (databaseFile.exists()) {
                    databaseFile.copyTo(rollbackFile, overwrite = true)
                }
                deleteSidecars()
                writeJournal(restoreId, RestorePhase.DB_MOVED_ASIDE)

                writeAtomically(databaseBytes)
                writeJournal(restoreId, RestorePhase.DB_INSTALLED)

                afterInstallHook?.invoke()
                verifyIntegrity()
                writeJournal(restoreId, RestorePhase.VERIFIED)
                writeJournal(restoreId, RestorePhase.DONE)

                Outcome.success(RestoreOutcome(restoreId, rollbackFile))
            } catch (e: Exception) {
                val rolledBack = rollback(rollbackFile)
                writeJournal(
                    restoreId,
                    if (rolledBack) RestorePhase.ROLLED_BACK else RestorePhase.RECOVERY_REQUIRED,
                )
                failure(
                    message =
                        if (rolledBack) {
                            "restore failed and was rolled back: ${e.message}"
                        } else {
                            "restore failed and rollback also failed: ${e.message}"
                        },
                    cause = e,
                )
            }
        }

    /**
     * 启动时调用：未完成的 journal 存在时，用回滚副本恢复现场。
     *
     * 覆盖"恢复过程中进程被杀"的情况；已完成/已回滚的 journal 直接清理。
     */
    @Suppress("TooGenericExceptionCaught") // 启动恢复边界：任何失败都保持 RECOVERY_REQUIRED
    suspend fun recoverIfInterrupted(): Outcome<RecoveryOutcome> =
        withContext(ioDispatcher) {
            val journal =
                when (val read = journalStore.read()) {
                    is Outcome.Failure -> return@withContext if (read.error.code == ErrorCode.NF_001) {
                        Outcome.success(RecoveryOutcome.NothingToRecover)
                    } else {
                        Outcome.failure(read.error)
                    }
                    is Outcome.Success -> read.value
                }

            if (journal.phase == RestorePhase.DONE || journal.phase == RestorePhase.ROLLED_BACK) {
                journalStore.clear()
                return@withContext Outcome.success(RecoveryOutcome.NothingToRecover)
            }

            val rollbackFile = File(rollbackDirectory, "${journal.restoreId}.db")
            if (!rollbackFile.exists()) {
                journalStore.clear()
                return@withContext Outcome.success(RecoveryOutcome.NothingToRecover)
            }

            return@withContext try {
                closeDatabaseQuietly()
                rollbackFile.copyTo(databaseFile, overwrite = true)
                deleteSidecars()
                writeJournal(journal.restoreId, RestorePhase.ROLLED_BACK)
                journalStore.clear()
                Outcome.success(RecoveryOutcome.RolledBack(journal.restoreId))
            } catch (e: Exception) {
                writeJournal(journal.restoreId, RestorePhase.RECOVERY_REQUIRED)
                Outcome.failure(
                    CulinaryError.Transfer(
                        message = "cannot recover the interrupted restore: ${e.message}",
                        code = ErrorCode.BACKUP_004,
                        cause = e,
                    ),
                )
            }
        }

    /** 恢复可能发生在数据库尚未打开时（启动恢复），因此关闭必须是尽力而为。 */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun closeDatabaseQuietly() {
        try {
            databaseProvider.get().close()
        } catch (e: Exception) {
            // 没有实例或已关闭：继续恢复流程
        }
    }

    private fun hasUnfinishedJournal(): Boolean =
        when (val read = journalStore.read()) {
            is Outcome.Failure -> false
            is Outcome.Success ->
                read.value.phase != RestorePhase.DONE && read.value.phase != RestorePhase.ROLLED_BACK
        }

    private fun writeJournal(
        restoreId: String,
        phase: RestorePhase,
    ) {
        val now = clock.now().toString()
        journalStore.write(
            RestoreJournal(
                restoreId = restoreId,
                phase = phase,
                stagingDir = databaseFile.parent.orEmpty(),
                rollbackDir = rollbackDirectory.absolutePath,
                startedAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun writeAtomically(databaseBytes: ByteArray) {
        val temporary = File(databaseFile.parentFile, "${databaseFile.name}.restore.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(databaseBytes)
                output.flush()
                output.fd.sync()
            }
            temporary.copyTo(databaseFile, overwrite = true)
        } finally {
            temporary.delete()
        }
    }

    private fun verifyIntegrity() {
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IOException("restored database failed integrity_check")
                }
            }
        }
    }

    @Suppress("SwallowedException") // 回滚失败由调用方转为 RECOVERY_REQUIRED，不在此处再抛
    private fun rollback(rollbackFile: File): Boolean =
        try {
            if (!rollbackFile.exists()) {
                false
            } else {
                rollbackFile.copyTo(databaseFile, overwrite = true)
                deleteSidecars()
                true
            }
        } catch (e: IOException) {
            false
        }

    private fun deleteSidecars() {
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
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
