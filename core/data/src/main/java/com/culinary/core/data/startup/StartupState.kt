package com.culinary.core.data.startup

/** 应用启动后的可用状态（R01 阶段 6）。 */
sealed interface StartupState {
    /** 数据库已就绪，可以进入主界面。 */
    data object Ready : StartupState

    /** 数据库版本高于当前 App，禁止继续（不降级、不清库）。 */
    data class Incompatible(
        val databaseVersion: Int,
        val appSchemaVersion: Int,
    ) : StartupState

    /** 数据库损坏或迁移/打开失败：进入 Recovery，不自动清理数据。 */
    data class Recovery(
        val reason: RecoveryReason,
        val detail: String? = null,
    ) : StartupState
}

enum class RecoveryReason {
    DATABASE_CORRUPT,
    MIGRATION_FAILED,
    OPEN_FAILED,
}
