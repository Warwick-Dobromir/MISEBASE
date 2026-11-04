package com.culinary.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.common.ErrorCode
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinaryColorScheme
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
        modifier = modifier,
    )
}

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xxl),
    ) {
        Text(
            text = stringResource(R.string.settings_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
        )
        BackupSection(state = state, onBackupClick = onBackupClick)
        RestoreSection(
            state = state,
            onRestoreClick = onRestoreClick,
            onCancelRestore = onCancelRestore,
        )
        DiagnosticsSection(state = state, onExportDiagnostics = onExportDiagnostics)
        AboutSection(state = state, onShowLicenses = onShowLicenses)
        Text(
            text = stringResource(R.string.settings_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg),
        )
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
}

@Composable
private fun BackupSection(
    state: SettingsUiState,
    onBackupClick: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.settings_section_backup),
        modifier = Modifier.padding(horizontal = CulinarySpacing.md),
    )
    SettingCard {
        Text(
            text = stringResource(R.string.settings_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Button(
            onClick = onBackupClick,
            enabled = state.backup !is BackupState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_backup_action))
        }
        when (val backup = state.backup) {
            BackupState.Idle -> Unit
            BackupState.Running -> RunningRow(text = stringResource(R.string.settings_backup_running))
            is BackupState.Done ->
                ResultText(
                    text =
                        stringResource(
                            R.string.settings_backup_success,
                            backup.archiveName,
                            formatBytes(backup.sizeBytes),
                        ),
                    color = CulinaryColorScheme.current.success,
                )

            is BackupState.Failed ->
                ResultText(text = failureText(backup.code), color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = stringResource(R.string.settings_backup_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun RestoreSection(
    state: SettingsUiState,
    onRestoreClick: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.settings_section_restore),
        modifier = Modifier.padding(horizontal = CulinarySpacing.md),
    )
    SettingCard {
        Text(
            text = stringResource(R.string.settings_restore_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        OutlinedButton(
            onClick = onRestoreClick,
            enabled = state.restore !is RestoreState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_restore_action))
        }
        when (val restore = state.restore) {
            RestoreState.Idle -> Unit
            RestoreState.Running -> RunningRow(text = stringResource(R.string.settings_restore_running))
            is RestoreState.Done ->
                ResultText(
                    text =
                        stringResource(
                            R.string.settings_restore_success_inline,
                            restore.backupVersionName,
                            restore.backupSchemaVersion,
                        ),
                    color = CulinaryColorScheme.current.success,
                )

            is RestoreState.Failed -> {
                ResultText(text = failureText(restore.code), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onCancelRestore) {
                    Text(text = stringResource(R.string.settings_restore_clear))
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
}

@Composable
private fun DiagnosticsSection(
    state: SettingsUiState,
    onExportDiagnostics: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.settings_section_diagnostics),
        modifier = Modifier.padding(horizontal = CulinarySpacing.md),
    )
    SettingCard {
        DiagnosticRow(
            label = stringResource(R.string.settings_diag_app_version),
            value = "${state.appVersionName}（${state.appVersionCode}）",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DiagnosticRow(
            label = stringResource(R.string.settings_diag_db_version),
            value = stringResource(R.string.settings_diag_db_version_value, state.schemaVersion),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DiagnosticRow(
            label = stringResource(R.string.settings_diag_db_size),
            value = formatBytes(state.databaseSizeBytes),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DiagnosticRow(
            label = stringResource(R.string.settings_diag_device),
            value = state.deviceLabel,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        OutlinedButton(
            onClick = onExportDiagnostics,
            enabled = state.diagnosticsExport !is DiagnosticsExportState.Running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.settings_diag_export))
        }
        when (val export = state.diagnosticsExport) {
            DiagnosticsExportState.Idle -> Unit
            DiagnosticsExportState.Running -> RunningRow(text = stringResource(R.string.settings_diag_export_running))
            is DiagnosticsExportState.Done ->
                ResultText(
                    text = stringResource(R.string.settings_diag_export_success, export.fileName),
                    color = CulinaryColorScheme.current.success,
                )

            is DiagnosticsExportState.Failed ->
                ResultText(text = failureText(export.code), color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = stringResource(R.string.settings_diag_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
}

@Composable
private fun AboutSection(
    state: SettingsUiState,
    onShowLicenses: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.settings_section_about),
        modifier = Modifier.padding(horizontal = CulinarySpacing.md),
    )
    SettingCard {
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
        HorizontalDivider(
            modifier = Modifier.padding(vertical = CulinarySpacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
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
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = CulinaryIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestartDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(text = stringResource(R.string.settings_restart_title)) },
        text = { Text(text = stringResource(R.string.settings_restart_body)) },
        confirmButton = {
            Button(onClick = onRestart) {
                Text(text = stringResource(R.string.settings_restart_action))
            }
        },
    )
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_license_title)) },
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
                Text(text = stringResource(R.string.settings_close))
            }
        },
    )
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CulinarySpacing.md),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(CulinarySpacing.md), content = content)
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RunningRow(text: String) {
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
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
