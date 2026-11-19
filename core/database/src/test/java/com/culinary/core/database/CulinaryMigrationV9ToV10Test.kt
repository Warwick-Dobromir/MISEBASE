package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** v9 → v10：新增菜谱研发备注字段，旧备注必须原样保留。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV9ToV10Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `adds eight independent fields and preserves legacy notes`() {
        createV9()
        val opened =
            Room.databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(CulinaryMigrations.MIGRATION_9_10)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        val sqlite = opened.openHelper.writableDatabase

        assertThat(userVersion(sqlite)).isEqualTo(10)
        val columns = columnNames(sqlite, "recipe")
        val expected = listOf(
            "product_standard",
            "raw_material_requirements",
            "preparation_notes",
            "process_parameters",
            "plating_standard",
            "storage_reheating",
            "adjustment_record",
            "test_record",
        )
        assertThat(columns).containsAtLeastElementsIn(expected)
        assertThat(singleString(sqlite, "SELECT notes FROM recipe WHERE id = 'legacy-recipe'"))
            .isEqualTo("这是旧版研发备注，必须保留")
        assertThat(singleString(sqlite, "SELECT seed_note FROM recipe WHERE id = 'legacy-recipe'"))
            .isEqualTo("旧版种子测试记录")
        expected.forEach { column ->
            assertThat(singleString(sqlite, "SELECT `$column` FROM recipe WHERE id = 'legacy-recipe'"))
                .isNull()
        }
    }

    private fun createV9() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "9.json").readText())
            val databaseJson = root.getJSONObject("database")
            val entities = databaseJson.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val tableName = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices") ?: continue
                for (indexIndex in 0 until indices.length()) {
                    val createSql = indices.getJSONObject(indexIndex).getString("createSql")
                    sqlite.execSQL(createSql.replace("${'$'}{TABLE_NAME}", tableName))
                }
            }
            sqlite.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '${databaseJson.getString("identityHash")}')",
            )
            sqlite.version = 9
            sqlite.execSQL(
                "INSERT INTO recipe (id, name, normalized_name, category, notes, seed_note, status, " +
                    "created_at, updated_at, revision) VALUES " +
                    "('legacy-recipe', '旧菜谱', '旧菜谱', 'DISH', '这是旧版研发备注，必须保留', " +
                    "'旧版种子测试记录', 'ACTIVE', 1, 1, 1)",
            )
        }
    }

    private fun columnNames(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun singleString(db: SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun userVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "migration-v9-v10.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
    }
}
