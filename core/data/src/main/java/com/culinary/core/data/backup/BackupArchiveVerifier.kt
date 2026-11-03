package com.culinary.core.data.backup

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.data.util.Sha256
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 备份归档校验器（E05 §5.2）。
 *
 * 校验顺序：结构 → manifest 哈希 → 必填/版本 → checksums 哈希 → 逐文件哈希。
 * 任一必需文件失败即拒绝，不触碰现有数据；返回 `CUL-BACKUP-001/002`。
 *
 * 校验过程使用守卫式提前返回，便于在首个失败点给出精确 reason。
 */
@Suppress("ReturnCount")
class BackupArchiveVerifier {
    fun verify(input: InputStream): Outcome<BackupManifest> {
        val entries =
            try {
                readEntries(input)
            } catch (e: BackupFormatException) {
                return fail(e.message ?: "invalid archive")
            }
        return readManifest(entries)
            .flatMap { manifest -> verifyChecksums(entries, manifest) }
            .flatMap { manifest -> verifyPayloads(entries, manifest) }
    }

    private fun readManifest(entries: Map<String, ByteArray>): Outcome<BackupManifest> {
        val manifestBytes =
            entries[BackupLayout.MANIFEST_JSON]
                ?: return fail("manifest.json missing")
        val manifestSha =
            entries[BackupLayout.MANIFEST_SHA256]
                ?.decodeToString()
                ?.trim()
                ?: return fail("manifest.sha256 missing")
        if (Sha256.of(manifestBytes) != manifestSha) return fail("manifest hash mismatch")

        val manifest =
            try {
                BackupJson.decodeFromString(BackupManifest.serializer(), manifestBytes.decodeToString())
            } catch (e: IllegalArgumentException) {
                return fail("manifest parse error: ${e.message}")
            } catch (e: kotlinx.serialization.SerializationException) {
                return fail("manifest parse error: ${e.message}")
            }

        if (manifest.formatVersion > BackupManifest.FORMAT_VERSION) {
            return fail("unsupported format_version ${manifest.formatVersion}", ErrorCode.BACKUP_002)
        }
        if (manifest.integrity.status != BackupIntegrity.VERIFIED) {
            return fail("integrity status is ${manifest.integrity.status}")
        }
        return Outcome.success(manifest)
    }

    private fun verifyChecksums(
        entries: Map<String, ByteArray>,
        manifest: BackupManifest,
    ): Outcome<BackupManifest> {
        val checksumsBytes =
            entries[BackupLayout.CHECKSUMS_SHA256]
                ?: return fail("checksums.sha256 missing")
        if (Sha256.of(checksumsBytes) != manifest.integrity.checksumsSha256) {
            return fail("checksums hash mismatch")
        }

        val lines = checksumsBytes.decodeToString().lines().filter { it.isNotBlank() }
        if (lines.any { line -> FORBIDDEN_IN_CHECKSUMS.any { name -> line.contains(name) } }) {
            return fail("checksums must not list manifest files")
        }

        lines.forEach { line ->
            val separator = line.indexOf("  ")
            if (separator <= 0) return fail("malformed checksum line")
            val path = line.substring(separator + 2)
            val expected = line.substring(0, separator)
            val bytes = entries[path] ?: return fail("missing entry $path")
            if (Sha256.of(bytes) != expected) return fail("hash mismatch for $path")
        }
        return Outcome.success(manifest)
    }

    private fun verifyPayloads(
        entries: Map<String, ByteArray>,
        manifest: BackupManifest,
    ): Outcome<BackupManifest> {
        val database = manifest.database.singleOrNull() ?: return fail("database entry count")
        val databaseBytes = entries[database.path] ?: return fail("missing ${database.path}")
        if (databaseBytes.size.toLong() != database.sizeBytes) return fail("database size mismatch")
        if (Sha256.of(databaseBytes) != database.sha256) return fail("database hash mismatch")

        manifest.attachments.forEach { attachment ->
            val bytes = entries[attachment.path] ?: return fail("missing ${attachment.path}")
            if (bytes.size.toLong() != attachment.sizeBytes) return fail("attachment size mismatch")
            if (Sha256.of(bytes) != attachment.sha256) return fail("attachment hash mismatch")
        }
        return Outcome.success(manifest)
    }

    /** 读取 ZIP 条目并做 zip-bomb/路径安全检查（E05 §12）。 */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip -> readZip(zip, result) }
        return result
    }

    private fun readZip(
        zip: ZipInputStream,
        result: MutableMap<String, ByteArray>,
    ) {
        var totalBytes = 0L
        var entry = zip.nextEntry
        while (entry != null) {
            totalBytes = readOne(zip, entry, result, totalBytes)
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    private fun readOne(
        zip: ZipInputStream,
        entry: java.util.zip.ZipEntry,
        result: MutableMap<String, ByteArray>,
        totalBytes: Long,
    ): Long {
        if (entry.isDirectory) return totalBytes
        val bytes = zip.readBytes()
        val problem = validateEntry(entry.name, result.size, totalBytes, bytes.size)
        if (problem != null) throw BackupFormatException(problem)
        result[entry.name] = bytes
        return totalBytes + bytes.size
    }

    private fun validateEntry(
        name: String,
        entryCount: Int,
        totalBytes: Long,
        entrySize: Int,
    ): String? =
        when {
            entryCount >= MAX_ENTRIES -> "too many entries"
            name.startsWith("/") || name.contains("..") || name.contains('\\') -> "unsafe entry $name"
            totalBytes + entrySize > MAX_TOTAL_BYTES -> "archive too large"
            else -> null
        }

    private class BackupFormatException(
        message: String,
    ) : Exception(message)

    private fun fail(
        reason: String,
        code: ErrorCode = ErrorCode.BACKUP_001,
    ): Outcome<BackupManifest> =
        Outcome.failure(
            CulinaryError.Transfer(
                message = "backup verification failed: $reason",
                code = code,
                context = mapOf("reason" to reason),
            ),
        )

    private companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_TOTAL_BYTES = 1L * 1024 * 1024 * 1024

        val FORBIDDEN_IN_CHECKSUMS =
            listOf(
                BackupLayout.MANIFEST_JSON,
                BackupLayout.MANIFEST_SHA256,
                BackupLayout.CHECKSUMS_SHA256,
            )
    }
}
