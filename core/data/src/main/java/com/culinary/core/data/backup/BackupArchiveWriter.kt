package com.culinary.core.data.backup

import com.culinary.core.common.Clock
import com.culinary.core.common.SystemClock
import com.culinary.core.data.util.Sha256
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 待写入备份的附件（v1 仅图片）。 */
data class BackupAttachmentSource(
    val path: String,
    val mimeType: String,
    val file: File,
)

/**
 * 备份归档写入器（E05 §4.3 / §5）。
 *
 * 生成顺序严格按 E05：先算 DB/附件哈希 → 写 checksums → 算 checksums 哈希 →
 * 生成最终 manifest → 算 manifest 哈希 → 打包 ZIP。manifest 不包含自身哈希。
 */
class BackupArchiveWriter(
    private val clock: Clock = SystemClock,
) {
    fun write(
        output: OutputStream,
        databaseFile: File,
        appVersionName: String,
        appVersionCode: Int,
        schemaVersion: Int,
        installId: String,
        device: BackupDevice,
        attachments: List<BackupAttachmentSource> = emptyList(),
        counts: Map<String, Long> = emptyMap(),
        backupId: String = UUID.randomUUID().toString(),
    ): BackupManifest {
        val databaseBytes = databaseFile.readBytes()
        val payloads = attachments.map { source -> source to source.file.readBytes() }

        val databaseEntry =
            BackupDatabaseFile(
                path = BackupLayout.DATABASE_FILE,
                sizeBytes = databaseBytes.size.toLong(),
                sha256 = Sha256.of(databaseBytes),
            )
        val attachmentEntries =
            payloads.map { (source, bytes) ->
                BackupAttachmentEntry(
                    kind = "image",
                    path = source.path,
                    sizeBytes = bytes.size.toLong(),
                    mimeType = source.mimeType,
                    sha256 = Sha256.of(bytes),
                )
            }

        val checksumsBytes = buildChecksums(databaseEntry, attachmentEntries)
        val manifest =
            buildManifest(
                backupId = backupId,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                schemaVersion = schemaVersion,
                installId = installId,
                device = device,
                databaseEntry = databaseEntry,
                attachmentEntries = attachmentEntries,
                counts = counts,
                totalBytes = databaseBytes.size.toLong() + payloads.sumOf { (_, bytes) -> bytes.size.toLong() },
                checksumsSha = Sha256.of(checksumsBytes),
            )
        val manifestBytes = BackupJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray()

        writeArchive(
            output = output,
            manifestBytes = manifestBytes,
            checksumsBytes = checksumsBytes,
            databaseBytes = databaseBytes,
            payloads = payloads,
        )
        return manifest
    }

    private fun buildChecksums(
        databaseEntry: BackupDatabaseFile,
        attachmentEntries: List<BackupAttachmentEntry>,
    ): ByteArray {
        val lines =
            buildList {
                add("${databaseEntry.sha256}  ${databaseEntry.path}")
                attachmentEntries.forEach { entry -> add("${entry.sha256}  ${entry.path}") }
            }
        return lines.joinToString(separator = "\n", postfix = "\n").toByteArray()
    }

    @Suppress("LongParameterList")
    private fun buildManifest(
        backupId: String,
        appVersionName: String,
        appVersionCode: Int,
        schemaVersion: Int,
        installId: String,
        device: BackupDevice,
        databaseEntry: BackupDatabaseFile,
        attachmentEntries: List<BackupAttachmentEntry>,
        counts: Map<String, Long>,
        totalBytes: Long,
        checksumsSha: String,
    ): BackupManifest =
        BackupManifest(
            backupId = backupId,
            createdAt = clock.now().toString(),
            app =
                BackupApp(
                    versionName = appVersionName,
                    versionCode = appVersionCode,
                    schemaVersion = schemaVersion,
                    installId = installId,
                ),
            device = device,
            database = listOf(databaseEntry),
            attachments = attachmentEntries,
            counts = counts,
            totals =
                BackupTotals(
                    fileCount = 1 + attachmentEntries.size,
                    totalBytes = totalBytes,
                ),
            integrity =
                BackupIntegrity(
                    status = BackupIntegrity.VERIFIED,
                    checksumsSha256 = checksumsSha,
                ),
        )

    private fun writeArchive(
        output: OutputStream,
        manifestBytes: ByteArray,
        checksumsBytes: ByteArray,
        databaseBytes: ByteArray,
        payloads: List<Pair<BackupAttachmentSource, ByteArray>>,
    ) {
        ZipOutputStream(output).use { zip ->
            zip.putEntry(BackupLayout.MANIFEST_JSON, manifestBytes)
            zip.putEntry(BackupLayout.MANIFEST_SHA256, Sha256.of(manifestBytes).toByteArray())
            zip.putEntry(BackupLayout.CHECKSUMS_SHA256, checksumsBytes)
            zip.putEntry(BackupLayout.DATABASE_FILE, databaseBytes)
            payloads.forEach { (source, bytes) -> zip.putEntry(source.path, bytes) }
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
