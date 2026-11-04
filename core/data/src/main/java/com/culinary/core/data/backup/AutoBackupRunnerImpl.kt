package com.culinary.core.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.culinary.core.common.AppLogger
import com.culinary.core.common.Clock
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.util.Sha256
import com.culinary.core.database.dao.BackupRecordDao
import com.culinary.core.database.entity.BackupRecordEntity
import com.culinary.core.database.model.BackupKindDb
import com.culinary.core.database.model.BackupStatusDb
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.platform.AutoBackupRunner
import com.culinary.core.domain.transaction.TransactionRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AccessDeniedException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 测试注入点；生产不设置。 */
internal enum class BackupStage {
    AFTER_SNAPSHOT,
    AFTER_ARCHIVE_WRITE,
    BEFORE_RECORD_WRITE,
}

/**
 * 自动备份编排（MB-D-007 事项 1–3）。
 *
 * 成功条件：归档存在 + `BackupArchiveVerifier` 通过 + `backup_record` 写入成功。
 * 写屏障/checkpoint 由 [QuiescedDatabaseSnapshotter] 负责；归档打包与记录写入在屏障外。
 * `backup_record` 经 [TransactionRunner] 写入，仍使用同一把应用级写锁。
 */
@Suppress("TooGenericExceptionCaught") // 备份边界：任何 IO/平台异常都必须转为真实结果
class AutoBackupRunnerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val snapshotter: QuiescedDatabaseSnapshotter,
        private val backupRecordDao: BackupRecordDao,
        private val transactionRunner: TransactionRunner,
        private val dispatchers: DispatcherProvider,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
        private val logger: AppLogger,
    ) : AutoBackupRunner {
        private val writer = BackupArchiveWriter(clock)
        private val verifier = BackupArchiveVerifier()

        /** 测试注入：替换剩余空间探测。 */
        internal var freeSpaceProvider: (File) -> Long = { file -> file.usableSpace }

        /** 测试注入：在各阶段观察/破坏产物；生产为 null。 */
        internal var stageHook: ((BackupStage, File?) -> Unit)? = null

        /** 测试注入：覆盖目标目录，避免依赖外部存储。 */
        internal var targetDirectoryOverride: File? = null

        override suspend fun run(request: AutoBackupRequest): AutoBackupRunResult =
            withContext(dispatchers.io) {
                val backupId = request.backupId ?: idGenerator.newId()
                val target = resolveTarget(request.targetDirectory)
                if (!ensureDirectory(target)) {
                    return@withContext AutoBackupRunResult.Failure(backupId, "target_directory_unavailable")
                }

                val snapshot =
                    when (val captured = snapshotter.capture(backupId)) {
                        is Outcome.Success -> captured.value
                        is Outcome.Failure -> return@withContext snapshotFailure(backupId, captured.error.context)
                    }
                execute(backupId, target, snapshot)
            }

        @Suppress("LongMethod", "ReturnCount") // 备份提交序列按 E05 顺序排列，拆开反而隐藏顺序约束
        private suspend fun execute(
            backupId: String,
            target: File,
            snapshot: DatabaseSnapshot,
        ): AutoBackupRunResult {
            var temporary: File? = null
            var finalFile: File? = null
            var recordWritten = false
            try {
                if (freeSpaceProvider(target) < snapshot.file.length() + MIN_HEADROOM_BYTES) {
                    return AutoBackupRunResult.Retry(backupId, "insufficient_storage")
                }
                stageHook?.invoke(BackupStage.AFTER_SNAPSHOT, snapshot.file)

                val name = archiveName(backupId)
                val temporaryFile = File(target, ".$name.tmp")
                val archive = File(target, name)
                temporary = temporaryFile
                finalFile = archive
                writeArchive(temporaryFile, snapshot, backupId)
                stageHook?.invoke(BackupStage.AFTER_ARCHIVE_WRITE, temporaryFile)

                verifyArchive(temporaryFile)?.let { reason ->
                    return AutoBackupRunResult.Failure(backupId, reason)
                }

                AtomicFiles.moveIntoPlace(temporaryFile, archive)
                AtomicFiles.fsyncDirectory(target)
                stageHook?.invoke(BackupStage.BEFORE_RECORD_WRITE, archive)

                val record =
                    when (val written = writeRecord(backupId, archive, snapshot.schemaVersion)) {
                        is Outcome.Success -> written.value
                        is Outcome.Failure -> return recordFailure(backupId, written.error.retryable)
                    }
                recordWritten = true
                logger.info(
                    "auto_backup_success",
                    fields =
                        mapOf(
                            "backup_id" to backupId,
                            "record_id" to record,
                            "size_bytes" to archive.length(),
                        ),
                )
                return AutoBackupRunResult.Success(
                    backupId = backupId,
                    archivePath = archive.absolutePath,
                    recordId = record,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("auto_backup_failed", e, fields = mapOf("backup_id" to backupId))
                return classify(backupId, e)
            } finally {
                temporary?.delete()
                snapshot.delete()
                if (!recordWritten) finalFile?.delete()
            }
        }

        private fun writeArchive(
            target: File,
            snapshot: DatabaseSnapshot,
            backupId: String,
        ) {
            val (versionName, versionCode) = appVersion()
            FileOutputStream(target).use { output ->
                writer.write(
                    output = output,
                    databaseFile = snapshot.file,
                    appVersionName = versionName,
                    appVersionCode = versionCode,
                    schemaVersion = snapshot.schemaVersion,
                    installId = installId(),
                    device = BackupDevice(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT),
                    backupId = backupId,
                )
            }
            RandomAccessFile(target, "rw").use { file -> file.fd.sync() }
        }

        private fun verifyArchive(file: File): String? =
            when (val verified = file.inputStream().use { verifier.verify(it) }) {
                is Outcome.Success -> null
                is Outcome.Failure -> {
                    logger.warn(
                        "auto_backup_archive_invalid",
                        fields = mapOf("reason" to verified.error.code.value),
                    )
                    "archive_verification_failed"
                }
            }

        private suspend fun writeRecord(
            backupId: String,
            archive: File,
            schemaVersion: Int,
        ): Outcome<String> {
            val recordId = idGenerator.newId()
            val record =
                BackupRecordEntity(
                    id = recordId,
                    kind = BackupKindDb.AUTO,
                    fileName = archive.name,
                    fileUri = archive.toURI().toString(),
                    createdAt = clock.now().toEpochMilli(),
                    sizeBytes = archive.length(),
                    sha256 = Sha256.of(archive.readBytes()),
                    schemaVersion = schemaVersion,
                    appVersionName = appVersion().first,
                    status = BackupStatusDb.VERIFIED,
                    note = backupId,
                )
            return when (val written = transactionRunner.write { backupRecordDao.insert(record) }) {
                is Outcome.Success -> Outcome.success(recordId)
                is Outcome.Failure -> written
            }
        }

        private fun snapshotFailure(
            backupId: String,
            context: Map<String, String>,
        ): AutoBackupRunResult {
            val reason = context["reason"] ?: "snapshot_failed"
            return when (reason) {
                "write_barrier_timeout",
                "write_barrier_nested",
                "quick_check_failed",
                "snapshot_quick_check_failed",
                "snapshot_schema_mismatch",
                -> AutoBackupRunResult.Failure(backupId, reason)

                else -> AutoBackupRunResult.Retry(backupId, reason)
            }
        }

        private fun recordFailure(
            backupId: String,
            retryable: Boolean,
        ): AutoBackupRunResult =
            if (retryable) {
                AutoBackupRunResult.Retry(backupId, "backup_record_write_failed")
            } else {
                AutoBackupRunResult.Failure(backupId, "backup_record_write_failed")
            }

        private fun classify(
            backupId: String,
            error: Throwable,
        ): AutoBackupRunResult =
            when {
                error is AccessDeniedException || error is SecurityException ->
                    AutoBackupRunResult.Failure(backupId, "permission_denied")

                isNoSpace(error) -> AutoBackupRunResult.Retry(backupId, "insufficient_storage")
                error is IOException -> AutoBackupRunResult.Retry(backupId, "temporary_io_error")
                else -> AutoBackupRunResult.Failure(backupId, "unexpected_error")
            }

        private fun isNoSpace(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                val message = current.message?.lowercase().orEmpty()
                if (message.contains("no space") || message.contains("enospc") || message.contains("disk full")) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        private fun resolveTarget(requestedDirectory: String?): File =
            targetDirectoryOverride
                ?: requestedDirectory?.let { File(it).absoluteFile }
                ?: defaultBackupDirectory()

        private fun defaultBackupDirectory(): File {
            val external = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            return if (external != null) File(external, "CULINARY/Backups") else File(context.filesDir, "backups")
        }

        private fun ensureDirectory(directory: File): Boolean = directory.isDirectory || directory.mkdirs()

        private fun archiveName(backupId: String): String {
            val stamp = TIMESTAMP_FORMAT.format(clock.now())
            val suffix = backupId.filter { it.isLetterOrDigit() || it == '-' }.take(8).ifEmpty { "backup" }
            return "misebase-auto-$stamp-$suffix.zip"
        }

        @Suppress("DEPRECATION", "SwallowedException") // minSdk 26：versionCode 兼容路径
        private fun appVersion(): Pair<String, Int> =
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                (info.versionName ?: "unknown") to info.versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown" to 0
            }

        private fun installId(): String {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val existing = preferences.getString(KEY_INSTALL_ID, null)
            if (existing != null) return existing
            val generated = idGenerator.newId()
            preferences.edit().putString(KEY_INSTALL_ID, generated).apply()
            return generated
        }

        private companion object {
            const val MIN_HEADROOM_BYTES: Long = 16L * 1024 * 1024
            const val PREFERENCES_NAME: String = "misebase-backup"
            const val KEY_INSTALL_ID: String = "install_id"

            val TIMESTAMP_FORMAT: DateTimeFormatter =
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        }
    }
