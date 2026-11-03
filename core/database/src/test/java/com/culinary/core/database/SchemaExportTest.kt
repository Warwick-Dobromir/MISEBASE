package com.culinary.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * 校验 Room 导出的 schema JSON 与 A06 的 31 表一致。
 *
 * 这是"迁移框架可用"的前置条件：没有 v1 的导出 schema，就无法写
 * `MigrationTestHelper` 的 v1→v2 迁移测试。真正的迁移测试在阶段 9 的
 * instrumented 测试中运行（需要 androidTest assets 与设备）。
 */
class SchemaExportTest {
    private val schemaFile =
        File(
            "schemas/com.culinary.core.database.CulinaryDatabase/1.json",
        )

    @Test
    fun `exports schema version 1 with thirty one tables`() {
        assertThat(schemaFile.exists()).isTrue()

        val text = schemaFile.readText()
        val tableCount = Regex("\"tableName\"").findAll(text).count()

        assertThat(tableCount).isEqualTo(31)
    }

    @Test
    fun `records the database version`() {
        val text = schemaFile.readText()

        assertThat(text).contains("\"version\": 1")
    }

    @Test
    fun `migration registry starts empty and non destructive`() {
        assertThat(CulinaryMigrations.ALL).isEmpty()
    }
}
