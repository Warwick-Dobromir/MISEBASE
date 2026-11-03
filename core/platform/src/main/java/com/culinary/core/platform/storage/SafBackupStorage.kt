package com.culinary.core.platform.storage

import android.content.Context
import android.net.Uri
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.platform.BackupStoragePort
import com.culinary.core.domain.platform.StorageHandle
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * SAF 实现（A03A §8.1 / D-011）。
 *
 * - 领域层只看到不透明 [StorageHandle]；`Uri` 不越过本模块；
 * - 用户取消选择返回可预期的失败，不抛异常；
 * - 只负责流与句柄，不解析备份格式（格式校验在 `core:data`）。
 */
class SafBackupStorage(
    private val context: Context,
    private val launcher: DocumentLauncher,
) : BackupStoragePort {
    override suspend fun createDocument(
        displayName: String,
        mimeType: String,
    ): Outcome<StorageHandle> {
        val uri = launcher.createDocument(displayName, mimeType) ?: return cancelled()
        return Outcome.success(StorageHandle(uri.toString()))
    }

    override suspend fun pickDocument(): Outcome<StorageHandle> {
        val uri = launcher.pickDocument(PICK_MIME_TYPES) ?: return cancelled()
        return Outcome.success(StorageHandle(uri.toString()))
    }

    override suspend fun openInput(handle: StorageHandle): Outcome<InputStream> =
        try {
            val uri = Uri.parse(handle.value)
            val stream =
                context.contentResolver.openInputStream(uri)
                    ?: return cannotOpen("cannot open input stream")
            Outcome.success(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            cannotOpen("cannot open input stream: ${e.message}", e)
        } catch (e: SecurityException) {
            cannotOpen("no permission to read the document", e)
        }

    override suspend fun openOutput(handle: StorageHandle): Outcome<OutputStream> =
        try {
            val uri = Uri.parse(handle.value)
            val stream =
                context.contentResolver.openOutputStream(uri, "wt")
                    ?: return cannotOpen("cannot open output stream")
            Outcome.success(stream)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            cannotOpen("cannot open output stream: ${e.message}", e)
        } catch (e: SecurityException) {
            cannotOpen("no permission to write the document", e)
        }

    private fun cancelled(): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Platform(
                message = "document picker cancelled",
                code = ErrorCode.FILE_001,
                retryable = false,
            ),
        )

    private fun cannotOpen(
        message: String,
        cause: Throwable? = null,
    ): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Platform(
                message = message,
                code = ErrorCode.FILE_001,
                retryable = true,
                cause = cause,
            ),
        )

    private companion object {
        val PICK_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")
    }
}
