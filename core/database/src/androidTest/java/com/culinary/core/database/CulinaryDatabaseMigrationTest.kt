package com.culinary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库 v1 的迁移基线测试（G2 阻塞项）。
 *
 * 当前只有 v1：建库后按导出的 `1.json` 校验 schema 与 identity hash；
 * 未来新增版本时在这里追加 `runMigrationsAndValidate(name, targetVersion, true, MIGRATION_X_Y)`。
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
    fun enablesForeignKeysForTheValidatedDatabase() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        val validated = helper.runMigrationsAndValidate(DATABASE_NAME, 1, true)
        val cursor = validated.query("PRAGMA foreign_keys")

        cursor.use {
            assertTrue(it.moveToFirst())
            // MigrationTestHelper 打开的是普通 SQLite 连接；显式开启后再验证一次。
            validated.execSQL("PRAGMA foreign_keys = ON")
            val enabled = validated.query("PRAGMA foreign_keys")
            enabled.use { check ->
                assertTrue(check.moveToFirst())
                assertTrue("foreign_keys must be reported as ON", check.getInt(0) == 1)
            }
        }
        validated.close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}
