package com.culinary.core.data.backup

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 附件恢复（E05 §7 / G3-1）。
 *
 * 只处理归档中的 `images/...` 条目：校验相对路径 → 备份同名旧文件到 rollback →
 * 临时文件 + fsync + 原子替换。任一步失败会把已安装的附件还原并返回错误。
 *
 * 调用方必须先通过归档校验（校验和、条目数、路径安全）。
 */
class AttachmentRestorer(
    private val imagesDirectory: File,
    private val rollbackDirectory: File,
) {
    fun install(entries: Map<String, ByteArray>): Outcome<Int> {
        val attachments = entries.filterKeys { it.startsWith(IMAGES_PREFIX) }
        val invalid = attachments.keys.firstOrNull { !isSafeRelativeName(it) }
        if (invalid != null) {
            return failure("unsafe attachment path: $invalid")
        }
        if (!imagesDirectory.isDirectory && !imagesDirectory.mkdirs()) {
            return failure("cannot create images directory")
        }
        if (!rollbackDirectory.isDirectory && !rollbackDirectory.mkdirs()) {
            return failure("cannot create attachment rollback directory")
        }

        val backups = mutableListOf<Pair<File, File>>()
        return try {
            attachments.forEach { (name, bytes) ->
                val target = File(imagesDirectory.parentFile, name)
                backups += backupExisting(target, name)
                writeAtomically(target, bytes)
            }
            Outcome.success(attachments.size)
        } catch (e: IOException) {
            backups.asReversed().forEach { (target, backup) ->
                runCatching { backup.copyTo(target, overwrite = true) }
            }
            failure("cannot install attachments: ${e.message}", e)
        }
    }

    private fun backupExisting(
        target: File,
        name: String,
    ): Pair<File, File> {
        if (!target.exists()) return target to File(rollbackDirectory, "missing")
        val backup = File(rollbackDirectory, name.replace('/', '_'))
        target.copyTo(backup, overwrite = true)
        return target to backup
    }

    private fun writeAtomically(
        target: File,
        bytes: ByteArray,
    ) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            temporary.copyTo(target, overwrite = true)
        } finally {
            temporary.delete()
        }
    }

    private fun isSafeRelativeName(name: String): Boolean =
        name.startsWith(IMAGES_PREFIX) &&
            !name.contains("..") &&
            !name.contains('\\') &&
            !name.startsWith("/")

    private fun failure(
        message: String,
        cause: Throwable? = null,
    ): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Transfer(
                message = message,
                code = ErrorCode.BACKUP_003,
                cause = cause,
            ),
        )

    private companion object {
        const val IMAGES_PREFIX = "images/"
    }
}
