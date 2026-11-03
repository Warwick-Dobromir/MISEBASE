package com.culinary.core.data.backup

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome

/** 预检通过后的信息；UI 用于展示版本、附件与迁移路径。 */
data class PrecheckResult(
    val manifest: BackupManifest,
    val requiresPartialConfirmation: Boolean,
    val migrationRequired: Boolean,
)

/**
 * 恢复前预检（E05 §12 / §12.1）。
 *
 * **预检不通过，绝不触碰现有数据。** 本类只做与 manifest 有关的兼容性判断；
 * ZIP 结构、逐文件哈希由 [BackupArchiveVerifier] 负责，数据库完整性检查在阶段 5+。
 */
class RestorePrecheck(
    private val currentSchemaVersion: Int,
    private val testedMigrationSources: Set<Int> = emptySet(),
) {
    fun check(
        manifest: BackupManifest,
        acceptPartial: Boolean = false,
    ): Outcome<PrecheckResult> {
        if (manifest.formatVersion > BackupManifest.FORMAT_VERSION) {
            return fail("unsupported format_version ${manifest.formatVersion}", ErrorCode.BACKUP_002)
        }

        val status = manifest.integrity.status
        val isPartial = status == BackupIntegrity.PARTIAL
        if (status != BackupIntegrity.VERIFIED && !isPartial) {
            return fail("integrity status is $status")
        }
        if (isPartial && !acceptPartial) {
            return fail("PARTIAL backup requires explicit second confirmation")
        }

        val backupSchema = manifest.app.schemaVersion
        return when {
            backupSchema > currentSchemaVersion ->
                fail("backup schema $backupSchema is newer than app schema $currentSchemaVersion", ErrorCode.BACKUP_002)

            backupSchema == currentSchemaVersion ->
                Outcome.success(
                    PrecheckResult(
                        manifest = manifest,
                        requiresPartialConfirmation = isPartial,
                        migrationRequired = false,
                    ),
                )

            backupSchema in testedMigrationSources ->
                Outcome.success(
                    PrecheckResult(
                        manifest = manifest,
                        requiresPartialConfirmation = isPartial,
                        migrationRequired = true,
                    ),
                )

            else ->
                fail(
                    "no tested migration path from schema $backupSchema to $currentSchemaVersion",
                    ErrorCode.BACKUP_002,
                )
        }
    }

    private fun fail(
        reason: String,
        code: ErrorCode = ErrorCode.BACKUP_001,
    ): Outcome<PrecheckResult> =
        Outcome.failure(
            CulinaryError.Transfer(
                message = "restore precheck failed: $reason",
                code = code,
                context = mapOf("reason" to reason),
            ),
        )
}
