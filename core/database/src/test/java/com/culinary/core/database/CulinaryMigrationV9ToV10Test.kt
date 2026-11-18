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
    fun `adds ingredient archive fields and preserves existing rows`() {
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
        assertThat(columnNames(sqlite, "ingredient")).containsAtLeast("common_varieties", "edible_portion")
        assertThat(singleString(sqlite, "SELECT name FROM ingredient WHERE id = 'ingredient-v9'"))
            .isEqualTo("柠檬")
        assertThat(singleString(sqlite, "SELECT common_varieties FROM ingredient WHERE id = 'ingredient-v9'"))
            .isNull()
        sqlite.execSQL(
            "UPDATE ingredient SET common_varieties = '尤力克、里斯本', edible_portion = '果肉与果皮' " +
                "WHERE id = 'ingredient-v9'",
        )
        assertThat(singleString(sqlite, "SELECT common_varieties FROM ingredient WHERE id = 'ingredient-v9'"))
            .isEqualTo("尤力克、里斯本")
        assertThat(singleString(sqlite, "SELECT edible_portion FROM ingredient WHERE id = 'ingredient-v9'"))
            .isEqualTo("果肉与果皮")
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
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (indexIndex in 0 until indices.length()) {
                    val sql = indices.getJSONObject(indexIndex).getString("createSql")
                    sqlite.execSQL(sql.replace("\${TABLE_NAME}", table))
                }
            }
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '" +
                    databaseJson.getString("identityHash") + "')",
            )
            sqlite.version = 9
            sqlite.execSQL(
                "INSERT INTO ingredient (id, name, normalized_name, status, created_at, updated_at, revision) " +
                    "VALUES ('ingredient-v9', '柠檬', '柠檬', 'ACTIVE', 100, 100, 1)",
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
