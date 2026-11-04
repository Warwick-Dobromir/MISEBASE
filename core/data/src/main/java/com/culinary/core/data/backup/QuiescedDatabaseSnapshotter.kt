package com.culinary.core.data.backup

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.common.AppLogger
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Provider

/** 已校验的一致性数据库快照；调用方负责在 finally 中删除 [file]。 */
data class DatabaseSnapshot(
    val file: File,
    val schemaVersion: Int,
    val journalMode: String,
) {
    fun delete(): Boolean = file.delete()
}

/**
 * `QUIESCED_CHECKPOINT_COPY` 快照器（E05 §6.3）。
 *
 * 顺序：写屏障 → `wal_checkpoint(TRUNCATE)` → `quick_check` → 复制主库 → 校验快照 → 释放屏障。
 * checkpoint 返回 busy/未截断立即失败，**不继续复制**；读写模式与 Schema 均不改变。
 */
@Suppress("TooGenericExceptionCaught") // 快照边界：任何 IO/平台异常都必须转为可诊断失败
class QuiescedDatabaseSnapshotter(
    private val databaseProvider: Provider<CulinaryDatabase>,
    private val writeBarrier: WriteBarrier,
    private val snapshotDirectory: File,
    private val dispatchers: DispatcherProvider,
    private val logger: AppLogger,
    private val barrierTimeoutMillis: Long = WriteBarrier.DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun capture(snapshotId: String): Outcome<DatabaseSnapshot> =
        withContext(dispatchers.io) {
            val live =
                when (val opened = openLiveDatabase()) {
                    is Outcome.Success -> opened.value
                    is Outcome.Failure -> return@withContext opened
                }
            val livePath =
                live.path ?: return@withContext failure("database_path_missing", "database path is null")

            when (writeBarrier.acquireForBackup(barrierTimeoutMillis)) {
                BarrierAcquireResult.TIMEOUT ->
                    return@withContext failure("write_barrier_timeout", "timed out waiting for active writes")
                BarrierAcquireResult.ALREADY_HELD ->
                    return@withContext failure("write_barrier_nested", "write barrier is already held")
                BarrierAcquireResult.ACQUIRED -> Unit
            }

            try {
                captureUnderBarrier(live, File(livePath), snapshotId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("backup_snapshot_failed", e)
                failure("snapshot_io_error", e.message, e)
            } finally {
                if (!writeBarrier.releaseForBackup()) {
                    logger.warn("write_barrier_release_failed")
                }
            }
        }

    private fun captureUnderBarrier(
        live: SupportSQLiteDatabase,
        liveFile: File,
        snapshotId: String,
    ): Outcome<DatabaseSnapshot> {
        validateLiveDatabase(live)?.let { reason ->
            return failure(reason, "live database validation failed")
        }
        val schemaVersion = userVersion(live)
        val journalMode = journalMode(live)
        val snapshotFile = copyToSnapshot(liveFile, snapshotId)
        verifySnapshot(snapshotFile, schemaVersion)?.let { reason ->
            snapshotFile.delete()
            return failure(reason, "snapshot verification failed")
        }
        logger.info(
            "backup_snapshot_ready",
            fields =
                mapOf(
                    "schema_version" to schemaVersion,
                    "journal_mode" to journalMode,
                    "size_bytes" to snapshotFile.length(),
                ),
        )
        return Outcome.success(
            DatabaseSnapshot(
                file = snapshotFile,
                schemaVersion = schemaVersion,
                journalMode = journalMode,
            ),
        )
    }

    private fun openLiveDatabase(): Outcome<SupportSQLiteDatabase> =
        try {
            Outcome.success(databaseProvider.get().openHelper.writableDatabase)
        } catch (e: Exception) {
            failure("database_unavailable", e.message, e)
        }

    /** checkpoint 与 quick_check 都通过才返回 null。 */
    private fun validateLiveDatabase(live: SupportSQLiteDatabase): String? = checkpoint(live) ?: quickCheck(live)

    /** 返回 null 表示成功，否则返回稳定 reason（写入错误 context，供 Worker 分类）。 */
    private fun checkpoint(database: SupportSQLiteDatabase): String? {
        val state =
            try {
                readCheckpoint(database)
            } catch (e: Exception) {
                logger.warn("wal_checkpoint_failed", e)
                return "checkpoint_error"
            }
        if (state == null) return "checkpoint_no_result"
        logger.info(
            "wal_checkpoint",
            fields = mapOf("busy" to state.busy, "log" to state.log, "checkpointed" to state.checkpointed),
        )
        return when {
            state.busy != 0 -> "checkpoint_busy"
            state.log != state.checkpointed -> "checkpoint_incomplete"
            else -> null
        }
    }

    private fun readCheckpoint(database: SupportSQLiteDatabase): CheckpointState? =
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                CheckpointState(
                    busy = cursor.getInt(0),
                    log = cursor.getInt(1),
                    checkpointed = cursor.getInt(2),
                )
            }
        }

    private fun quickCheck(database: SupportSQLiteDatabase): String? =
        try {
            database.query("PRAGMA quick_check").use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != QUICK_CHECK_OK) {
                    "quick_check_failed"
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("quick_check_failed", e)
            "quick_check_error"
        }

    private fun userVersion(database: SupportSQLiteDatabase): Int =
        database.query("PRAGMA user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun journalMode(database: SupportSQLiteDatabase): String =
        database.query("PRAGMA journal_mode").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
        }

    private fun copyToSnapshot(
        liveFile: File,
        snapshotId: String,
    ): File {
        if (!snapshotDirectory.isDirectory && !snapshotDirectory.mkdirs()) {
            throw IOException("cannot create snapshot directory")
        }
        val target = File(snapshotDirectory, "snapshot-$snapshotId.db")
        if (target.canonicalPath == liveFile.canonicalPath) {
            throw IOException("snapshot target equals live database")
        }
        liveFile.inputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        return target
    }

    private fun verifySnapshot(
        file: File,
        expectedSchemaVersion: Int,
    ): String? =
        try {
            openAndVerifySnapshot(file, expectedSchemaVersion)
        } catch (e: Exception) {
            logger.warn("snapshot_verify_failed", e)
            "snapshot_open_failed"
        } finally {
            File("${file.path}-wal").delete()
            File("${file.path}-shm").delete()
        }

    private fun openAndVerifySnapshot(
        file: File,
        expectedSchemaVersion: Int,
    ): String? =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            when {
                readQuickCheck(database) != QUICK_CHECK_OK -> "snapshot_quick_check_failed"
                readUserVersion(database) != expectedSchemaVersion -> "snapshot_schema_mismatch"
                else -> null
            }
        }

    private fun readQuickCheck(database: SQLiteDatabase): String? =
        database.rawQuery("PRAGMA quick_check", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun readUserVersion(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else -1
        }

    private fun failure(
        reason: String,
        message: String?,
        cause: Throwable? = null,
    ): Outcome<Nothing> {
        val code =
            if (reason == "quick_check_failed" || reason == "snapshot_quick_check_failed") {
                ErrorCode.BACKUP_001
            } else {
                ErrorCode.BACKUP_003
            }
        return Outcome.failure(
            CulinaryError.Transfer(
                message = "backup snapshot failed: ${message ?: reason}",
                code = code,
                cause = cause,
                context = mapOf("reason" to reason),
            ),
        )
    }

    private data class CheckpointState(
        val busy: Int,
        val log: Int,
        val checkpointed: Int,
    )

    private companion object {
        const val QUICK_CHECK_OK = "ok"
    }
}
