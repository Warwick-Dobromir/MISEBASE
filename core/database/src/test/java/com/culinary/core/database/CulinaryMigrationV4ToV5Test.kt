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

/** v4 → v5：只加酱汁编辑器的可空列与关系排序列，不移动旧数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV4ToV5Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migrates v4 to v5 and preserves old sauce data`() {
        createV4()
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(CulinaryMigrations.MIGRATION_4_5)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        database = opened
        val sqlite = opened.openHelper.writableDatabase

        assertThat(userVersion(sqlite)).isEqualTo(5)
        assertThat(columnNames(sqlite, "sauce"))
            .containsAtLeast("description", "difficulty", "hero_caption")
        assertThat(columnNames(sqlite, "sauce_version")).contains("steps_json")
        assertThat(columnNames(sqlite, "sauce_component"))
            .containsAtLeast("label_override", "label_override_en")
        assertThat(columnNames(sqlite, "kitchen_relation")).contains("sort_order")
        assertThat(runBlocking { opened.sauceDao().findById(SAUCE_ID)?.name }).isEqualTo("迁移前酱汁")
        assertThat(runBlocking { opened.sauceVersionDao().findById(VERSION_ID)?.stepsJson }).isNull()
    }

    private fun createV4() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "4.json").readText())
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
            sqlite.version = 4
            sqlite.execSQL(
                "CREATE UNIQUE INDEX ux_sauce_current ON sauce_version(sauce_id) WHERE is_current = 1",
            )
            sqlite.execSQL(
                "CREATE UNIQUE INDEX ux_image_primary ON image_asset(owner_type, owner_id) " +
                    "WHERE role = 'PRIMARY' AND deleted_at IS NULL",
            )
            sqlite.execSQL(
                "INSERT INTO sauce (id, name, normalized_name, status, created_at, updated_at, revision) " +
                    "VALUES ('$SAUCE_ID', '迁移前酱汁', '迁移前酱汁', 'ACTIVE', $NOW, $NOW, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO sauce_version " +
                    "(id, sauce_id, version_no, status, yield_qty, yield_unit_id, is_current, created_at) " +
                    "VALUES ('$VERSION_ID', '$SAUCE_ID', 1, 'DRAFT', 250, 'unit-ml', 0, $NOW)",
            )
        }
    }

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
        const val DATABASE_NAME = "migration-v4-v5.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val SAUCE_ID = "sauce-before-v5"
        const val VERSION_ID = "sauce-version-before-v5"
    }
}
