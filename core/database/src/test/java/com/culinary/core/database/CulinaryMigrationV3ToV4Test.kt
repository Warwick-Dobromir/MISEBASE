package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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

/** v3 → v4：只加可空扩展列与 kitchen_relation，不移动旧数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV3ToV4Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migrates v3 to v4 and preserves old data`() {
        createV3()
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(
                    CulinaryMigrations.MIGRATION_3_4,
                    CulinaryMigrations.MIGRATION_4_5,
                    CulinaryMigrations.MIGRATION_5_6,
                    CulinaryMigrations.MIGRATION_6_7,
                    CulinaryMigrations.MIGRATION_7_8,
                    CulinaryMigrations.MIGRATION_8_9,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        val sqlite = opened.openHelper.writableDatabase

        assertThat(userVersion(sqlite)).isEqualTo(9)
        assertThat(tableExists(sqlite, "kitchen_relation")).isTrue()
        assertThat(columnNames(sqlite, "sauce")).contains("english_name")
        assertThat(columnNames(sqlite, "ingredient")).contains("example_products")
        assertThat(columnNames(sqlite, "recipe")).contains("allergen_notes")
        assertThat(columnNames(sqlite, "kitchen_reference")).contains("english_name")
        assertThat(runBlocking { opened.sauceDao().findById(SAUCE_ID)?.name }).isEqualTo("迁移前酱汁")
        assertThat(runBlocking { opened.sauceDao().countSeed("v1") }).isEqualTo(0)
    }

    private fun createV3() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "3.json").readText())
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
            sqlite.version = 3
            sqlite.execSQL(
                "CREATE UNIQUE INDEX ux_ingredient_normalized_name " +
                    "ON ingredient(normalized_name) WHERE deleted_at IS NULL",
            )
            sqlite.execSQL(
                "CREATE UNIQUE INDEX ux_recipe_current ON recipe_version(recipe_id) WHERE is_current = 1",
            )
            sqlite.execSQL(
                "INSERT INTO sauce (id, name, normalized_name, status, created_at, updated_at, revision) " +
                    "VALUES ('$SAUCE_ID', '迁移前酱汁', '迁移前酱汁', 'ACTIVE', $NOW, $NOW, 1)",
            )
        }
    }

    private fun tableExists(
        db: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        db
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { cursor -> cursor.moveToFirst() }

    private fun columnNames(
        db: SupportSQLiteDatabase,
        table: String,
    ): List<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun userVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "migration-v3-v4.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val SAUCE_ID = "sauce-before-v4"
    }
}
