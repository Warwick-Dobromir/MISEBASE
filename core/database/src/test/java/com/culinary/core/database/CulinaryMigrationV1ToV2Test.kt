package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.database.model.CalculationRecordStatusDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v1 → v2 迁移的 JVM 可复现测试（Robolectric）。
 *
 * v1 库不是手写 DDL，而是逐条执行 `1.json` 中 Room 导出的 `createSql` 并写入
 * v1 的 `identity_hash`，因此 Room 打开时会按真实迁移路径执行 `MIGRATION_1_2`
 * 并校验 `2.json`。覆盖空库、真实旧数据、11 张 DORMANT 表结构不变、迁移后完整性。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV1ToV2Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migrates an empty v1 database to v2`() =
        runBlocking {
            createV1(seedData = false)

            val opened = openV2()
            val sqlite = opened.openHelper.writableDatabase

            assertThat(userVersion(sqlite)).isEqualTo(6)
            assertThat(countTables(sqlite)).isEqualTo(38)
            assertThat(tableExists(sqlite, "kitchen_reference")).isTrue()
            assertThat(tableExists(sqlite, "kitchen_relation")).isTrue()
            assertThat(columnInfo(sqlite, "recipe_version", "servings"))
                .isEqualTo(ColumnInfo(type = "INTEGER", notNull = 1, defaultValue = "1"))
            assertThat(opened.calculationRecordDao().count()).isEqualTo(0)
            assertIntegrity(sqlite)
        }

    @Test
    fun `migrates real v1 data and defaults servings to one`() =
        runBlocking {
            createV1(seedData = true)
            val dormantBefore = dormantTableSql()

            val opened = openV2()
            val sqlite = opened.openHelper.writableDatabase
            val cursor =
                sqlite.query(
                    "SELECT recipe_id, version_no, servings, yield_qty FROM recipe_version",
                )

            cursor.use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo(RECIPE_ID)
                assertThat(it.getInt(1)).isEqualTo(1)
                assertThat(it.getInt(2)).isEqualTo(1)
                assertThat(it.getDouble(3)).isWithin(1e-9).of(1000.0)
                assertThat(it.moveToNext()).isFalse()
            }
            assertThat(recipeCount(sqlite)).isEqualTo(1)
            assertThat(ingredientCount(sqlite)).isEqualTo(1)
            assertThat(indexExists(sqlite, "ux_ingredient_normalized_name")).isTrue()
            assertThat(indexExists(sqlite, "ux_recipe_current")).isTrue()
            RESTORED_PARTIAL_INDEXES.forEach { name ->
                assertThat(indexExists(sqlite, name)).isTrue()
            }
            assertThat(dormantTableSql()).isEqualTo(dormantBefore)
            assertIntegrity(sqlite)
        }

    @Test
    fun `migrated schema accepts confirmed calculation records and enforces immutability`() =
        runBlocking {
            createV1(seedData = true)
            val opened = openV2()
            val sqlite = opened.openHelper.writableDatabase
            val record =
                CalculationRecordEntity(
                    id = CALC_ID,
                    recipeVersionId = VERSION_ID,
                    servings = 2.5,
                    engineVersion = "stage2-1",
                    payloadHash = "a".repeat(64),
                    status = CalculationRecordStatusDb.CONFIRMED,
                    createdAt = NOW,
                    confirmedAt = NOW,
                )

            opened.calculationRecordDao().insertConfirmed(
                record,
                listOf(
                    CalculationRecordLineEntity(
                        id = "line-1",
                        calculationRecordId = CALC_ID,
                        lineNo = 1,
                        ingredientId = INGREDIENT_ID,
                        quantity = 200.0,
                        unitId = UNIT_ID,
                        isOptional = false,
                    ),
                ),
            )

            assertThat(opened.calculationRecordDao().findById(CALC_ID)?.servings).isEqualTo(2.5)
            assertThat(opened.calculationRecordDao().findLines(CALC_ID)).hasSize(1)

            assertThrowsSql {
                sqlite.execSQL("UPDATE calculation_record SET servings = 3 WHERE id = '$CALC_ID'")
            }
            assertThrowsSql {
                sqlite.execSQL("UPDATE calculation_record_line SET quantity = 1 WHERE id = 'line-1'")
            }
            assertThrowsSql {
                sqlite.execSQL("UPDATE recipe_version SET servings = 0 WHERE id = '$VERSION_ID'")
            }
            assertIntegrity(sqlite)
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
        // Room 懒打开；触碰连接才会执行迁移与 schema 校验。
        opened.openHelper.writableDatabase
        return opened
    }

    private fun createV1(seedData: Boolean) {
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
            if (seedData) seedV1(sqlite)
        }
    }

    private fun seedV1(sqlite: SQLiteDatabase) {
        sqlite.execSQL(
            "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                "is_approximate, display_precision, is_active, sort_order, created_at) " +
                "VALUES ('$UNIT_ID', 'tg', '测试克', 'tg', 'MASS', 1.0, 0, 0, 1, 1, 0, $NOW)",
        )
        sqlite.execSQL(
            "INSERT INTO ingredient (id, name, normalized_name, status, created_at, updated_at, revision) " +
                "VALUES ('$INGREDIENT_ID', '面粉', '面粉', 'ACTIVE', $NOW, $NOW, 1)",
        )
        sqlite.execSQL(
            "INSERT INTO recipe (id, name, normalized_name, category, status, created_at, updated_at, revision) " +
                "VALUES ('$RECIPE_ID', '测试菜谱', '测试菜谱', 'DISH', 'ACTIVE', $NOW, $NOW, 1)",
        )
        sqlite.execSQL(
            "INSERT INTO recipe_version (id, recipe_id, version_no, status, yield_qty, " +
                "yield_unit_id, is_current, created_at) VALUES " +
                "('$VERSION_ID', '$RECIPE_ID', 1, 'PUBLISHED', 1000.0, '$UNIT_ID', 1, $NOW)",
        )
        // 真实 v1 库在 onOpen 时会创建这些部分索引（不在 Room 导出 schema 中）。
        sqlite.execSQL(
            "CREATE UNIQUE INDEX ux_ingredient_normalized_name " +
                "ON ingredient(normalized_name) WHERE deleted_at IS NULL",
        )
        sqlite.execSQL(
            "CREATE UNIQUE INDEX ux_recipe_current ON recipe_version(recipe_id) WHERE is_current = 1",
        )
    }

    private fun assertIntegrity(sqlite: SupportSQLiteDatabase) {
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
        sqlite.query("PRAGMA quick_check").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("ok")
        }
    }

    private fun dormantTableSql(): Map<String, String> {
        val placeholders = DORMANT_TABLES.joinToString(separator = ",") { "'$it'" }
        val path = context.getDatabasePath(DATABASE_NAME)
        val result = linkedMapOf<String, String>()
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite
                .rawQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name IN ($placeholders)",
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        result[cursor.getString(0)] = cursor.getString(1)
                    }
                }
        }
        return result
    }

    private fun tableExists(
        sqlite: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        sqlite
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { cursor -> cursor.moveToFirst() }

    private fun userVersion(sqlite: SupportSQLiteDatabase): Int =
        sqlite.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun countTables(sqlite: SupportSQLiteDatabase): Int =
        sqlite
            .query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')",
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private fun recipeCount(sqlite: SupportSQLiteDatabase): Int = count(sqlite, "recipe")

    private fun ingredientCount(sqlite: SupportSQLiteDatabase): Int = count(sqlite, "ingredient")

    private fun indexExists(
        sqlite: SupportSQLiteDatabase,
        name: String,
    ): Boolean =
        sqlite
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$name'")
            .use { cursor -> cursor.moveToFirst() }

    private fun count(
        sqlite: SupportSQLiteDatabase,
        table: String,
    ): Int =
        sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun columnInfo(
        sqlite: SupportSQLiteDatabase,
        table: String,
        column: String,
    ): ColumnInfo =
        sqlite.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    return@use ColumnInfo(
                        type = cursor.getString(2),
                        notNull = cursor.getInt(3),
                        defaultValue = cursor.getString(4),
                    )
                }
            }
            error("column $table.$column not found")
        }

    private fun assertThrowsSql(block: () -> Unit) {
        try {
            block()
            error("expected SQLiteException")
        } catch (expected: SQLiteException) {
            assertThat(expected.message).isNotEmpty()
        }
    }

    private data class ColumnInfo(
        val type: String,
        val notNull: Int,
        val defaultValue: String?,
    )

    private companion object {
        const val DATABASE_NAME = "migration-v1-v2.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-test-g"
        const val INGREDIENT_ID = "ingredient-flour"
        const val RECIPE_ID = "recipe-1"
        const val VERSION_ID = "recipe-v1"
        const val CALC_ID = "calc-1"

        val DORMANT_TABLES =
            listOf(
                "supplier",
                "production_plan",
                "production_plan_item",
                "production_plan_snapshot",
                "material_requirement",
                "material_requirement_source",
                "procurement_requirement",
                "purchase_order",
                "purchase_order_item",
                "goods_receipt",
                "goods_receipt_item",
            )

        val RESTORED_PARTIAL_INDEXES =
            listOf(
                "ux_ingredient_normalized_name",
                "ux_sauce_normalized_name",
                "ux_recipe_normalized_name",
                "ux_sauce_current",
                "ux_recipe_current",
                "ux_supplier_normalized_name",
                "ux_barcode_primary",
                "ux_image_primary",
                "ux_reservation_active",
            )
    }
}
