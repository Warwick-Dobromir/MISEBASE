package com.culinary.settings

import com.culinary.core.data.kitchen.KitchenSeedStatus

/**
 * 设置页 UI 状态。
 *
 * 业务结果只携带结构化数据（名称、大小、版本号、错误码），
 * 文案由 Composable 用 `stringResource` 组装，保证本地化与可测试性。
 */
data class SettingsUiState(
    val appVersionName: String = "",
    val appVersionCode: Int = 0,
    val schemaVersion: Int = 0,
    val databaseSizeBytes: Long = 0L,
    val deviceLabel: String = "",
    val backup: BackupState = BackupState.Idle,
    val restore: RestoreState = RestoreState.Idle,
    val diagnosticsExport: DiagnosticsExportState = DiagnosticsExportState.Idle,
    val selectedRestoreFileName: String? = null,
    val showRestoreConfirm: Boolean = false,
    val restartRequired: Boolean = false,
    val showLicenses: Boolean = false,
    val seedStatus: KitchenSeedStatus? = null,
    val seedAction: SeedActionState = SeedActionState.Idle,
)

sealed interface SeedActionState {
    data object Idle : SeedActionState

    data object Running : SeedActionState

    data class Done(
        val imported: Boolean,
    ) : SeedActionState

    data object Failed : SeedActionState
}

sealed interface BackupState {
    data object Idle : BackupState

    data object Running : BackupState

    data class Done(
        val archiveName: String,
        val sizeBytes: Long,
    ) : BackupState

    data class Failed(
        val code: String,
    ) : BackupState
}

sealed interface RestoreState {
    data object Idle : RestoreState

    data object Running : RestoreState

    data class Done(
        val backupVersionName: String,
        val backupSchemaVersion: Int,
    ) : RestoreState

    data class Failed(
        val code: String,
    ) : RestoreState
}

sealed interface DiagnosticsExportState {
    data object Idle : DiagnosticsExportState

    data object Running : DiagnosticsExportState

    data class Done(
        val fileName: String,
    ) : DiagnosticsExportState

    data class Failed(
        val code: String,
    ) : DiagnosticsExportState
}
