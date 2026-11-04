package com.culinary.core.data.backup

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 恢复/快照使用的原子文件操作。
 *
 * 统一顺序：同目录临时文件 → 写入 → `fd.sync()` → 原子 rename → best-effort fsync 目录。
 * 目标文件在 rename 之前不会被覆盖；失败只删除临时文件。
 */
internal object AtomicFiles {
    fun writeBytesAtomically(
        target: File,
        bytes: ByteArray,
    ) {
        val temporary = temporaryFor(target)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            moveIntoPlace(temporary, target)
            fsyncDirectory(target.parentFile)
        } finally {
            temporary.delete()
        }
    }

    /** 把 [source] 复制成 [target]；[source] 保留（用于回滚副本）。 */
    fun copyAtomically(
        source: File,
        target: File,
    ) {
        val temporary = temporaryFor(target)
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            moveIntoPlace(temporary, target)
            fsyncDirectory(target.parentFile)
        } finally {
            temporary.delete()
        }
    }

    /**
     * 目录项 fsync。Android/Linux 允许以只读方式打开目录后 `fsync`；
     * 部分 JVM/文件系统不支持，降级为 no-op（原子 rename 已保证目标不半写）。
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun fsyncDirectory(directory: File?) {
        if (directory == null) return
        try {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(descriptor)
            } finally {
                Os.close(descriptor)
            }
        } catch (e: Exception) {
            // best-effort：平台不支持目录 fsync 时不影响已完成的目标替换
        }
    }

    private fun temporaryFor(target: File): File = File(target.parentFile, ".${target.name}.tmp")

    @Suppress("SwallowedException") // ATOMIC_MOVE 不被支持时改用普通移动，属于预期降级
    fun moveIntoPlace(
        temporary: File,
        target: File,
    ) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
