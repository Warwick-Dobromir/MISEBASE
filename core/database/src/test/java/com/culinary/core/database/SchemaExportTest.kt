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
    fun `registers all forward migrations`() {
        assertThat(CulinaryMigrations.ALL).hasLength(4)
        assertThat(CulinaryMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(CulinaryMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
        assertThat(CulinaryMigrations.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(CulinaryMigrations.MIGRATION_2_3.endVersion).isEqualTo(3)
        assertThat(CulinaryMigrations.MIGRATION_3_4.startVersion).isEqualTo(3)
        assertThat(CulinaryMigrations.MIGRATION_3_4.endVersion).isEqualTo(4)
        assertThat(CulinaryMigrations.MIGRATION_4_5.startVersion).isEqualTo(4)
        assertThat(CulinaryMigrations.MIGRATION_4_5.endVersion).isEqualTo(5)
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
