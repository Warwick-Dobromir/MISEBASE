package com.culinary.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.Executor

/**
 * 数据库构建入口（E02 §3.1）。
 *
 * - WAL：单写者 + 读写并发的前提；
 * - 写事务用单线程执行器，读用固定大小线程池，顺序可预测；
 * - 迁移只走 [CulinaryMigrations]，绝不使用破坏性回退；
 * - 回调负责外键、busy timeout、CHECK 触发器与种子。
 */
object DatabaseFactory {
    fun create(
        context: Context,
        writeExecutor: Executor,
        readExecutor: Executor,
    ): CulinaryDatabase {
        val builder =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, CulinaryDatabase.DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .setQueryExecutor(readExecutor)
                .setTransactionExecutor(writeExecutor)
                .addCallback(CulinaryDatabaseCallback())

        CulinaryMigrations.ALL.forEach { builder.addMigrations(it) }

        return builder.build()
    }
}
