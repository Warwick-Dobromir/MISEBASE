package com.culinary.core.domain.platform

/**
 * 自动备份执行端口（MB-D-007 事项 3）。
 *
 * `core:platform` 的 `AutoBackupWorker` 只负责把 [AutoBackupRunResult] 映射为 WorkManager
 * 结果；真正的写屏障、checkpoint、归档与 `backup_record` 由 `core:data` 实现。
 * 端口不暴露 Android/WorkManager 类型。
 */
interface AutoBackupRunner {
    suspend fun run(request: AutoBackupRequest): AutoBackupRunResult
}

/** Worker 输入：只传标量；[targetDirectory] 缺省时由实现选择应用私有目录。 */
data class AutoBackupRequest(
    val backupId: String? = null,
    val targetDirectory: String? = null,
)

/** 自动备份的真实结果；成功必须同时满足归档存在、校验通过、记录已写。 */
sealed interface AutoBackupRunResult {
    data class Success(
        val backupId: String,
        val archivePath: String,
        val recordId: String,
    ) : AutoBackupRunResult

    /** 空间不足、临时 IO、checkpoint busy 等可重试失败。 */
    data class Retry(
        val backupId: String,
        val reason: String,
    ) : AutoBackupRunResult

    /** 权限拒绝、归档校验失败、写屏障超时等不可重试失败。 */
    data class Failure(
        val backupId: String,
        val reason: String,
    ) : AutoBackupRunResult
}
