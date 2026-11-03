package com.culinary.core.data.backup

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * journal 读写（E05 §13.2）。
 *
 * 写入流程：序列化 → 写 `.tmp` → `fsync` → 原子 rename。不原地覆盖，
 * 因此进程被杀时要么是旧 journal，要么是新 journal，不会出现半截 JSON。
 */
class RestoreJournalStore(
    private val file: File,
) {
    fun write(journal: RestoreJournal): Outcome<Unit> {
        return try {
            val directory = file.parentFile
            if (directory != null && !directory.isDirectory && !directory.mkdirs()) {
                return failure("cannot create journal directory")
            }
            val temporary = File(file.parentFile, ".${file.name}.tmp")
            val bytes = BackupJson.encodeToString(RestoreJournal.serializer(), journal).toByteArray()
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(file)) {
                temporary.delete()
                return failure("cannot commit restore journal")
            }
            Outcome.success(Unit)
        } catch (e: IOException) {
            failure("cannot write restore journal: ${e.message}")
        }
    }

    /** journal 不存在返回 `NotFound`；解析失败返回 `Transfer`，不猜内容。 */
    fun read(): Outcome<RestoreJournal> {
        if (!file.isFile) {
            return Outcome.failure(
                CulinaryError.NotFound(entity = "restore_journal", id = file.path),
            )
        }
        return try {
            val journal =
                BackupJson.decodeFromString(
                    RestoreJournal.serializer(),
                    file.readText(),
                )
            Outcome.success(journal)
        } catch (e: IllegalArgumentException) {
            failure("restore journal is corrupt: ${e.message}")
        } catch (e: kotlinx.serialization.SerializationException) {
            failure("restore journal is corrupt: ${e.message}")
        }
    }

    fun clear(): Outcome<Unit> {
        if (!file.exists()) return Outcome.success(Unit)
        return if (file.delete()) Outcome.success(Unit) else failure("cannot delete restore journal")
    }

    private fun failure(message: String): Outcome<Nothing> =
        Outcome.failure(
            CulinaryError.Transfer(
                message = message,
                code = ErrorCode.BACKUP_004,
            ),
        )
}
