package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v8 → v9：库存档案化迁移。
 *
 * 重点验证三件事：
 * 1. 旧批次/流水一行不丢，并按商品回填档案归属；
 * 2. `product_id` 放宽为可空（酱汁现货批次没有采购商品），旧数据保持原值；
 * 3. 迁移后触发器仍生效（流水不可改、批次可冻结）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV8ToV9Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migrates v8 to v9 and backfills archive ownership`() {
        createV8()
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(CulinaryMigrations.MIGRATION_8_9)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        val sqlite = opened.openHelper.writableDatabase

        assertThat(userVersion(sqlite)).isEqualTo(9)

        // 旧批次保留并被挂到自动生成的档案上
        assertThat(columnNames(sqlite, "inventory_batch"))
            .containsAtLeast(
                "archive_id",
                "production_date",
                "frozen_at",
                "frozen_reason",
                "recycled_at",
                "is_temporary",
                "temporary_since",
            )
        assertThat(columnNotNull(sqlite, "inventory_batch", "product_id")).isFalse()
        assertThat(singleString(sqlite, "SELECT archive_id FROM inventory_batch WHERE id = '$BATCH_ID'"))
            .isEqualTo("arch_$PRODUCT_ID")
        assertThat(singleString(sqlite, "SELECT name FROM inventory_archive WHERE id = 'arch_$PRODUCT_ID'"))
            .isEqualTo("全脂牛奶 1L")
        assertThat(
            singleString(
                sqlite,
                "SELECT product_id FROM inventory_archive_product WHERE archive_id = 'arch_$PRODUCT_ID'",
            ),
        ).isEqualTo(PRODUCT_ID)

        // 旧流水保留并回填档案归属；新增操作人与打印任务列
        assertThat(columnNames(sqlite, "inventory_ledger"))
            .containsAtLeast("archive_id", "operator", "print_job_id")
        assertThat(singleString(sqlite, "SELECT archive_id FROM inventory_ledger WHERE id = '$LEDGER_ID'"))
            .isEqualTo("arch_$PRODUCT_ID")
        assertThat(singleDouble(sqlite, "SELECT on_hand_after FROM inventory_ledger WHERE id = '$LEDGER_ID'"))
            .isEqualTo(100.0)

        // 酱汁现货批次可以没有采购商品
        sqlite.execSQL(
            "INSERT INTO inventory_batch (id, product_id, archive_id, location_id, batch_code, " +
                "received_at, production_date, expiry_date, quantity_on_hand, quantity_reserved, " +
                "stock_unit_id, currency_code, status, is_temporary, created_at, updated_at, revision) " +
                "VALUES ('sauce-batch', NULL, 'arch-sauce', '$LOCATION_ID', NULL, $NOW, '2026-11-01', " +
                "'2026-12-01', 500, 0, '$UNIT_ID', 'CNY', 'ACTIVE', 0, $NOW, $NOW, 1)",
        )
        assertThat(singleString(sqlite, "SELECT product_id FROM inventory_batch WHERE id = 'sauce-batch'"))
            .isNull()

        // 冻结状态被新 CHECK 触发器接受
        sqlite.execSQL(
            "UPDATE inventory_batch SET status = 'FROZEN', frozen_at = $NOW, " +
                "frozen_reason = '待质检' WHERE id = '$BATCH_ID'",
        )
        assertThat(singleString(sqlite, "SELECT status FROM inventory_batch WHERE id = '$BATCH_ID'"))
            .isEqualTo("FROZEN")

        // 流水仍然只追加
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            sqlite.execSQL("UPDATE inventory_ledger SET note = 'tampered' WHERE id = '$LEDGER_ID'")
        }

        // v9 新表全部建立；档案表已按商品回填一行，其余为空
        assertThat(singleString(sqlite, "SELECT COUNT(*) FROM inventory_archive")).isEqualTo("1")
        assertThat(singleString(sqlite, "SELECT COUNT(*) FROM inventory_archive_product")).isEqualTo("1")
        listOf(
            "inventory_freeze_record",
            "inventory_location_transfer",
            "inventory_issue_slip",
            "inventory_issue_slip_line",
            "inventory_print_job",
            "inventory_stocktake",
            "inventory_stocktake_line",
        ).forEach { table ->
            assertThat(singleString(sqlite, "SELECT COUNT(*) FROM `$table`")).isEqualTo("0")
        }
    }

    private fun createV8() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "8.json").readText())
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
            sqlite.version = 8
            sqlite.execSQL(
                "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                    "is_approximate, display_precision, is_active, sort_order, created_at) " +
                    "VALUES ('$UNIT_ID', 'g', '克', 'g', 'MASS', 1.0, 1, 0, 0, 1, 0, $NOW)",
            )
            sqlite.execSQL(
                "INSERT INTO ingredient (id, name, normalized_name, default_unit_id, status, " +
                    "created_at, updated_at, revision) " +
                    "VALUES ('$INGREDIENT_ID', '牛奶', '牛奶', '$UNIT_ID', 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO storage_location (id, name, normalized_name, kind, sort_order, status, " +
                    "created_at, updated_at, revision) " +
                    "VALUES ('$LOCATION_ID', '冷藏 A-01', '冷藏a01', 'FRIDGE', 0, 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO product (id, ingredient_id, name, normalized_name, content_qty, " +
                    "content_unit_id, stock_unit_id, stock_to_content_factor, currency_code, " +
                    "status, created_at, updated_at, revision) " +
                    "VALUES ('$PRODUCT_ID', '$INGREDIENT_ID', '全脂牛奶 1L', '全脂牛奶1l', 1000, " +
                    "'$UNIT_ID', '$UNIT_ID', 1.0, 'CNY', 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO inventory_batch (id, product_id, location_id, batch_code, received_at, " +
                    "expiry_date, quantity_on_hand, quantity_reserved, stock_unit_id, currency_code, " +
                    "status, created_at, updated_at, revision) " +
                    "VALUES ('$BATCH_ID', '$PRODUCT_ID', '$LOCATION_ID', 'B-001', $NOW, '2026-12-01', " +
                    "100, 0, '$UNIT_ID', 'CNY', 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO inventory_ledger (id, batch_id, product_id, location_id, type, " +
                    "on_hand_delta, reserved_delta, stock_unit_id, on_hand_after, reserved_after, " +
                    "idempotency_key, note, occurred_at, created_at) " +
                    "VALUES ('$LEDGER_ID', '$BATCH_ID', '$PRODUCT_ID', '$LOCATION_ID', " +
                    "'PURCHASE_RECEIPT', 100, 0, '$UNIT_ID', 100, 0, 'seed-key', '旧数据', $NOW, $NOW)",
            )
        }
    }

    private fun columnNames(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<String> = columnInfo(db, table).map { it.first }

    private fun columnNotNull(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ): Boolean = columnInfo(db, table).first { it.first == column }.second

    private fun columnInfo(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<Pair<String, Boolean>> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    // PRAGMA table_info 第 4 列是 notnull 标记；true 表示 NOT NULL。
                    add(cursor.getString(1) to (cursor.getInt(3) != 0))
                }
            }
        }

    private fun singleDouble(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Double =
        db.query(sql).use { cursor ->
            cursor.moveToFirst()
            cursor.getDouble(0)
        }

    private fun singleString(
        db: SupportSQLiteDatabase,
        sql: String,
    ): String? =
        db.query(sql).use { cursor ->
            if (!cursor.moveToFirst()) return null
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun userVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "migration-v8-v9.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val UNIT_ID = "unit-g"
        const val INGREDIENT_ID = "ingredient-milk"
        const val LOCATION_ID = "loc-a01"
        const val PRODUCT_ID = "product-milk-1l"
        const val BATCH_ID = "batch-before-v9"
        const val LEDGER_ID = "ledger-before-v9"
    }
}
