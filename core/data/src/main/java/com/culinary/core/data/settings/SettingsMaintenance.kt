package com.culinary.core.data.settings

import android.content.Context
import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.backup.BackupArchiveVerifier
import com.culinary.core.data.backup.RestoreExecutor
import com.culinary.core.data.backup.RestoreJournalStore
import com.culinary.core.data.backup.RestorePrecheck
import com.culinary.core.data.backup.WriteBarrier
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.platform.AutoBackupRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Provider

/** 手动备份导出结果：归档名与字节数，用于界面回显。 */
data class BackupExportResult(
    val archiveName: String,
    val sizeBytes: Long,
)

/** 恢复结果：备份来源信息与应用内生成的操作 id。 */
data class RestoreArchiveResult(
    val restoreId: String,
    val backupAppVersionName: String,
    val backupSchemaVersion: Int,
    val backupCreatedAt: String,
)

/**
 * 设置页的备份/恢复出口（Stage 6 UI 接线）。
 *
 * 只做编排复用：备份走既有 [AutoBackupRunner]（写屏障 + checkpoint + 归档 + 校验 + 记录），
 * 恢复走既有 `BackupArchiveVerifier` → `RestorePrecheck` → [RestoreExecutor] 序列，
 * **不新增/修改任何备份算法或数据库结构**。
 *
 * 放在 `core:data` 的原因：`app` 的依赖白名单不含 `core:domain`/`core:database`
 * （`config/module-dependencies.json`），而 `BackupStoragePort`、`AutoBackupRunner`、
 * `CulinaryDatabase` 都在这些层。本类把实现细节收敛在数据层，对外只暴露
 * `core:common` 结果类型与 Java IO 流，UI 不需要感知端口与数据库句柄。
 */
class SettingsMaintenance
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val runner: AutoBackupRunner,
        private val databaseProvider: Provider<CulinaryDatabase>,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
        private val writeBarrier: WriteBarrier,
        private val dispatchers: DispatcherProvider,
    ) {
        /** 当前数据库 schema 版本（`PRAGMA user_version`），用于诊断与恢复预检。 */
        fun currentSchemaVersion(): Int =
            runCatching {
                databaseProvider.get().openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }.getOrDefault(0)

        /** 当前数据库文件字节数；文件不存在返回 0。 */
        fun databaseSizeBytes(): Long = databaseFile().let { file -> if (file.isFile) file.length() else 0L }

        /**
         * 生成一份完整备份并写入调用方提供的流（SAF 输出流）。
         *
         * 成功条件与自动备份一致：归档存在、校验通过、`backup_record` 已写；
         * 失败返回可展示的 [CulinaryError]。
         */
        suspend fun exportBackup(output: OutputStream): Outcome<BackupExportResult> =
            withContext(dispatchers.io) {
                when (val result = runner.run(AutoBackupRequest())) {
                    is AutoBackupRunResult.Success -> copyArchive(result.archivePath, output)
                    is AutoBackupRunResult.Retry ->
                        failure(
                            message = "backup runner is retryable: ${result.reason}",
                            code = ErrorCode.BACKUP_003,
                            retryable = true,
                        )

                    is AutoBackupRunResult.Failure ->
                        failure(
                            message = "backup failed: ${result.reason}",
                            code = ErrorCode.BACKUP_001,
                        )
                }
            }

        /**
         * 从归档恢复数据库。
         *
         * 顺序：整包读入 → 校验（结构/manifest/checksums/逐文件哈希）→ 预检（格式与 schema 兼容）
         * → 取出数据库条目 → [RestoreExecutor]（写屏障、关连接、回滚副本、原子装库、journal）。
         *
         * **成功后数据库连接保持关闭**：调用方必须重启应用再使用。
         */
        suspend fun restoreBackup(input: InputStream): Outcome<RestoreArchiveResult> =
            withContext(dispatchers.io) {
                val archive =
                    try {
                        input.use { it.readBytes() }
                    } catch (e: IOException) {
                        return@withContext failure(
                            message = "cannot read the selected archive: ${e.message}",
                            code = ErrorCode.BACKUP_003,
                            retryable = true,
                            cause = e,
                        )
                    }

                val manifest =
                    when (val verified = BackupArchiveVerifier().verify(ByteArrayInputStream(archive))) {
                        is Outcome.Success -> verified.value
                        is Outcome.Failure -> return@withContext verified
                    }

                val precheckResult =
                    // v2/v3 备份在 v4 上通过已测试的迁移链恢复；更老版本仍需显式迁移路径。
                    when (
                        val prechecked =
                            RestorePrecheck(
                                currentSchemaVersion = currentSchemaVersion(),
                                testedMigrationSources = setOf(2, 3),
                            ).check(manifest)
                    ) {
                        is Outcome.Success -> prechecked.value
                        is Outcome.Failure -> return@withContext prechecked
                    }

                val databaseFiles = precheckResult.manifest.database
                val databasePath = databaseFiles.single().path
                val databaseBytes =
                    readEntry(archive, databasePath)
                        ?: return@withContext failure(
                            message = "restored database entry is missing: $databasePath",
                            code = ErrorCode.BACKUP_001,
                        )

                val restoreId = idGenerator.newId()
                when (val restored = restoreExecutor().restoreDatabase(databaseBytes, restoreId)) {
                    is Outcome.Success ->
                        Outcome.success(
                            RestoreArchiveResult(
                                restoreId = restored.value.restoreId,
                                backupAppVersionName = manifest.app.versionName,
                                backupSchemaVersion = manifest.app.schemaVersion,
                                backupCreatedAt = manifest.createdAt,
                            ),
                        )

                    is Outcome.Failure -> restored
                }
            }

        private fun copyArchive(
            archivePath: String,
            output: OutputStream,
        ): Outcome<BackupExportResult> {
            val archive = File(archivePath)
            if (!archive.isFile) {
                return failure("backup archive is missing: $archivePath", ErrorCode.BACKUP_001)
            }
            return try {
                archive.inputStream().use { input -> input.copyTo(output) }
                output.flush()
                Outcome.success(BackupExportResult(archive.name, archive.length()))
            } catch (e: IOException) {
                failure(
                    message = "cannot write the backup archive: ${e.message}",
                    code = ErrorCode.BACKUP_003,
                    retryable = true,
                    cause = e,
                )
            }
        }

        private fun readEntry(
            archive: ByteArray,
            entryName: String,
        ): ByteArray? {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        return zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
            return null
        }

        private fun restoreExecutor(): RestoreExecutor =
            RestoreExecutor(
                databaseProvider = databaseProvider,
                databaseFile = databaseFile(),
                journalStore = RestoreJournalStore(File(restoreDirectory(), JOURNAL_FILE)),
                rollbackDirectory = File(restoreDirectory(), ROLLBACK_DIRECTORY),
                clock = clock,
                ioDispatcher = dispatchers.io,
                writeBarrier = writeBarrier,
            )

        private fun databaseFile(): File = context.getDatabasePath(CulinaryDatabase.DATABASE_NAME)

        private fun restoreDirectory(): File = File(context.filesDir, RESTORE_DIRECTORY)

        private fun failure(
            message: String,
            code: ErrorCode,
            retryable: Boolean = false,
            cause: Throwable? = null,
        ): Outcome<Nothing> =
            Outcome.failure(
                CulinaryError.Transfer(
                    message = message,
                    code = code,
                    retryable = retryable,
                    cause = cause,
                ),
            )

        private companion object {
            const val RESTORE_DIRECTORY = "restore"
            const val JOURNAL_FILE = "journal.json"
            const val ROLLBACK_DIRECTORY = "rollback"
        }
    }
