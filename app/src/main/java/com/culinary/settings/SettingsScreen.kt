package com.culinary.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.common.ErrorCode
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryInfoRow
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.ResultPanel
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** 备份/恢复归档的 MIME 类型；部分文件管理器只认 octet-stream。 */
private const val ZIP_MIME = "application/zip"

private const val TEXT_MIME = "text/plain"

private val RESTORE_MIME_TYPES = arrayOf(ZIP_MIME, "application/octet-stream")

/** N3 第 5 项“设置”：备份、恢复、诊断与关于。 */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME)) { uri ->
            viewModel.startBackup(uri)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            viewModel.onRestoreFileSelected(uri)
        }
    val diagnosticsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TEXT_MIME)) { uri ->
            viewModel.exportDiagnostics(uri)
        }

    SettingsScreen(
        state = state,
        onBackupClick = { backupLauncher.launch("MISEBASE-backup-${fileStamp()}.zip") },
        onRestoreClick = { restoreLauncher.launch(RESTORE_MIME_TYPES) },
        onConfirmRestore = viewModel::confirmRestore,
        onCancelRestore = viewModel::cancelRestore,
        onRestart = { restartApplication(context) },
        onExportDiagnostics = { diagnosticsLauncher.launch("MISEBASE-diagnostics-${fileStamp()}.txt") },
        onShowLicenses = viewModel::showLicenses,
        onDismissLicenses = viewModel::dismissLicenses,
        onReimportSeed = viewModel::reimportSeed,
        onClearSeed = viewModel::clearSeed,
        modifier = modifier,
    )
}

@Suppress("LongMethod") // 设置页由多个独立分节组成，统一编排入口状态
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onRestart: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onShowLicenses: () -> Unit,
    onDismissLicenses: () -> Unit,
    onReimportSeed: () -> Unit,
    onClearSeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSeedClearConfirm by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        PageHeader(
            eyebrow = "PREFERENCES",
            title = stringResource(R.string.destination_settings),
            supporting = stringResource(R.string.settings_intro),
        )
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            BackupSection(state = state, onBackupClick = onBackupClick)
            RestoreSection(
                state = state,
                onRestoreClick = onRestoreClick,
                onCancelRestore = onCancelRestore,
            )
            SeedSection(
                state = state,
                onReimport = onReimportSeed,
                onClear = { showSeedClearConfirm = true },
            )
            DiagnosticsSection(state = state, onExportDiagnostics = onExportDiagnostics)
            AboutSection(state = state, onShowLicenses = onShowLicenses)
            Spacer(modifier = Modifier.height(CulinarySpacing.block))
            CulinaryRule()
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            Text(
                text = stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (state.showRestoreConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_restore_confirm_title),
            message =
                stringResource(
                    R.string.settings_restore_confirm_body,
                    state.selectedRestoreFileName ?: stringResource(R.string.settings_restore_unknown_file),
                ),
            confirmLabel = stringResource(R.string.settings_restore_confirm_action),
            dismissLabel = stringResource(R.string.settings_cancel),
            destructive = true,
            onConfirm = onConfirmRestore,
            onDismiss = onCancelRestore,
        )
    }

    if (state.restartRequired) {
        RestartDialog(onRestart = onRestart)
    }

    if (state.showLicenses) {
        LicenseDialog(onDismiss = onDismissLicenses)
    }

    if (showSeedClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_seed_clear_title),
            message = stringResource(R.string.settings_seed_clear_body),
            confirmLabel = stringResource(R.string.settings_seed_clear_confirm),
            dismissLabel = stringResource(R.string.settings_cancel),
            destructive = true,
            onConfirm = {
                showSeedClearConfirm = false
                onClearSeed()
            },
            onDismiss = { showSeedClearConfirm = false },
        )
    }
}

