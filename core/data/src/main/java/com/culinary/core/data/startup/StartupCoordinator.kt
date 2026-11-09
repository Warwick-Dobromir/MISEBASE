package com.culinary.core.data.startup

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import com.culinary.core.database.CulinaryDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Provider

/**
 * 启动协调器（R01 §9.1）。
 *
 * 顺序：读取现有 `user_version` → 版本过高则拒绝 → 打开 Room（执行迁移）→ 就绪。
 * 损坏/迁移失败/打开失败进入 [StartupState.Recovery]，**绝不自动清空或重建数据库**。
 *
 * 数据库通过 [Provider] 惰性获取，避免 Activity/ViewModel 构造时就打开连接。
 */
class StartupCoordinator(
    @ApplicationContext private val context: Context,
    private val databaseProvider: Provider<CulinaryDatabase>,
    private val expectedSchemaVersion: Int = EXPECTED_SCHEMA_VERSION,
    private val databaseName: String = CulinaryDatabase.DATABASE_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun start(): StartupState =
        withContext(ioDispatcher) {
            val file = context.getDatabasePath(databaseName)

            val existingVersion =
                try {
                    readUserVersion(file)
                } catch (e: SQLiteDatabaseCorruptException) {
                    return@withContext StartupState.Recovery(RecoveryReason.DATABASE_CORRUPT, e.message)
                } catch (e: SQLiteException) {
                    return@withContext StartupState.Recovery(RecoveryReason.OPEN_FAILED, e.message)
                }

            if (existingVersion > expectedSchemaVersion) {
                return@withContext StartupState.Incompatible(
                    databaseVersion = existingVersion,
                    appSchemaVersion = expectedSchemaVersion,
                )
            }

            try {
                val database = databaseProvider.get()
                val actual = readUserVersion(database)
                if (actual < expectedSchemaVersion) {
                    StartupState.Recovery(RecoveryReason.MIGRATION_FAILED)
                } else {
                    StartupState.Ready
                }
            } catch (e: SQLiteDatabaseCorruptException) {
                StartupState.Recovery(RecoveryReason.DATABASE_CORRUPT, e.message)
            } catch (e: SQLiteException) {
                StartupState.Recovery(RecoveryReason.MIGRATION_FAILED, e.message)
            } catch (e: IllegalStateException) {
                StartupState.Recovery(RecoveryReason.OPEN_FAILED, e.message)
            }
        }

    /**
     * 文件不存在返回 0（Room 会新建当前版本）。
     *
     * 先校验 SQLite 文件头：损坏文件绝不能让 Room 打开——Room 的默认错误处理会删除损坏库，
     * 这违反"不自动清空数据"的硬规则。
     */
    private fun readUserVersion(file: File): Int {
        if (!file.exists() || file.length() == 0L) return 0
        if (!hasSqliteHeader(file)) {
            throw SQLiteDatabaseCorruptException("invalid SQLite header")
        }
        return SQLiteDatabase
            .openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY, noErrorHandler)
            .use { database -> readUserVersion(database) }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { stream -> stream.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun readUserVersion(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun readUserVersion(database: CulinaryDatabase): Int =
        database.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private companion object {
        /**
         * 应用期望的数据库 Schema 版本。
         *
         * **必须与 [com.culinary.core.database.CulinaryDatabase] 的 `@Database(version = 4)`
         * 保持同步。** 升级 Schema 时必须同时修改两处，并补“已持久化库再次启动”的回归测试；
         * 否则会把当前版本的库误判为“更新版本”，把应用锁在 [StartupState.Incompatible]。
         */
        const val EXPECTED_SCHEMA_VERSION = 7

        val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

        /** 打不开时让异常冒泡，由调用方映射为 Recovery，而不是静默新建。 */
        val noErrorHandler = DatabaseErrorHandler { _ -> }
    }
}
