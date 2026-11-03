package com.culinary.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 数据库构建入口。
 *
 * - WAL：单写者 + 读写并发的前提（E02）；
 * - 迁移只走 [CulinaryMigrations]，绝不使用破坏性回退；
 * - 回调负责外键、busy timeout、CHECK 触发器与种子。
 */
object DatabaseFactory {
    fun create(context: Context): CulinaryDatabase {
        val builder =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, CulinaryDatabase.DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(CulinaryDatabaseCallback())

        CulinaryMigrations.ALL.forEach { builder.addMigrations(it) }

        return builder.build()
    }
}
