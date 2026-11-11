package com.culinary.core.database

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 打开配置与种子：外键、busy timeout、schema 版本、WAL、CHECK 触发器。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseFoundationTest {
    private lateinit var database: CulinaryDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    CulinaryDatabase::class.java,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enables foreign key enforcement`() {
        val enabled =
            database.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

        assertThat(enabled).isEqualTo(1)
    }

    @Test
    fun `sets a finite busy timeout`() {
        val timeout =
            database.openHelper.writableDatabase.query("PRAGMA busy_timeout").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

        assertThat(timeout).isEqualTo(5000)
    }

    @Test
    fun `creates the schema at version 9`() {
        val version =
            database.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

        assertThat(version).isEqualTo(9)
    }

    @Test
    fun `seeds the unit catalogue`() {
        val count =
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM unit").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

        assertThat(count).isEqualTo(15)
    }

    @Test
    fun `seeding is idempotent across reopens`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "seed-idempotency-test.db"
        context.deleteDatabase(name)
        try {
            repeat(2) {
                val fileDatabase =
                    Room
                        .databaseBuilder(context, CulinaryDatabase::class.java, name)
                        .addCallback(CulinaryDatabaseCallback())
                        .allowMainThreadQueries()
                        .build()
                try {
                    val count =
                        fileDatabase.openHelper.writableDatabase
                            .query("SELECT COUNT(*) FROM unit")
                            .use { cursor ->
                                cursor.moveToFirst()
                                cursor.getInt(0)
                            }
                    assertThat(count).isEqualTo(15)
                } finally {
                    fileDatabase.close()
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `uses write ahead logging for file databases`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "wal-foundation-test.db"
        context.deleteDatabase(name)
        val fileDatabase =
            Room
                .databaseBuilder(context, CulinaryDatabase::class.java, name)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        try {
            val mode =
                fileDatabase.openHelper.writableDatabase.query("PRAGMA journal_mode").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getString(0)
                }
            assertThat(mode.lowercase()).isEqualTo("wal")
        } finally {
            fileDatabase.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `rejects a unit with an out of range dimension`() {
        val sqlite = database.openHelper.writableDatabase

        assertThrows(SQLiteException::class.java) {
            sqlite.execSQL(
                "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                    "is_approximate, display_precision, is_active, sort_order, created_at) " +
                    "VALUES ('bad-1', 'bad', 'bad', 'bad', 'WEIGHT', 1, 0, 0, 2, 1, 99, 0)",
            )
        }
    }

    @Test
    fun `rejects a unit with a non positive base factor`() {
        val sqlite = database.openHelper.writableDatabase

        assertThrows(SQLiteException::class.java) {
            sqlite.execSQL(
                "INSERT INTO unit (id, code, name, symbol, dimension, base_factor, is_base, " +
                    "is_approximate, display_precision, is_active, sort_order, created_at) " +
                    "VALUES ('bad-2', 'bad2', 'bad', 'bad', 'MASS', 0, 0, 0, 2, 1, 99, 0)",
            )
        }
    }
}
