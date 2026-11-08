package com.culinary.core.data.startup

import android.database.sqlite.SQLiteDatabase
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.DatabaseFactory
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Provider

/**
 * 启动协调：全新库、持久化 v4 库、v1→v4 迁移、版本过高与损坏六条路径。
 *
 * 回归重点：`EXPECTED_SCHEMA_VERSION` 必须与 `CulinaryDatabase` 的 v5 对齐；否则
 * 已持久化的 v4 库在第二次启动时会被误判为 `Incompatible`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupCoordinatorTest {
    private val context = RuntimeEnvironment.getApplication()
    private var database: CulinaryDatabase? = null
    private val writeExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val readExecutor: ExecutorService = Executors.newFixedThreadPool(2)

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        writeExecutor.shutdownNow()
        readExecutor.shutdownNow()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `reports ready for a fresh database`() =
        runBlocking {
            val state = coordinator().start()

            assertThat(state).isEqualTo(StartupState.Ready)
        }

    @Test
    fun `reports ready for a persisted schema v4 database`() =
        runBlocking {
            createPersistedV4Database()

            val state = coordinator().start()

            assertThat(state).isEqualTo(StartupState.Ready)
            assertThat(requireNotNull(database).openHelper.writableDatabase.version).isEqualTo(5)
        }

    @Test
    fun `migrates a v1 database to v4 and reports ready`() =
        runBlocking {
            createV1Database()
            assertV1Fixture()

            val state = coordinator().start()

            assertThat(state).isEqualTo(StartupState.Ready)
            database?.close()
            database = null
            SQLiteDatabase
                .openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READONLY)
                .use { sqlite ->
                    assertThat(sqlite.version).isEqualTo(5)
                    assertThat(columnNames(sqlite, "recipe_version")).contains("servings")
                    assertThat(tableNames(sqlite)).contains("kitchen_reference")
                    assertThat(tableNames(sqlite)).contains("kitchen_relation")
                    assertThat(count(sqlite, "ingredient")).isEqualTo(1)
                }
        }

    @Test
    fun `rejects a database created by a newer app`() =
        runBlocking {
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                sqlite.version = 6
            }
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isInstanceOf(StartupState.Incompatible::class.java)
            val incompatible = state as StartupState.Incompatible
            assertThat(incompatible.databaseVersion).isEqualTo(6)
            assertThat(incompatible.appSchemaVersion).isEqualTo(5)
        }

    @Test
    fun `routes a corrupt database to recovery without deleting it`() =
        runBlocking {
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(256) { index -> (index * 7).toByte() })
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isInstanceOf(StartupState.Recovery::class.java)
            assertThat((state as StartupState.Recovery).reason).isEqualTo(RecoveryReason.DATABASE_CORRUPT)
            assertThat(file.exists()).isTrue()
        }

    private fun coordinator(): StartupCoordinator =
        StartupCoordinator(
            context = context,
            databaseProvider = Provider { buildRoom().also { database = it } },
            ioDispatcher = Dispatchers.IO,
        )

    private fun buildRoom(): CulinaryDatabase = DatabaseFactory.create(context, writeExecutor, readExecutor)

    private fun createPersistedV4Database() {
        context.deleteDatabase(DATABASE_NAME)
        val built = buildRoom()
        built.openHelper.writableDatabase
        runBlocking {
            built.ingredientDao().insert(
                IngredientEntity(
                    id = "ingredient-1",
                    name = "酱油",
                    normalizedName = "酱油",
                    createdAt = 1_760_000_000_000L,
                    updatedAt = 1_760_000_000_000L,
                ),
            )
        }
        built.close()
    }

    /** 直接从冻结的 `schemas/1.json` 建 v1 夹具，避免从 v4 反向删列造成伪结构。 */
    private fun createV1Database() {
        context.deleteDatabase(DATABASE_NAME)
        val file = context.getDatabasePath(DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
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
                "INSERT INTO ingredient (id, name, normalized_name, status, created_at, updated_at, revision) " +
                    "VALUES ('ingredient-1', '酱油', '酱油', 'ACTIVE', $NOW, $NOW, 1)",
            )
        }
        check(file.exists()) { "v1 fixture was not created" }
    }

    private fun columnNames(
        sqlite: SQLiteDatabase,
        table: String,
    ): List<String> =
        sqlite.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun assertV1Fixture() {
        SQLiteDatabase
            .openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READONLY)
            .use { sqlite ->
                assertThat(sqlite.version).isEqualTo(1)
                assertThat(columnNames(sqlite, "recipe_version")).doesNotContain("servings")
                assertThat(tableNames(sqlite)).doesNotContain("calculation_record")
                assertThat(tableNames(sqlite)).doesNotContain("calculation_record_line")
                assertThat(tableNames(sqlite)).doesNotContain("kitchen_reference")
                assertThat(tableNames(sqlite)).doesNotContain("kitchen_relation")
            }
    }

    private fun tableNames(sqlite: SQLiteDatabase): List<String> =
        sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun count(
        sqlite: SQLiteDatabase,
        table: String,
    ): Int =
        sqlite.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        val DATABASE_NAME: String = CulinaryDatabase.DATABASE_NAME
        const val SCHEMA_DIR = "../database/schemas/com.culinary.core.database.CulinaryDatabase"
        const val NOW = 1_760_000_000_000L
    }
}
