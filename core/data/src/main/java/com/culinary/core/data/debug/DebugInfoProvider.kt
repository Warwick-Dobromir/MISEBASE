package com.culinary.core.data.debug

import android.content.Context
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.database.CulinaryDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Provider

/** 一行调试信息（标签 + 值）。 */
data class DebugInfoItem(
    val label: String,
    val value: String,
)

/**
 * 调试信息（R01 阶段 6 的调试入口）。
 *
 * 只读：数据库路径/大小/版本、journal 模式、崩溃日志数量与最近时间。
 * 不导出数据、不修改任何文件。
 */
class DebugInfoProvider(
    private val context: Context,
    private val databaseProvider: Provider<CulinaryDatabase>,
    private val crashLogDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Suppress("DEPRECATION") // PackageManager.getPackageInfo(String, Int) 在 API 33 仍可用
    suspend fun read(): Outcome<List<DebugInfoItem>> =
        withContext(ioDispatcher) {
            try {
                val file = context.getDatabasePath(CulinaryDatabase.DATABASE_NAME)
                val sqlite = databaseProvider.get().openHelper.readableDatabase
                val version =
                    sqlite.query("PRAGMA user_version").use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else -1
                    }
                val journalMode =
                    sqlite.query("PRAGMA journal_mode").use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
                    }
                val crashLogs = crashLogDirectory.listFiles()?.filter { it.isFile }.orEmpty()
                val latestCrash = crashLogs.maxByOrNull { it.lastModified() }
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

                Outcome.success(
                    listOf(
                        DebugInfoItem("应用版本", packageInfo.versionName ?: "unknown"),
                        DebugInfoItem("数据库路径", file.absolutePath),
                        DebugInfoItem("数据库大小", "${file.length()} bytes"),
                        DebugInfoItem("user_version", version.toString()),
                        DebugInfoItem("journal_mode", journalMode),
                        DebugInfoItem("崩溃日志数", crashLogs.size.toString()),
                        DebugInfoItem(
                            "最近崩溃",
                            latestCrash?.let { Instant.ofEpochMilli(it.lastModified()).toString() } ?: "无",
                        ),
                    ),
                )
            } catch (e: android.database.sqlite.SQLiteException) {
                Outcome.failure(
                    CulinaryError.Data(
                        message = "cannot read debug info: ${e.message}",
                        code = ErrorCode.DB_001,
                        retryable = true,
                        cause = e,
                    ),
                )
            }
        }
}
