package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * 迁移回滚验证（MB-SC-001）。
 *
 * 结论：**不支持原地降级**。Room 只登记 `1 → 2` 前向迁移，没有 `2 → 1`；
 * 安全回滚路径是"恢复迁移前 v1 备份 + 使用 v1 构建"，或恢复备份后由 v2 重新迁移。
 *
 * 本测试验证：
 * 1. 迁移前 v1 备份文件本身可独立打开、数据完整、没有半迁移结构；
 * 2. 把该备份写回数据库位置后，新版本会重新执行 v1→v2，结果一致；
 * 3. 迁移注册表没有降级迁移，避免伪造"可原地降级"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryRollbackV1Test {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    @Suppress("LongMethod") // 回滚验证是一条不可分割的证据链：迁移、备份、恢复、重开
    fun `restores a pre migration v1 backup and reopens it without half migrated state`() {
        runBlocking {
            createV1()
            val preMigrationCopy = temporaryFolder.newFile("pre-migration-v1.db")
            context.getDatabasePath(DATABASE_NAME).copyTo(preMigrationCopy, overwrite = true)

            val migrated = openV2()
            try {
                migrated.calculationRecordDao().insertConfirmed(
                    com.culinary.core.database.entity.CalculationRecordEntity(
                        id = "calc-after-migration",
                        recipeVersionId = VERSION_ID,
                        servings = 1.0,
                        engineVersion = "stage2-1",
                        payloadHash = "d".repeat(64),
                        status = com.culinary.core.database.model.CalculationRecordStatusDb.CONFIRMED,
                        createdAt = NOW,
                        confirmedAt = NOW,
                    ),
                    emptyList(),
                )
                assertThat(userVersion(migrated.openHelper.writableDatabase)).isEqualTo(8)
            } finally {
                migrated.close()
                database = null
            }

            // 1) 迁移前备份（v1）可独立打开，且不含 v2 结构。
            SQLiteDatabase
                .openDatabase(
                    preMigrationCopy.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { v1 ->
                    assertThat(userVersion(v1)).isEqualTo(1)
                    assertThat(count(v1, "recipe_version")).isEqualTo(1)
                    assertThat(tableExists(v1, "calculation_record")).isFalse()
                    assertThat(tableExists(v1, "calculation_record_line")).isFalse()
                }

            // 2) 安全回滚：恢复备份到数据库位置，由 v2 重新迁移；旧版本构建需另装 v1 APK。
            preMigrationCopy.copyTo(context.getDatabasePath(DATABASE_NAME), overwrite = true)
            File("${context.getDatabasePath(DATABASE_NAME).path}-wal").delete()
            File("${context.getDatabasePath(DATABASE_NAME).path}-shm").delete()

            val reopened = openV2()
            try {
                val sqlite = reopened.openHelper.writableDatabase
                assertThat(userVersion(sqlite)).isEqualTo(8)
                assertThat(reopened.recipeVersionDao().findById(VERSION_ID)?.servings).isEqualTo(1)
                assertThat(reopened.calculationRecordDao().count()).isEqualTo(0)
                sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                    assertThat(cursor.count).isEqualTo(0)
                }
                sqlite.query("PRAGMA quick_check").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("ok")
                }
            } finally {
                reopened.close()
                database = null
            }

            // 3) 没有降级迁移；不得声称支持原地降级。
            assertThat(CulinaryMigrations.ALL.map { migration -> migration.startVersion to migration.endVersion })
                .containsExactly(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8)
        }
    }

    private fun createV1() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "1.json").readText())
            val databaseJson = root.getJSONObject("database")
            val entities = databaseJson.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (indexIndex in 0 until indices.length()) {
                    val createSql = indices.getJSONObject(indexIndex).getString("createSql")
                    sqlite.execSQL(createSql.replace("\${TABLE_NAME}", tableName))
                }
            }
            sqlite.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '${databaseJson.getString("identityHash")}')",
            )
            sqlite.version = 1
            sqlite.execSQL(
                "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                    "is_approximate, display_precision, is_active, sort_order, created_at) " +
                    "VALUES ('$UNIT_ID', 'tg', '测试克', 'tg', 'MASS', 1.0, 0, 0, 1, 1, 0, $NOW)",
            )
            sqlite.execSQL(
                "INSERT INTO recipe (id, name, normalized_name, category, status, " +
                    "created_at, updated_at, revision) VALUES " +
                    "('$RECIPE_ID', '回滚菜谱', '回滚菜谱', 'DISH', 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO recipe_version (id, recipe_id, version_no, status, yield_qty, " +
                    "yield_unit_id, is_current, created_at) VALUES " +
                    "('$VERSION_ID', '$RECIPE_ID', 1, 'PUBLISHED', 1000.0, '$UNIT_ID', 1, $NOW)",
            )
        }
    }

    private fun openV2(): CulinaryDatabase {
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(*CulinaryMigrations.ALL)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        opened.openHelper.writableDatabase
        return opened
    }

    private fun userVersion(sqlite: SQLiteDatabase): Int =
        sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun userVersion(sqlite: SupportSQLiteDatabase): Int =
        sqlite.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun count(
        sqlite: SQLiteDatabase,
        table: String,
    ): Int =
        sqlite.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun tableExists(
        sqlite: SQLiteDatabase,
        table: String,
    ): Boolean =
        sqlite
            .rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'", null)
            .use { cursor -> cursor.moveToFirst() }

    private companion object {
        const val DATABASE_NAME = "rollback-v1-v2.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-test-g"
        const val RECIPE_ID = "recipe-rollback"
        const val VERSION_ID = "recipe-rollback-v1"
    }
}
