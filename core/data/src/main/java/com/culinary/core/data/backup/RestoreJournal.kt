package com.culinary.core.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 恢复 journal（E05 §13.2）。
 *
 * 固定路径 `filesDir/restore.journal`；用"临时文件 + fsync + 原子 rename"更新。
 * 每次文件移动前先写 `INTENT`，完成后写 `DONE`，再推进 [phase]。
 */
@Serializable
data class RestoreJournal(
    @SerialName("restore_id")
    val restoreId: String,
    val phase: RestorePhase,
    val operations: List<RestoreOperation> = emptyList(),
    @SerialName("staging_dir")
    val stagingDir: String,
    @SerialName("rollback_dir")
    val rollbackDir: String,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

/** 恢复阶段；失败统一进入 [ROLLBACK_STARTED]/[ROLLED_BACK]/[RECOVERY_REQUIRED]。 */
@Serializable
enum class RestorePhase {
    PREPARED,
    DB_CLOSING,
    DB_MOVED_ASIDE,
    DB_INSTALLED,
    ATTACHMENTS_MOVED_ASIDE,
    ATTACHMENTS_INSTALLED,
    MIGRATING,
    VERIFIED,
    DONE,
    ROLLBACK_STARTED,
    ROLLED_BACK,
    RECOVERY_REQUIRED,
}

@Serializable
data class RestoreOperation(
    val id: String,
    val kind: RestoreOperationKind,
    val source: String,
    val target: String,
    val state: RestoreOperationState,
)

@Serializable
enum class RestoreOperationKind {
    MOVE,
    COPY,
    DELETE,
}

@Serializable
enum class RestoreOperationState {
    INTENT,
    DONE,
}
