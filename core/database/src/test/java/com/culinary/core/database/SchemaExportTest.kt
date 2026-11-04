package com.culinary.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 校验 Room 导出的 schema JSON。
 *
 * v1（31 表）是历史基线，文件字节不得变化；v2（33 表）在 MB-SC-001 中新增。
 * 迁移测试依赖 `2.json`，因此这里同时锁定版本号与表数。
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
    fun `registers exactly one forward migration from v1 to v2`() {
        assertThat(CulinaryMigrations.ALL).hasLength(1)
        assertThat(CulinaryMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(CulinaryMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
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
