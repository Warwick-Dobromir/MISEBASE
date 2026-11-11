package com.culinary.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.database.entity.KitchenReferenceEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.KitchenReferenceKindDb
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

/** v2 → v3：只新增 kitchen_reference，旧数据与旧表保持完整。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CulinaryMigrationV2ToV3Test {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `migrates v2 to v3 and preserves old data`() =
        runBlocking {
            createV2()
            val opened = openV3()
            val sqlite = opened.openHelper.writableDatabase

            assertThat(userVersion(sqlite)).isEqualTo(9)
            assertThat(tableExists(sqlite, "kitchen_reference")).isTrue()
            assertThat(tableExists(sqlite, "kitchen_relation")).isTrue()
            assertThat(indexExists(sqlite, "ux_ingredient_normalized_name")).isTrue()
            assertThat(indexExists(sqlite, "ux_recipe_current")).isTrue()
            assertThat(opened.sauceDao().count()).isEqualTo(1)
            assertThat(opened.kitchenReferenceDao().count()).isEqualTo(0)

            opened.kitchenReferenceDao().insert(
                KitchenReferenceEntity(
                    id = REFERENCE_ID,
                    kind = KitchenReferenceKindDb.TECHNIQUE,
                    name = "低温慢煮",
                    normalizedName = "低温慢煮",
                    category = "热加工",
                    description = "真空低温加热",
                    status = EntityStatusDb.ACTIVE,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )

            val stored = opened.kitchenReferenceDao().findById(REFERENCE_ID)
            assertThat(stored?.name).isEqualTo("低温慢煮")
            assertThat(stored?.kind).isEqualTo(KitchenReferenceKindDb.TECHNIQUE)
            assertIntegrity(sqlite)
        }

    private fun createV2() {
        context.deleteDatabase(DATABASE_NAME)
        val path = context.getDatabasePath(DATABASE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sqlite ->
            val root = JSONObject(File(SCHEMA_DIR, "2.json").readText())
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
            sqlite.version = 2
            // 真实 v2 库在 onOpen 时会创建这两个不在 Room 导出 schema 中的局部索引。
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

    private fun openV3(): CulinaryDatabase {
        val opened =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .addMigrations(
                    CulinaryMigrations.MIGRATION_2_3,
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
        opened.openHelper.writableDatabase
        return opened
    }

    private fun tableExists(
        db: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        db
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { cursor -> cursor.moveToFirst() }

    private fun indexExists(
        db: SupportSQLiteDatabase,
        index: String,
    ): Boolean =
        db
            .query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(index))
            .use { cursor -> cursor.moveToFirst() }

    private fun userVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun assertIntegrity(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
        db.query("PRAGMA quick_check").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("ok")
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-v2-v3.db"
        const val SCHEMA_DIR = "schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
        const val SAUCE_ID = "sauce-before-v3"
        const val REFERENCE_ID = "reference-v3"
    }
}
