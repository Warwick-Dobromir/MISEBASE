package com.culinary.core.data.startup

import android.database.sqlite.SQLiteDatabase
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.DatabaseFactory
import com.culinary.core.database.entity.IngredientEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Provider

/**
 * 启动协调：全新库、持久化 v2 库、v1→v2 迁移、版本过高与损坏六条路径。
 *
 * 回归重点：`EXPECTED_SCHEMA_VERSION` 必须与 `CulinaryDatabase` 的 v2 对齐；否则
 * 已持久化的 v2 库在第二次启动时会被误判为 `Incompatible`。
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
    fun `reports ready for a persisted schema v2 database`() =
        runBlocking {
            createPersistedV2Database()

            val state = coordinator().start()

            assertThat(state).isEqualTo(StartupState.Ready)
            assertThat(requireNotNull(database).openHelper.writableDatabase.version).isEqualTo(2)
        }

    @Test
    fun `migrates a v1 database to v2 and reports ready`() =
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
                    assertThat(sqlite.version).isEqualTo(2)
                    assertThat(columnNames(sqlite, "recipe_version")).contains("servings")
                    assertThat(count(sqlite, "ingredient")).isEqualTo(1)
                }
        }

    @Test
    fun `rejects a database created by a newer app`() =
        runBlocking {
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                sqlite.version = 3
            }
            val coordinator = coordinator()

            val state = coordinator.start()

            assertThat(state).isInstanceOf(StartupState.Incompatible::class.java)
            val incompatible = state as StartupState.Incompatible
            assertThat(incompatible.databaseVersion).isEqualTo(3)
            assertThat(incompatible.appSchemaVersion).isEqualTo(2)
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

    private fun createPersistedV2Database() {
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

    /**
     * 用当前 v2 库反向构造 v1 夹具：重建不含 `servings` 的 `recipe_version`、删除 v2 新增表、
     * 降 user_version、恢复 v1 identity hash。这样 JVM 测试不需要 instrumentation/room-testing，
     * 也不依赖 `1.json` 的路径。
     */
    private fun createV1Database() {
        createPersistedV2Database()
        val file = context.getDatabasePath(DATABASE_NAME)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            V1_RECIPE_VERSION_TRIGGERS.forEach { trigger ->
                sqlite.execSQL("DROP TRIGGER IF EXISTS `$trigger`")
            }
            sqlite.execSQL("PRAGMA foreign_keys = OFF")
            sqlite.execSQL(V1_RECIPE_VERSION_TABLE)
            sqlite.execSQL(V1_RECIPE_VERSION_COPY)
            sqlite.execSQL("DROP TABLE `recipe_version`")
            sqlite.execSQL("ALTER TABLE `recipe_version_v1` RENAME TO `recipe_version`")
            V1_RECIPE_VERSION_INDICES.forEach(sqlite::execSQL)
            sqlite.execSQL("PRAGMA foreign_keys = ON")

            sqlite.execSQL("DROP TABLE IF EXISTS `calculation_record_line`")
            sqlite.execSQL("DROP TABLE IF EXISTS `calculation_record`")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(V1_IDENTITY_HASH),
            )
            sqlite.version = 1
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

        /** `schemas/1.json` 的 identityHash；v1 Schema 已冻结。 */
        const val V1_IDENTITY_HASH = "b75e6e1b0cb2ce5b1eb24bad59e0a987"

        val V1_RECIPE_VERSION_TRIGGERS =
            listOf(
                "trg_recipe_version_check_insert",
                "trg_recipe_version_check_update",
                "trg_recipe_version_published_immutable",
            )

        /** v1 `recipe_version` 结构（不含 v2 的 `servings`），与 `schemas/1.json` 对齐。 */
        const val V1_RECIPE_VERSION_TABLE = """
            CREATE TABLE IF NOT EXISTS `recipe_version_v1` (
                `id` TEXT NOT NULL,
                `recipe_id` TEXT NOT NULL,
                `version_no` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `yield_qty` REAL NOT NULL,
                `yield_unit_id` TEXT NOT NULL,
                `instructions_md` TEXT,
                `change_note` TEXT,
                `based_on_version_id` TEXT,
                `is_current` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `published_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`yield_unit_id`) REFERENCES `unit`(`id`)
                    ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`based_on_version_id`) REFERENCES `recipe_version`(`id`)
                    ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """

        const val V1_RECIPE_VERSION_COPY = """
            INSERT INTO `recipe_version_v1` (
                `id`, `recipe_id`, `version_no`, `status`, `yield_qty`, `yield_unit_id`,
                `instructions_md`, `change_note`, `based_on_version_id`, `is_current`,
                `created_at`, `published_at`
            )
            SELECT
                `id`, `recipe_id`, `version_no`, `status`, `yield_qty`, `yield_unit_id`,
                `instructions_md`, `change_note`, `based_on_version_id`, `is_current`,
                `created_at`, `published_at`
            FROM `recipe_version`
        """

        val V1_RECIPE_VERSION_INDICES =
            listOf(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_version_recipe_id_version_no` " +
                    "ON `recipe_version` (`recipe_id`, `version_no`)",
                "CREATE INDEX IF NOT EXISTS `index_recipe_version_recipe_id_status` " +
                    "ON `recipe_version` (`recipe_id`, `status`)",
                "CREATE INDEX IF NOT EXISTS `index_recipe_version_yield_unit_id` " +
                    "ON `recipe_version` (`yield_unit_id`)",
                "CREATE INDEX IF NOT EXISTS `index_recipe_version_based_on_version_id` " +
                    "ON `recipe_version` (`based_on_version_id`)",
            )
    }
}
