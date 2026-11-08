package com.culinary.core.data.backup

import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 恢复预检：版本兼容、PARTIAL 二次确认与拒绝路径（E05 §12）。 */
class RestorePrecheckTest {
    private val precheck =
        RestorePrecheck(
            currentSchemaVersion = 5,
            testedMigrationSources = setOf(2, 3, 4),
        )

    @Test
    fun `accepts a backup with the same schema version`() {
        val result = precheck.check(manifest(schemaVersion = 5))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value.migrationRequired).isFalse()
    }

    @Test
    fun `rejects a newer backup schema`() {
        val result = precheck.check(manifest(schemaVersion = 6))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-002")
    }

    @Test
    fun `accepts an older schema only when a tested migration path exists`() {
        val result = precheck.check(manifest(schemaVersion = 2))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value.migrationRequired).isTrue()

        val v3Result = precheck.check(manifest(schemaVersion = 3))
        assertThat(v3Result).isInstanceOf(Outcome.Success::class.java)
        assertThat((v3Result as Outcome.Success).value.migrationRequired).isTrue()

        val v4Result = precheck.check(manifest(schemaVersion = 4))
        assertThat(v4Result).isInstanceOf(Outcome.Success::class.java)
        assertThat((v4Result as Outcome.Success).value.migrationRequired).isTrue()
    }

    @Test
    fun `rejects an older schema without a tested migration path`() {
        val result = precheck.check(manifest(schemaVersion = 1))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-002")
        assertThat(result.error.context["reason"]).contains("no tested migration path")
    }

    @Test
    fun `rejects a partial backup without the second confirmation`() {
        val result = precheck.check(manifest(schemaVersion = 5, status = BackupIntegrity.PARTIAL))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-001")
        assertThat(result.error.context["reason"]).contains("PARTIAL")
    }

    @Test
    fun `accepts a partial backup after explicit confirmation`() {
        val result =
            precheck.check(
                manifest = manifest(schemaVersion = 5, status = BackupIntegrity.PARTIAL),
                acceptPartial = true,
            )

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value.requiresPartialConfirmation).isTrue()
    }

    @Test
    fun `rejects an unsupported backup format version`() {
        val result = precheck.check(manifest(schemaVersion = 5, formatVersion = 2))

        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-002")
    }

    private fun manifest(
        schemaVersion: Int,
        status: String = BackupIntegrity.VERIFIED,
        formatVersion: Int = BackupManifest.FORMAT_VERSION,
    ) = BackupManifest(
        formatVersion = formatVersion,
        backupId = "backup-1",
        createdAt = "2026-11-04T00:30:00Z",
        app = BackupApp("0.1.0", 100, schemaVersion, "install-1"),
        device = BackupDevice("Google", "Pixel", 36),
        database = listOf(BackupDatabaseFile("database/culinary.db", 10, "abc")),
        totals = BackupTotals(1, 10),
        integrity = BackupIntegrity(status, checksumsSha256 = "def"),
    )
}
