package com.culinary.core.domain.platform

import com.culinary.core.common.Outcome
import java.io.InputStream
import java.io.OutputStream

/**
 * 备份文件的存储端口（A03A §8.1）。
 *
 * `core:platform` 用 SAF 实现；`core:data` 只依赖本接口与 Java IO 流，
 * 不直接引用 `android.net.Uri`、`Intent` 或 Activity Result。
 */
interface BackupStoragePort {
    suspend fun createDocument(
        displayName: String,
        mimeType: String,
    ): Outcome<StorageHandle>

    suspend fun pickDocument(): Outcome<StorageHandle>

    suspend fun openInput(handle: StorageHandle): Outcome<InputStream>

    suspend fun openOutput(handle: StorageHandle): Outcome<OutputStream>
}
