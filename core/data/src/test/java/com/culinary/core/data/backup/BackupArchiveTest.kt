package com.culinary.core.data.backup

import com.culinary.core.common.Clock
import com.culinary.core.common.Outcome
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 备份往返：有数据、空附件、篡改与缺失清单（E05 §5 / §18）。 */
class BackupArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val clock = Clock { Instant.parse("2026-11-04T00:30:00Z") }
    private val writer = BackupArchiveWriter(clock)
    private val verifier = BackupArchiveVerifier()

    @Test
    fun `writes and verifies a backup with database and image`() {
        val database = databaseFile(1024)
        val image = temporaryFolder.newFile("photo.webp").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val output = ByteArrayOutputStream()

        val manifest =
            writer.write(
                output = output,
                databaseFile = database,
                appVersionName = "0.1.0",
                appVersionCode = 100,
                schemaVersion = 1,
                installId = "install-1",
                device = BackupDevice("Google", "Pixel", 36),
                attachments =
                    listOf(
                        BackupAttachmentSource("images/0a/photo.webp", "image/webp", image),
                    ),
                counts = mapOf("ingredient" to 3L),
            )

        val result = verifier.verify(ByteArrayInputStream(output.toByteArray()))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val parsed = (result as Outcome.Success).value
        assertThat(parsed.backupId).isEqualTo(manifest.backupId)
        assertThat(parsed.totals.fileCount).isEqualTo(2)
        assertThat(parsed.totals.totalBytes).isEqualTo(1024L + 5L)
        assertThat(parsed.attachments).hasSize(1)
        assertThat(parsed.attachments.single().path).isEqualTo("images/0a/photo.webp")
        assertThat(parsed.integrity.status).isEqualTo(BackupIntegrity.VERIFIED)
        assertThat(parsed.createdAt).isEqualTo("2026-11-04T00:30:00Z")
    }

    @Test
    fun `supports an empty attachment set`() {
        val output = ByteArrayOutputStream()

        val manifest =
            writer.write(
                output = output,
                databaseFile = databaseFile(256),
                appVersionName = "0.1.0",
                appVersionCode = 100,
                schemaVersion = 1,
                installId = "install-1",
                device = BackupDevice("Google", "Pixel", 36),
            )

        val result = verifier.verify(ByteArrayInputStream(output.toByteArray()))

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        assertThat((result as Outcome.Success).value.totals.fileCount).isEqualTo(1)
        assertThat(manifest.attachments).isEmpty()
    }

    @Test
    fun `rejects a tampered database entry`() {
        val output = ByteArrayOutputStream()
        writer.write(
            output = output,
            databaseFile = databaseFile(128),
            appVersionName = "0.1.0",
            appVersionCode = 100,
            schemaVersion = 1,
            installId = "install-1",
            device = BackupDevice("Google", "Pixel", 36),
        )

        val tampered =
            rebuild(output.toByteArray()) { entries ->
                val database = entries.getValue(BackupLayout.DATABASE_FILE)
                database[0] = (database[0].toInt() + 1).toByte()
            }

        val result = verifier.verify(ByteArrayInputStream(tampered))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        val failure = result as Outcome.Failure
        assertThat(failure.error.code.value).isEqualTo("CUL-BACKUP-001")
    }

    @Test
    fun `rejects an archive without a manifest`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupLayout.DATABASE_FILE))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }

        val result = verifier.verify(ByteArrayInputStream(output.toByteArray()))

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat((result as Outcome.Failure).error.code.value).isEqualTo("CUL-BACKUP-001")
    }

    private fun databaseFile(size: Int) =
        temporaryFolder.newFile("culinary.db").apply {
            writeBytes(ByteArray(size) { index -> (index % 251).toByte() })
        }

    private fun rebuild(
        archive: ByteArray,
        transform: (MutableMap<String, ByteArray>) -> Unit,
    ): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        transform(entries)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