@Composable
private fun BackupSection(
    state: SettingsUiState,
    onBackupClick: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_section_backup))
    Text(
        text = stringResource(R.string.settings_backup_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    CulinaryButton(
        text = stringResource(R.string.settings_backup_action),
        onClick = onBackupClick,
        enabled = state.backup !is BackupState.Running,
        loading = state.backup is BackupState.Running,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val backup = state.backup) {
        BackupState.Idle -> Unit
        BackupState.Running -> RunningRow(text = stringResource(R.string.settings_backup_running))
        is BackupState.Done ->
            ResultPanel(
                title = stringResource(R.string.settings_section_backup),
                message =
                    stringResource(
                        R.string.settings_backup_success,
                        backup.archiveName,
                        formatBytes(backup.sizeBytes),
                    ),
                tone = StatusTone.Success,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )

        is BackupState.Failed ->
            ResultPanel(
                title = stringResource(R.string.settings_section_backup),
                message = failureText(backup.code),
                tone = StatusTone.Danger,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
    }
    Text(
        text = stringResource(R.string.settings_backup_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun RestoreSection(
    state: SettingsUiState,
    onRestoreClick: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_section_restore))
    Text(
        text = stringResource(R.string.settings_restore_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    CulinaryButton(
        text = stringResource(R.string.settings_restore_action),
        onClick = onRestoreClick,
        variant = CulinaryButtonVariant.Secondary,
        enabled = state.restore !is RestoreState.Running,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val restore = state.restore) {
        RestoreState.Idle -> Unit
        RestoreState.Running -> RunningRow(text = stringResource(R.string.settings_restore_running))
        is RestoreState.Done ->
            ResultPanel(
                title = stringResource(R.string.settings_section_restore),
                message =
                    stringResource(
                        R.string.settings_restore_success_inline,
                        restore.backupVersionName,
                        restore.backupSchemaVersion,
                    ),
                tone = StatusTone.Success,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )

        is RestoreState.Failed -> {
            ResultPanel(
                title = stringResource(R.string.settings_section_restore),
                message = failureText(restore.code),
                tone = StatusTone.Danger,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
            TextButton(onClick = onCancelRestore) {
                Text(
                    text = stringResource(R.string.settings_restore_clear),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.settings_restore_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Suppress("LongMethod") // 种子状态、两个操作与结果反馈保持在同一分节
@Composable
private fun SeedSection(
    state: SettingsUiState,
    onReimport: () -> Unit,
    onClear: () -> Unit,
) {
    val status = state.seedStatus
    val running = state.seedAction is SeedActionState.Running
    SectionHeader(title = stringResource(R.string.settings_section_seed))
    Text(
        text = stringResource(R.string.settings_seed_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    Text(
        text =
            when {
                status?.isImported == true ->
                    stringResource(
                        R.string.settings_seed_counts,
                        status.version.orEmpty(),
                        status.counts.ingredients,
                        status.counts.sauces,
                        status.counts.recipes,
                        status.counts.references,
                    )

                status?.disabled == true -> stringResource(R.string.settings_seed_cleared)
                else -> stringResource(R.string.settings_seed_none)
            },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    Row(modifier = Modifier.fillMaxWidth()) {
        CulinaryButton(
            text = stringResource(R.string.settings_seed_reimport),
            onClick = onReimport,
            variant = CulinaryButtonVariant.Secondary,
            enabled = !running,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        CulinaryButton(
            text = stringResource(R.string.settings_seed_clear),
            onClick = onClear,
            variant = CulinaryButtonVariant.Danger,
            enabled = !running && status?.isImported == true,
            modifier = Modifier.weight(1f),
        )
    }
    when (val action = state.seedAction) {
        SeedActionState.Idle -> Unit
        SeedActionState.Running -> RunningRow(text = stringResource(R.string.settings_seed_running))
        is SeedActionState.Done ->
            ResultPanel(
                title = stringResource(R.string.settings_section_seed),
                message =
                    stringResource(
                        if (action.imported) R.string.settings_seed_done_import else R.string.settings_seed_done_clear,
                    ),
                tone = StatusTone.Success,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )

        SeedActionState.Failed ->
            ResultPanel(
                title = stringResource(R.string.settings_section_seed),
                message = stringResource(R.string.settings_seed_failed),
                tone = StatusTone.Danger,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
    }
    Text(
        text = stringResource(R.string.settings_seed_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun DiagnosticsSection(
    state: SettingsUiState,
    onExportDiagnostics: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_section_diagnostics))
    CulinaryInfoRow(
        label = stringResource(R.string.settings_diag_app_version),
        value = "${state.appVersionName}（${state.appVersionCode}）",
    )
    CulinaryRule()
    CulinaryInfoRow(
        label = stringResource(R.string.settings_diag_db_version),
        value = stringResource(R.string.settings_diag_db_version_value, state.schemaVersion),
    )
    CulinaryRule()
    CulinaryInfoRow(
        label = stringResource(R.string.settings_diag_db_size),
        value = formatBytes(state.databaseSizeBytes),
    )
    CulinaryRule()
    CulinaryInfoRow(
        label = stringResource(R.string.settings_diag_device),
        value = state.deviceLabel,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    CulinaryButton(
        text = stringResource(R.string.settings_diag_export),
        onClick = onExportDiagnostics,
        variant = CulinaryButtonVariant.Secondary,
        enabled = state.diagnosticsExport !is DiagnosticsExportState.Running,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val export = state.diagnosticsExport) {
        DiagnosticsExportState.Idle -> Unit
        DiagnosticsExportState.Running -> RunningRow(text = stringResource(R.string.settings_diag_export_running))
        is DiagnosticsExportState.Done ->
            ResultPanel(
                title = stringResource(R.string.settings_section_diagnostics),
                message = stringResource(R.string.settings_diag_export_success, export.fileName),
                tone = StatusTone.Success,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )

        is DiagnosticsExportState.Failed ->
            ResultPanel(
                title = stringResource(R.string.settings_section_diagnostics),
                message = failureText(export.code),
                tone = StatusTone.Danger,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )
    }
    Text(
        text = stringResource(R.string.settings_diag_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun AboutSection(
    state: SettingsUiState,
    onShowLicenses: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_section_about))
    Text(
        text = stringResource(R.string.settings_about_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    Text(
        text = stringResource(R.string.settings_about_version, state.appVersionName, state.appVersionCode),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    CulinaryRule()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowLicenses)
                .padding(vertical = CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = CulinaryIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    CulinaryRule()
}

@Composable
private fun RestartDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.settings_restart_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.settings_restart_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            CulinaryButton(
                text = stringResource(R.string.settings_restart_action),
                onClick = onRestart,
            )
        },
    )
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.settings_license_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.settings_license_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_close),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun RunningRow(text: String) {
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.size(CulinarySpacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun failureText(code: String): String =
    when (code) {
        ErrorCode.BACKUP_002.value -> stringResource(R.string.settings_error_incompatible)
        ErrorCode.BACKUP_001.value -> stringResource(R.string.settings_error_verify)
        ErrorCode.BACKUP_003.value -> stringResource(R.string.settings_error_io)
        ErrorCode.BACKUP_004.value -> stringResource(R.string.settings_error_journal)
        ErrorCode.FILE_001.value -> stringResource(R.string.settings_error_file)
        else -> stringResource(R.string.settings_error_unknown, code)
    }

private fun fileStamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

/**
 * 恢复成功后数据库连接已关闭，必须重启进程重新打开。
 *
 * 先提交新任务启动请求（CLEAR_TASK），随后立即结束旧进程：启动请求已交给
 * system_server，旧进程退出不会撤销它，系统会在新进程中冷启动 MainActivity。
 * 若延迟退出，新 Activity 已经跑在旧进程里，反而会被一起杀掉。
 */
private fun restartApplication(context: Context) {
    val launchIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(0)
}
