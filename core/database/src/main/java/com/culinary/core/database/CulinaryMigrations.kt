package com.culinary.core.database

import androidx.room.migration.Migration

/**
 * 迁移登记处（R01 §6.1）。
 *
 * v1 尚无迁移；任何 schema 变更都必须在这里登记 `Migration(n, n+1)` 并配套
 * `MigrationTestHelper` 测试（A07）。禁止 `fallbackToDestructiveMigration`。
 */
internal object CulinaryMigrations {
    val ALL: Array<Migration> = emptyArray()
}
