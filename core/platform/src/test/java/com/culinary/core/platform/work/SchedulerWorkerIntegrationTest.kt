package com.culinary.core.platform.work

import android.database.sqlite.SQLiteDatabase
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.culinary.core.common.Outcome
import com.culinary.core.domain.platform.AutoBackupRequest
import com.culinary.core.domain.platform.AutoBackupRunResult
import com.culinary.core.domain.platform.AutoBackupRunner
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Scheduler → Worker 集成：入队后 Worker 真实执行，产出结构可校验的 E05 归档并回传路径。
 *
 * 生产归档校验器在 `core:data` 单测中覆盖；本测试只验证调度链路的真实执行与产物结构。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchedulerWorkerIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @After
    fun tearDown() {
        AutoBackupWorkerDependencies.installForTest(null)
    }

    @Test
    fun `enqueued worker produces a verifiable archive and reports success`() {
        val target = temporaryFolder.newFolder("backups")
        AutoBackupWorkerDependencies.installForTest(ArchivingRunner(target))
        val scheduler = WorkManagerBackupScheduler(context)

        val enqueued = scheduler.enqueueImmediateBackup(backupId = "integration-1", targetDirectory = target.path)
        assertThat(enqueued).isInstanceOf(Outcome.Success::class.java)
        val workId = UUID.fromString((enqueued as Outcome.Success).value)

        val info = awaitFinished(workId)

        assertThat(info.state).isEqualTo(WorkInfo.State.SUCCEEDED)
        assertThat(info.outputData.getString(AutoBackupWorker.KEY_BACKUP_ID)).isEqualTo("integration-1")
        assertThat(info.outputData.getString(AutoBackupWorker.KEY_RECORD_ID)).isEqualTo("record-integration-1")
        val archive = File(requireNotNull(info.outputData.getString(AutoBackupWorker.KEY_ARCHIVE_PATH)))
        assertThat(archive.exists()).isTrue()
        verifyArchive(archive, "integration-1")
    }

    private fun awaitFinished(workId: UUID): WorkInfo {
        repeat(100) {
            val info = WorkManager.getInstance(context).getWorkInfoById(workId).get()
            if (info != null && info.state.isFinished) return info
            Thread.sleep(20)
        }
        error("work $workId did not finish")
    }

    private fun verifyArchive(
        archive: File,
        expectedBackupId: String,
    ) {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val databaseBytes = requireNotNull(entries["database/culinary.db"])
        val checksums = requireNotNull(entries["checksums.sha256"]).decodeToString()
        val manifestBytes = requireNotNull(entries["manifest.json"])
        assertThat(entries["manifest.sha256"]?.decodeToString()).isEqualTo(sha256(manifestBytes))
        assertThat(checksums).isEqualTo("${sha256(databaseBytes)}  database/culinary.db\n")
        val manifest = JSONObject(manifestBytes.decodeToString())
        assertThat(manifest.getString("backup_id")).isEqualTo(expectedBackupId)
        assertThat(manifest.getJSONObject("integrity").getString("status")).isEqualTo("VERIFIED")
        assertThat(manifest.getJSONObject("integrity").getString("checksums_sha256"))
            .isEqualTo(sha256(requireNotNull(entries["checksums.sha256"])))
    }

    private class ArchivingRunner(
        private val directory: File,
    ) : AutoBackupRunner {
        override suspend fun run(request: AutoBackupRequest): AutoBackupRunResult {
            val backupId = request.backupId ?: "generated"
            val archive = File(directory, "archive-$backupId.zip")
            writeArchive(archive, backupId)
            return AutoBackupRunResult.Success(backupId, archive.absolutePath, "record-$backupId")
        }

        private fun writeArchive(
            archive: File,
            backupId: String,
        ) {
            val databaseFile = File(directory, "source.db")
            if (!databaseFile.exists()) {
                SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
                    database.execSQL("CREATE TABLE probe (id INTEGER PRIMARY KEY)")
                }
            }
            val databaseBytes = databaseFile.readBytes()
            val databaseHash = sha256(databaseBytes)
            val databaseSize = databaseBytes.size
            val checksums = "$databaseHash  database/culinary.db\n".toByteArray()
            val checksumsHash = sha256(checksums)
            val manifest =
                """
                {
                  "format_version": 1,
                  "backup_id": "$backupId",
                  "created_at": "2026-11-04T00:00:00Z",
                  "app": {
                    "version_name": "0.1.0",
                    "version_code": 100,
                    "schema_version": 2,
                    "install_id": "install-1"
                  },
                  "device": {"manufacturer": "Google", "model": "Pixel", "api_level": 34},
                  "database": [
                    {"path": "database/culinary.db", "size_bytes": $databaseSize, "sha256": "$databaseHash"}
                  ],
                  "attachments": [],
                  "counts": {},
                  "totals": {"file_count": 1, "total_bytes": $databaseSize},
                  "integrity": {
                    "status": "VERIFIED",
                    "hash_algorithm": "SHA-256",
                    "checksums_sha256": "$checksumsHash"
                  }
                }
                """.trimIndent().toByteArray()
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putEntry("manifest.json", manifest)
                zip.putEntry("manifest.sha256", sha256(manifest).toByteArray())
                zip.putEntry("checksums.sha256", checksums)
                zip.putEntry("database/culinary.db", databaseBytes)
            }
        }

        private fun ZipOutputStream.putEntry(
            name: String,
            bytes: ByteArray,
        ) {
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }
    }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
