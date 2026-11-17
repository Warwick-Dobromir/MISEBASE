package com.culinary.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.BuildConfig
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.data.kitchen.KitchenSeedImporter
import com.culinary.core.data.settings.SettingsMaintenance
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 设置页 ViewModel：备份、恢复、诊断与关于。
 *
 * 不在 ViewModel 内拼装用户文案；只产生结构化状态，由 Composable 负责本地化。
 * 备份/恢复的真实工作全部委托给 [SettingsMaintenance]（既有数据层能力）。
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val maintenance: SettingsMaintenance,
        private val seedImporter: KitchenSeedImporter,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                SettingsUiState(
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    deviceLabel = deviceLabel(),
                ),
            )
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        private var pendingRestoreUri: Uri? = null

        init {
            refreshDiagnostics()
        }

        /** 读取数据库 schema 版本与文件大小；不修改任何数据。 */
        fun refreshDiagnostics() {
            viewModelScope.launch {
                val schema = withContext(Dispatchers.IO) { maintenance.currentSchemaVersion() }
                val size = withContext(Dispatchers.IO) { maintenance.databaseSizeBytes() }
                val seed = withContext(Dispatchers.IO) { seedImporter.status() }
                _uiState.update { it.copy(schemaVersion = schema, databaseSizeBytes = size, seedStatus = seed) }
            }
        }

        /** 重新写入厨房测试种子；已存在种子先清理，不会重复。 */
        fun reimportSeed() {
            viewModelScope.launch {
                _uiState.update { it.copy(seedAction = SeedActionState.Running) }
                val result = withContext(Dispatchers.IO) { seedImporter.reimport() }
                val seed = withContext(Dispatchers.IO) { seedImporter.status() }
                _uiState.update {
                    it.copy(
                        seedStatus = seed,
                        seedAction =
                            if (result is Outcome.Success) {
                                SeedActionState.Done(imported = result.value.imported)
                            } else {
                                SeedActionState.Failed
                            },
                    )
                }
            }
        }

        /** 清理测试种子；保留正式档案，并阻止下次启动自动回填。 */
        fun clearSeed() {
            viewModelScope.launch {
                _uiState.update { it.copy(seedAction = SeedActionState.Running) }
                val result = withContext(Dispatchers.IO) { seedImporter.clear() }
                val seed = withContext(Dispatchers.IO) { seedImporter.status() }
                _uiState.update {
                    it.copy(
                        seedStatus = seed,
                        seedAction =
                            if (result is Outcome.Success) {
                                SeedActionState.Done(imported = false)
                            } else {
                                SeedActionState.Failed
                            },
                    )
                }
            }
        }

        /** SAF 选择保存位置后执行备份；[uri] 为空表示用户取消。 */
        fun startBackup(uri: Uri?) {
            if (uri == null) return
            viewModelScope.launch {
                _uiState.update { it.copy(backup = BackupState.Running) }
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                                maintenance.exportBackup(output)
                            }
                        }.getOrNull()
                    }
                _uiState.update {
                    it.copy(
                        backup =
                            when (result) {
                                null -> BackupState.Failed(ErrorCode.FILE_001.value)
                                is Outcome.Success ->
                                    BackupState.Done(
                                        archiveName = displayName(uri),
                                        sizeBytes = result.value.sizeBytes,
                                    )

                                is Outcome.Failure -> BackupState.Failed(result.error.code.value)
                            },
                    )
                }
            }
        }

        /** SAF 选择归档后进入高危确认，不立即恢复。 */
        fun onRestoreFileSelected(uri: Uri?) {
            if (uri == null) return
            pendingRestoreUri = uri
            _uiState.update {
                it.copy(
                    selectedRestoreFileName = displayName(uri),
                    showRestoreConfirm = true,
                    restore = RestoreState.Idle,
                )
            }
        }

        /** 用户在确认弹窗点“覆盖并恢复”。 */
        fun confirmRestore() {
            val uri = pendingRestoreUri ?: return
            _uiState.update { it.copy(showRestoreConfirm = false, restore = RestoreState.Running) }
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                maintenance.restoreBackup(input)
                            }
                        }.getOrNull()
                    }
                _uiState.update {
                    when (result) {
                        null ->
                            it.copy(
                                restore = RestoreState.Failed(ErrorCode.FILE_001.value),
                                selectedRestoreFileName = null,
                            )

                        is Outcome.Success -> {
                            pendingRestoreUri = null
                            it.copy(
                                restore =
                                    RestoreState.Done(
                                        backupVersionName = result.value.backupAppVersionName,
                                        backupSchemaVersion = result.value.backupSchemaVersion,
                                    ),
                                selectedRestoreFileName = null,
                                restartRequired = true,
                            )
                        }

                        is Outcome.Failure ->
                            it.copy(
                                restore = RestoreState.Failed(result.error.code.value),
                                selectedRestoreFileName = null,
                            )
                    }
                }
            }
        }

        /** 关闭确认弹窗并丢弃本次选择（不触碰数据）。 */
        fun cancelRestore() {
            pendingRestoreUri = null
            _uiState.update {
                it.copy(showRestoreConfirm = false, selectedRestoreFileName = null)
            }
        }

        /** SAF 选择位置后导出诊断文本（不含业务内容）。 */
        fun exportDiagnostics(uri: Uri?) {
            if (uri == null) return
            viewModelScope.launch {
                _uiState.update { it.copy(diagnosticsExport = DiagnosticsExportState.Running) }
                val fileName = displayName(uri)
                val text = diagnosticsText()
                val success =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                                output.write(text.toByteArray(Charsets.UTF_8))
                                true
                            } ?: false
                        }.getOrDefault(false)
                    }
                _uiState.update {
                    it.copy(
                        diagnosticsExport =
                            if (success) {
                                DiagnosticsExportState.Done(fileName)
                            } else {
                                DiagnosticsExportState.Failed(ErrorCode.FILE_001.value)
                            },
                    )
                }
            }
        }

        fun showLicenses() {
            _uiState.update { it.copy(showLicenses = true) }
        }

        fun dismissLicenses() {
            _uiState.update { it.copy(showLicenses = false) }
        }

        private fun displayName(uri: Uri): String {
            val fromProvider =
                runCatching {
                    val cursor =
                        context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )
                    cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull()
            return fromProvider?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "selected-file"
        }

        private fun deviceLabel(): String =
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"

        private fun diagnosticsText(): String {
            val state = _uiState.value
            return buildString {
                appendLine("MISEBASE 诊断信息")
                appendLine("生成时间: ${java.time.LocalDateTime.now()}")
                appendLine("应用版本: ${state.appVersionName} (${state.appVersionCode})")
                appendLine("构建类型: ${if (BuildConfig.DEBUG) "debug" else "release"}")
                appendLine("数据库 schema: v${state.schemaVersion}")
                appendLine("数据库大小: ${formatBytes(state.databaseSizeBytes)}")
                appendLine("厨房测试种子: ${state.seedStatus?.version ?: "未导入"}")
                appendLine("设备: ${state.deviceLabel}")
                appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")
                appendLine("离线: 无 INTERNET 权限")
            }
        }
    }
