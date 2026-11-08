package com.culinary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设备上的增量迁移测试。
 *
 * 使用官方 [MigrationTestHelper] 验证 v1→v2 与 v2→v3：
 * v2→v3 只新增 kitchen_reference，旧数据与旧表保留。需要设备/模拟器。
 */
@RunWith(AndroidJUnit4::class)
class CulinaryDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CulinaryDatabase::class.java,
        )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun createsV1AndMatchesTheExportedSchema() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        val validated = helper.runMigrationsAndValidate(DATABASE_NAME, 1, true)

        val cursor = validated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'ingredient'")
        cursor.use {
            assertTrue("ingredient table must exist after validation", it.moveToFirst())
        }
        validated.close()
    }

    @Test
    fun migratesEmptyV1ToV2AndValidatesSchema() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                2,
                true,
                CulinaryMigrations.MIGRATION_1_2,
            )
        migrated.use { db ->
            val version =
                db.query("PRAGMA user_version").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            assertEquals(2, version)

            val servings = columnInfo(db, "recipe_version", "servings")
            assertEquals("INTEGER", servings.type)
            assertTrue(servings.notNull)
            assertEquals("1", servings.defaultValue)

            db
                .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'calculation_record'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
            assertIntegrity(db)
        }
    }

    @Test
    fun migratesV2ToV3AndCreatesKitchenReferenceTable() {
        helper.createDatabase(DATABASE_NAME, 2).close()

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                3,
                true,
                CulinaryMigrations.MIGRATION_2_3,
            )
        migrated.use { db ->
            val version =
                db.query("PRAGMA user_version").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            assertEquals(3, version)
            db
                .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'kitchen_reference'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
            assertIntegrity(db)
        }
    }

    @Test
    fun migratesV3ToV4AndCreatesRelationAndSeedColumns() {
        helper.createDatabase(DATABASE_NAME, 3).close()

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                4,
                true,
                CulinaryMigrations.MIGRATION_3_4,
            )
        migrated.use { db ->
            val version =
                db.query("PRAGMA user_version").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            assertEquals(4, version)
            db
                .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'kitchen_relation'")
                .use { cursor -> assertTrue(cursor.moveToFirst()) }
            assertTrue(columnInfo(db, "sauce", "english_name").notNull.not())
            assertTrue(columnInfo(db, "ingredient", "example_products").notNull.not())
            assertIntegrity(db)
        }
    }

    @Test
    fun migratesRealV1DataAndKeepsDormantTablesUntouched() {
        val v1 = helper.createDatabase(DATABASE_NAME, 1)
        seedV1(v1)
        val dormantBefore = dormantTableSql(v1)
        v1.close()

        val migrated =
            helper.runMigrationsAndValidate(
                DATABASE_NAME,
                2,
                true,
                CulinaryMigrations.MIGRATION_1_2,
            )
        migrated.use { db ->
            db.query("SELECT servings FROM recipe_version WHERE id = '$VERSION_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            assertEquals(dormantBefore, dormantTableSql(db))
            assertIntegrity(db)
            assertTrue(
                db
                    .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'calculation_record_line'")
                    .use { cursor -> cursor.moveToFirst() },
            )
        }
    }

    private fun seedV1(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                "is_approximate, display_precision, is_active, sort_order, created_at) " +
                "VALUES ('$UNIT_ID', 'tg', '测试克', 'tg', 'MASS', 1.0, 0, 0, 1, 1, 0, $NOW)",
        )
        db.execSQL(
            "INSERT INTO recipe (id, name, normalized_name, category, status, " +
                "created_at, updated_at, revision) VALUES " +
                "('$RECIPE_ID', '迁移菜谱', '迁移菜谱', 'DISH', 'ACTIVE', $NOW, $NOW, 1)",
        )
        db.execSQL(
            "INSERT INTO recipe_version (id, recipe_id, version_no, status, yield_qty, " +
                "yield_unit_id, is_current, created_at) VALUES " +
                "('$VERSION_ID', '$RECIPE_ID', 1, 'PUBLISHED', 1000.0, '$UNIT_ID', 1, $NOW)",
        )
        // 模拟 v1 onOpen 创建的部分索引；迁移必须先移除，onOpen 再重建。
        db.execSQL(
            "CREATE UNIQUE INDEX ux_recipe_current ON recipe_version(recipe_id) WHERE is_current = 1",
        )
    }

    private fun dormantTableSql(db: androidx.sqlite.db.SupportSQLiteDatabase): Map<String, String> {
        val placeholders = DORMANT_TABLES.joinToString(separator = ",") { "'$it'" }
        val result = linkedMapOf<String, String>()
        db
            .query(
                "SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name IN ($placeholders)",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    result[cursor.getString(0)] = cursor.getString(1)
                }
            }
        return result
    }

    private fun assertIntegrity(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        db.query("PRAGMA quick_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
    }

    private fun columnInfo(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        column: String,
    ): ColumnInfo =
        db.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) {
                    return@use ColumnInfo(
                        type = cursor.getString(2),
                        notNull = cursor.getInt(3) == 1,
                        defaultValue = cursor.getString(4),
                    )
                }
            }
            error("column $table.$column not found")
        }

    private data class ColumnInfo(
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
    )

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-test-g"
        const val RECIPE_ID = "recipe-migration"
        const val VERSION_ID = "recipe-migration-v1"

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
    }
}
