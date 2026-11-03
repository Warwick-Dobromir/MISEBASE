package com.culinary.core.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份 manifest（E05 0.4.1 §4）。
 *
 * 字段名与 E05 的 JSON 键一一对应；`manifest.json` 不包含自身大小或哈希，
 * 避免自引用。`integrity.checksums_sha256` 指向 `checksums.sha256` 的最终字节。
 */
@Serializable
data class BackupManifest(
    @SerialName("format_version")
    val formatVersion: Int = FORMAT_VERSION,
    @SerialName("backup_id")
    val backupId: String,
    @SerialName("created_at")
    val createdAt: String,
    val app: BackupApp,
    val device: BackupDevice,
    val database: List<BackupDatabaseFile>,
    val attachments: List<BackupAttachmentEntry> = emptyList(),
    val counts: Map<String, Long> = emptyMap(),
    val totals: BackupTotals,
    val integrity: BackupIntegrity,
    val warnings: List<BackupWarning> = emptyList(),
    val encryption: BackupEncryption? = null,
) {
    companion object {
        const val FORMAT_VERSION: Int = 1
    }
}

@Serializable
data class BackupApp(
    @SerialName("version_name")
    val versionName: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("schema_version")
    val schemaVersion: Int,
    @SerialName("install_id")
    val installId: String,
)

@Serializable
data class BackupDevice(
    val manufacturer: String,
    val model: String,
    @SerialName("api_level")
    val apiLevel: Int,
)

@Serializable
data class BackupDatabaseFile(
    val path: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class BackupAttachmentEntry(
    val kind: String,
    val path: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("mime_type")
    val mimeType: String,
    val sha256: String,
)

@Serializable
data class BackupTotals(
    @SerialName("file_count")
    val fileCount: Int,
    @SerialName("total_bytes")
    val totalBytes: Long,
)

@Serializable
data class BackupIntegrity(
    val status: String,
    @SerialName("hash_algorithm")
    val hashAlgorithm: String = "SHA-256",
    @SerialName("checksums_sha256")
    val checksumsSha256: String,
) {
    companion object {
        const val VERIFIED: String = "VERIFIED"
        const val PARTIAL: String = "PARTIAL"
        const val FAILED: String = "FAILED"
    }
}

@Serializable
data class BackupWarning(
    val code: String,
    @SerialName("image_asset_id")
    val imageAssetId: String? = null,
    val path: String,
    val type: String,
    @SerialName("expected_mime")
    val expectedMime: String? = null,
    @SerialName("actual_mime")
    val actualMime: String? = null,
    @SerialName("expected_size")
    val expectedSize: Long? = null,
    @SerialName("actual_size")
    val actualSize: Long? = null,
    val message: String,
)

@Serializable
data class BackupEncryption(
    val algorithm: String,
    val kdf: String,
    val iterations: Int,
    @SerialName("salt_b64")
    val saltBase64: String,
    @SerialName("nonce_b64")
    val nonceBase64: String,
)
