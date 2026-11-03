package com.culinary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.culinary.core.database.converter.DatabaseConverters
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.UnitEntity

/**
 * CULINARY 数据库（schema v1，A06）。
 *
 * `exportSchema = true`：每个版本必须提交 `schemas/` JSON，迁移测试依赖它。
 * 当前阶段 3 逐表落地；表集合完成前不宣称 v1 冻结。
 */
@Database(
    entities = [
        UnitEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class CulinaryDatabase : RoomDatabase() {
    abstract fun unitDao(): UnitDao

    companion object {
        const val DATABASE_NAME: String = "culinary.db"
    }
}
