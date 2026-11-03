package com.culinary.core.data.file

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.data.util.Sha256
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** 已写入的图片：相对路径 + 内容哈希 + 字节数。 */
data class StoredImage(
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * 图片文件存储端口（D-015）。
 *
 * 数据库只存 [StoredImage.relativePath] 与哈希；文件本体在应用私有目录。
 * 文件缺失时读取返回 `null`，由上层按"无图"降级，不阻塞档案读取。
 */
interface ImageFileStorage {
    fun resolve(relativePath: String): File?

    fun exists(relativePath: String): Boolean

    fun readOrNull(relativePath: String): ByteArray?

    suspend fun store(
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<StoredImage>

    suspend fun delete(relativePath: String): Outcome<Unit>
}

/**
 * 私有目录实现：`<root>/images/<xx>/<uuid>.<ext>`。
 *
 * 写入先落到同目录的 `.part` 临时文件并 `fsync`，再 rename 到最终路径；
 * 失败时删除临时文件，不留下半张图（E03 §8.2）。
 */
class LocalImageStorage(
    root: File,
) : ImageFileStorage {
    private val rootDir: File = root.canonicalFile

    /**
     * 路径解析。越界或无法规范化时返回 `null`，按"无图"降级（D-015）；
     * 这里故意不把 IO 异常抛给读取路径。
     */
    override fun resolve(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        if (relativePath.startsWith("/") || relativePath.contains("..")) return null
        val canonical = runCatching { File(rootDir, relativePath).canonicalFile }.getOrNull() ?: return null
        val prefix = rootDir.path + File.separator
        return if (canonical.path.startsWith(prefix)) canonical else null
    }

    override fun exists(relativePath: String): Boolean = resolve(relativePath)?.isFile == true

    /** 文件缺失/不可读时返回 `null`，由上层按无图显示（D-015）。 */
    override fun readOrNull(relativePath: String): ByteArray? {
        val file = resolve(relativePath)?.takeIf { it.isFile } ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    override suspend fun store(
        bytes: ByteArray,
        mimeType: String,
    ): Outcome<StoredImage> {
        val id = UUID.randomUUID().toString()
        val relativePath = "images/${id.take(2)}/$id.${extensionFor(mimeType)}"
        val target =
            resolve(relativePath)
                ?: return failure("invalid image path", retryable = false)

        return try {
            val directory = target.parentFile
            if (directory == null || (!directory.isDirectory && !directory.mkdirs())) {
                return failure("cannot create image directory", retryable = false)
            }
            val temporary = File(directory, ".${target.name}.part")
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                return failure("cannot commit image file", retryable = true)
            }
            Outcome.success(
                StoredImage(
                    relativePath = relativePath,
                    sha256 = Sha256.of(bytes),
                    sizeBytes = bytes.size.toLong(),
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Outcome.failure(
                CulinaryError.Data(
                    message = "image write failed",
                    code = ErrorCode.FILE_001,
                    retryable = true,
                    cause = e,
                ),
            )
        }
    }

    override suspend fun delete(relativePath: String): Outcome<Unit> {
        val file =
            resolve(relativePath)
                ?: return failure("invalid image path", retryable = false)
        if (!file.exists()) return Outcome.success(Unit)
        return if (file.delete()) {
            Outcome.success(Unit)
        } else {
            failure("cannot delete image file", retryable = true)
        }
    }

    private fun extensionFor(mimeType: String): String =
        when (mimeType) {
            "image/webp" -> "webp"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> "bin"
        }

    private fun failure(
        message: String,
        retryable: Boolean,
    ): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Data(
                message = message,
                code = ErrorCode.FILE_001,
                retryable = retryable,
            ),
        )
}
