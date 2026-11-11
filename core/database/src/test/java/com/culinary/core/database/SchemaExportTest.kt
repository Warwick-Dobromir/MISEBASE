package com.culinary.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 校验 Room 导出的 schema JSON。
 *
 * v1（31 表）、v2（33 表）、v3（34 表）是历史基线；v4 新增扩展字段与 kitchen_relation（35 表）。
 * 迁移测试依赖各版本 JSON，因此这里同时锁定版本号与表数。
 */
class SchemaExportTest {
    private val schemaDirectory =
        File(
            "schemas/com.culinary.core.database.CulinaryDatabase",
        )

    @Test
    fun `keeps the v1 schema byte for byte`() {
        val v1 = File(schemaDirectory, "1.json")

        assertThat(v1.exists()).isTrue()
        assertThat(sha256(v1)).isEqualTo(V1_SHA256)
        assertThat(v1.readText()).contains("\"version\": 1")
        assertThat(tableCount(v1)).isEqualTo(31)
    }

    @Test
    fun `exports schema version 2 with thirty three tables`() {
        val v2 = File(schemaDirectory, "2.json")

        assertThat(v2.exists()).isTrue()
        assertThat(v2.readText()).contains("\"version\": 2")
        assertThat(tableCount(v2)).isEqualTo(33)
    }

    @Test
    fun `exports schema version 3 with thirty four tables`() {
        val v3 = File(schemaDirectory, "3.json")

        assertThat(v3.exists()).isTrue()
        assertThat(v3.readText()).contains("\"version\": 3")
        assertThat(v3.readText()).contains("\"tableName\": \"kitchen_reference\"")
        assertThat(tableCount(v3)).isEqualTo(34)
    }

    @Test
    fun `exports schema version 4 with thirty five tables`() {
        val v4 = File(schemaDirectory, "4.json")

        assertThat(v4.exists()).isTrue()
        assertThat(v4.readText()).contains("\"version\": 4")
        assertThat(v4.readText()).contains("\"tableName\": \"kitchen_relation\"")
        assertThat(v4.readText()).contains("\"columnName\": \"seed_version\"")
        assertThat(tableCount(v4)).isEqualTo(35)
    }

    @Test
    fun `exports schema version 5 with the editor columns`() {
        val v5 = File(schemaDirectory, "5.json")

        assertThat(v5.exists()).isTrue()
        assertThat(v5.readText()).contains("\"version\": 5")
        assertThat(v5.readText()).contains("\"columnName\": \"steps_json\"")
        assertThat(v5.readText()).contains("\"columnName\": \"difficulty\"")
        assertThat(tableCount(v5)).isEqualTo(35)
    }

    @Test
    fun `exports schema version 6 with category and reference content tables`() {
        val v6 = File(schemaDirectory, "6.json")

        assertThat(v6.exists()).isTrue()
        assertThat(v6.readText()).contains("\"version\": 6")
        assertThat(v6.readText()).contains("\"tableName\": \"archive_category\"")
        assertThat(v6.readText()).contains("\"tableName\": \"archive_category_assignment\"")
        assertThat(v6.readText()).contains("\"tableName\": \"kitchen_reference_content\"")
        assertThat(tableCount(v6)).isEqualTo(38)
    }

    @Test
    fun `exports schema version 7 with recipe steps json`() {
        val v7 = File(schemaDirectory, "7.json")

        assertThat(v7.exists()).isTrue()
        assertThat(v7.readText()).contains("\"version\": 7")
        assertThat(v7.readText()).contains("\"columnName\": \"steps_json\"")
        assertThat(tableCount(v7)).isEqualTo(38)
    }

    @Test
    fun `exports schema version 8 with min range and recipe notes`() {
        val v8 = File(schemaDirectory, "8.json")

        assertThat(v8.exists()).isTrue()
        assertThat(v8.readText()).contains("\"version\": 8")
        assertThat(v8.readText()).contains("\"columnName\": \"min_range_qty\"")
        assertThat(v8.readText()).contains("\"columnName\": \"min_range_unit_id\"")
        assertThat(v8.readText()).contains("\"columnName\": \"notes\"")
        assertThat(tableCount(v8)).isEqualTo(38)
    }

    @Test
    fun `exports schema version 9 with inventory archive tables`() {
        val v9 = File(schemaDirectory, "9.json")

        assertThat(v9.exists()).isTrue()
        assertThat(v9.readText()).contains("\"version\": 9")
        assertThat(v9.readText()).contains("\"tableName\": \"inventory_archive\"")
        assertThat(v9.readText()).contains("\"tableName\": \"inventory_archive_product\"")
        assertThat(v9.readText()).contains("\"tableName\": \"inventory_issue_slip\"")
        assertThat(v9.readText()).contains("\"tableName\": \"inventory_print_job\"")
        assertThat(v9.readText()).contains("\"columnName\": \"archive_id\"")
        assertThat(v9.readText()).contains("\"columnName\": \"is_temporary\"")
        assertThat(tableCount(v9)).isEqualTo(47)
    }

    @Test
    fun `registers all forward migrations`() {
        assertThat(CulinaryMigrations.ALL).hasLength(8)
        assertThat(CulinaryMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(CulinaryMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
        assertThat(CulinaryMigrations.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(CulinaryMigrations.MIGRATION_2_3.endVersion).isEqualTo(3)
        assertThat(CulinaryMigrations.MIGRATION_3_4.startVersion).isEqualTo(3)
        assertThat(CulinaryMigrations.MIGRATION_3_4.endVersion).isEqualTo(4)
        assertThat(CulinaryMigrations.MIGRATION_4_5.startVersion).isEqualTo(4)
        assertThat(CulinaryMigrations.MIGRATION_4_5.endVersion).isEqualTo(5)
        assertThat(CulinaryMigrations.MIGRATION_5_6.startVersion).isEqualTo(5)
        assertThat(CulinaryMigrations.MIGRATION_5_6.endVersion).isEqualTo(6)
        assertThat(CulinaryMigrations.MIGRATION_6_7.startVersion).isEqualTo(6)
        assertThat(CulinaryMigrations.MIGRATION_6_7.endVersion).isEqualTo(7)
        assertThat(CulinaryMigrations.MIGRATION_7_8.startVersion).isEqualTo(7)
        assertThat(CulinaryMigrations.MIGRATION_7_8.endVersion).isEqualTo(8)
        assertThat(CulinaryMigrations.MIGRATION_8_9.startVersion).isEqualTo(8)
        assertThat(CulinaryMigrations.MIGRATION_8_9.endVersion).isEqualTo(9)
    }

    private fun tableCount(file: File): Int = Regex("\"tableName\"").findAll(file.readText()).count()

    private fun sha256(file: File): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val V1_SHA256: String =
            "a305afd334c8069c8342ed0664944d8817f24bc1c06f251e1df4da4bfd070b7b"
    }
}
