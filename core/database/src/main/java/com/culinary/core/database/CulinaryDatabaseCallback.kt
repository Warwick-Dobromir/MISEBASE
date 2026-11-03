package com.culinary.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.database.schema.CulinarySchemaExtras
import com.culinary.core.database.seed.seedUnits

/**
 * 每次打开连接时施加的数据库前置条件（E02 / A06 §1.6）：
 *
 * 1. 外键必须开启；关闭视为严重配置错误；
 * 2. busy timeout 必须是有限值，避免瞬时 BUSY 直接失败；
 * 3. 重放 schema 附加项（CHECK 触发器）；
 * 4. 幂等写入单位种子。
 *
 * 注意：WAL 由 `DatabaseFactory` 在构建期设置，不在这里切换（切换 journal mode
 * 需要独占连接）。
 */
class CulinaryDatabaseCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.query("PRAGMA busy_timeout = 5000").use { cursor -> cursor.moveToFirst() }
        CulinarySchemaExtras.apply(db)
        seedUnits(db)
    }
}
