package com.culinary.core.platform.work

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.platform.AutoBackupRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * 自动备份 Worker（MB-D-007 事项 3）。
 *
 * 不再固定返回 `success`：只有 `AutoBackupRunner` 返回“归档存在 + 校验通过 + `backup_record`
 * 已写”才 `success`；可重试失败 `retry`；不可重试失败 `failure`。取消时由运行器清理临时文件。
 *
 * 输入/输出只用 [Data] 传备份 ID / 路径 / 记录 ID，不传 DB 或 ZIP 字节。
 */
class AutoBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // 边界：任何失败都必须变成真实 WorkManager 结果
    override suspend fun doWork(): Result {
        val requestedId = inputData.getString(KEY_BACKUP_ID)
        val targetDirectory = inputData.getString(KEY_TARGET_DIRECTORY)
        val runner =
            try {
                AutoBackupWorkerDependencies.runner(applicationContext)
            } catch (e: Exception) {
                return Result.failure(failureData(requestedId, "runner_unavailable"))
            }

        val outcome =
            try {
                runner.run(AutoBackupRequest(backupId = requestedId, targetDirectory = targetDirectory))
            } catch (e: CancellationException) {
                return Result.failure(failureData(requestedId, "cancelled"))
            } catch (e: Exception) {
                return Result.failure(failureData(requestedId, "worker_exception"))
            }

        return when (outcome) {
            is AutoBackupRunResult.Success ->
                Result.success(
                    workDataOf(
                        KEY_BACKUP_ID to outcome.backupId,
                        KEY_ARCHIVE_PATH to outcome.archivePath,
                        KEY_RECORD_ID to outcome.recordId,
                    ),
                )

            is AutoBackupRunResult.Retry -> Result.retry()
            is AutoBackupRunResult.Failure -> Result.failure(failureData(outcome.backupId, outcome.reason))
        }
    }

    companion object {
        const val KEY_BACKUP_ID: String = "backup_id"
        const val KEY_TARGET_DIRECTORY: String = "target_directory"
        const val KEY_ARCHIVE_PATH: String = "archive_path"
        const val KEY_RECORD_ID: String = "record_id"
        const val KEY_REASON: String = "reason"

        internal fun failureData(
            backupId: String?,
            reason: String,
        ): Data {
            val builder = Data.Builder().putString(KEY_REASON, reason)
            if (backupId != null) builder.putString(KEY_BACKUP_ID, backupId)
            return builder.build()
        }
    }
}

/** 生产解析 Hilt 绑定；测试可覆盖为假运行器。 */
object AutoBackupWorkerDependencies {
    @Volatile
    private var testRunner: AutoBackupRunner? = null

    @VisibleForTesting
    fun installForTest(runner: AutoBackupRunner?) {
        testRunner = runner
    }

    internal fun runner(context: Context): AutoBackupRunner =
        testRunner
            ?: EntryPointAccessors
                .fromApplication(context.applicationContext, AutoBackupWorkerEntryPoint::class.java)
                .autoBackupRunner()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupWorkerEntryPoint {
    fun autoBackupRunner(): AutoBackupRunner
}
