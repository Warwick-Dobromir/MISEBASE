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
 * 完整恢复流程（E05 §13）：写屏障 → checkpoint → 关连接 → 回滚副本 → 原子装库 → 校验 → journal。
 *
 * **不包含** UI 与归档选择；归档校验/预检由调用方先完成，本类接收已解出的 DB 字节。
 * 装库使用“同目录临时文件 + fsync + 原子 rename + fsync 目录”；失败回滚同理。
 * 回滚也失败时 journal 标记 `RECOVERY_REQUIRED`，保留现场，不自动清库。
 *
 * 恢复成功后连接保持关闭：调用方必须重建 [CulinaryDatabase]（当前 App 走重启）。
 */
@Suppress("TooGenericExceptionCaught") // E05 §13：恢复边界必须捕获任何失败并回滚
class RestoreExecutor(
    private val databaseProvider: Provider<CulinaryDatabase>,
    private val databaseFile: File,
    private val journalStore: RestoreJournalStore,
    private val rollbackDirectory: File,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writeBarrier: WriteBarrier = WriteBarrier(),
    private val barrierTimeoutMillis: Long = WriteBarrier.DEFAULT_TIMEOUT_MILLIS,
    /** 仅测试注入：在装库成功后执行，用于验证失败回滚；生产为 null。 */
    private val afterInstallHook: (() -> Unit)? = null,
    /** 仅测试注入：在回滚副本复制后、校验前执行；生产为 null。 */
    private val afterRollbackCopyHook: (() -> Unit)? = null,
) {
    @Suppress("LongMethod") // 恢复提交序列按 E05 顺序排列，拆开反而隐藏顺序约束
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
            when (writeBarrier.acquireForBackup(barrierTimeoutMillis)) {
                BarrierAcquireResult.TIMEOUT ->
                    return@withContext failure("write barrier timeout; restore aborted")
                BarrierAcquireResult.ALREADY_HELD ->
                    return@withContext failure("write barrier is already held; restore aborted")
                BarrierAcquireResult.ACQUIRED -> Unit
            }

            try {
                try {
                    checkpointLiveDatabase()
                } catch (e: IOException) {
                    return@withContext failure("cannot checkpoint before restore: ${e.message}", e)
                }

                val rollbackFile = File(rollbackDirectory, "$restoreId.db")
                writeJournal(restoreId, RestorePhase.PREPARED)
                var installAttempted = false
                try {
                    closeDatabaseQuietly()
                    writeJournal(restoreId, RestorePhase.DB_CLOSING)

                    ensureRollbackDirectory()
                    if (databaseFile.exists() && databaseFile.length() > 0L) {
                        AtomicFiles.copyAtomically(databaseFile, rollbackFile)
                        afterRollbackCopyHook?.invoke()
                        verifyDatabaseFile(rollbackFile, "rollback copy")
                    }
                    deleteSidecars()
                    writeJournal(restoreId, RestorePhase.DB_MOVED_ASIDE)

                    installAttempted = true
                    AtomicFiles.writeBytesAtomically(databaseFile, databaseBytes)
                    writeJournal(restoreId, RestorePhase.DB_INSTALLED)

                    afterInstallHook?.invoke()
                    verifyDatabaseFile(databaseFile, "restored database")
                    writeJournal(restoreId, RestorePhase.VERIFIED)
                    writeJournal(restoreId, RestorePhase.DONE)

                    Outcome.success(RestoreOutcome(restoreId, rollbackFile))
                } catch (e: Exception) {
                    // 未尝试装库时原库仍完好：绝不能用未通过校验的回滚副本覆盖它。
                    val rolledBack = if (installAttempted) rollback(rollbackFile) else true
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
            } finally {
                writeBarrier.releaseForBackup()
            }
        }

    /**
     * 启动时调用：未完成的 journal 存在时，用回滚副本恢复现场。
     *
     * 覆盖"恢复过程中进程被杀"的情况；已完成/已回滚的 journal 直接清理。
     */
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
                // 没有可用回滚副本：保留现场，不猜、不自动清库。
                writeJournal(journal.restoreId, RestorePhase.RECOVERY_REQUIRED)
                return@withContext failure("restore rollback copy is missing; recovery required")
            }

            when (writeBarrier.acquireForBackup(barrierTimeoutMillis)) {
                BarrierAcquireResult.TIMEOUT ->
                    return@withContext failure("write barrier timeout; recovery aborted")
                BarrierAcquireResult.ALREADY_HELD ->
                    return@withContext failure("write barrier is already held; recovery aborted")
                BarrierAcquireResult.ACQUIRED -> Unit
            }

            try {
                closeDatabaseQuietly()
                AtomicFiles.copyAtomically(rollbackFile, databaseFile)
                deleteSidecars()
                verifyDatabaseFile(databaseFile, "recovered database")
                writeJournal(journal.restoreId, RestorePhase.ROLLED_BACK)
                journalStore.clear()
                Outcome.success(RecoveryOutcome.RolledBack(journal.restoreId))
            } catch (e: Exception) {
                writeJournal(journal.restoreId, RestorePhase.RECOVERY_REQUIRED)
                failure("cannot recover the interrupted restore: ${e.message}", e)
            } finally {
                writeBarrier.releaseForBackup()
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

    /** 关闭前把 WAL 中已提交事务落主库；busy/未截断必须失败，避免回滚副本缺数据。 */
    @Suppress("ThrowsCount") // 每个失败点都需要明确的诊断信息
    private fun checkpointLiveDatabase() {
        val database =
            try {
                databaseProvider.get()
            } catch (e: Exception) {
                throw IOException("cannot open database for checkpoint", e)
            }
        val supportDatabase =
            try {
                database.openHelper.writableDatabase
            } catch (e: Exception) {
                throw IOException("cannot open database for checkpoint", e)
            }
        supportDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (!cursor.moveToFirst()) throw IOException("wal checkpoint returned no result")
            val busy = cursor.getInt(0)
            val log = cursor.getInt(1)
            val checkpointed = cursor.getInt(2)
            if (busy != 0 || log != checkpointed) {
                throw IOException("wal checkpoint did not complete: busy=$busy log=$log checkpointed=$checkpointed")
            }
        }
    }

    private fun ensureRollbackDirectory() {
        if (!rollbackDirectory.isDirectory && !rollbackDirectory.mkdirs()) {
            throw IOException("cannot create rollback directory")
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
            if (readPragma(database, "quick_check") != QUICK_CHECK_OK) {
                throw IOException("$label failed quick_check")
            }
            val foreignKeyViolation =
                database.rawQuery("PRAGMA foreign_key_check", null).use { cursor -> cursor.moveToFirst() }
            if (foreignKeyViolation) throw IOException("$label failed foreign_key_check")
        }
    }

    private fun readPragma(
        database: SQLiteDatabase,
        pragma: String,
    ): String? =
        database.rawQuery("PRAGMA $pragma", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
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

    @Suppress("SwallowedException") // 回滚失败由调用方转为 RECOVERY_REQUIRED，不在此处再抛
    private fun rollback(rollbackFile: File): Boolean =
        try {
            if (!rollbackFile.exists()) {
                false
            } else {
                AtomicFiles.copyAtomically(rollbackFile, databaseFile)
                deleteSidecars()
                verifyDatabaseFile(databaseFile, "rolled-back database")
                true
            }
        } catch (e: Exception) {
            false
        }

    private fun deleteSidecars() {
        File("${databaseFile.path}-wal").delete()
        File("${databaseFile.path}-shm").delete()
    }

    private fun hasSqliteHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { stream -> stream.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
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
        const val QUICK_CHECK_OK = "ok"

        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
    }
}
