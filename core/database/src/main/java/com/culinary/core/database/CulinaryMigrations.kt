package com.culinary.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.database.schema.CulinarySchemaExtras

/**
 * Schema v1 → v2（MB-SC-001）。
 *
 * 变更范围只有两项：
 * 1. `recipe_version` 增加 `servings INTEGER NOT NULL DEFAULT 1`；
 * 2. 新增 `calculation_record` 与 `calculation_record_line`。
 *
 * 31 张既有表（含 11 张 DORMANT）不删除、不重建、不改结构。`1.json` 保持不变。
 */
internal object CulinaryMigrations {
    val MIGRATION_1_2: Migration = MIGRATION_1_2_IMPL

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

private val MIGRATION_1_2_IMPL =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 先移除 v1 运行时创建、但不在 Room 导出 schema 中的部分索引；
            // 迁移校验通过后由 onOpen 按原定义重建。
            CulinarySchemaExtras.dropPartialIndexes(db)
            db.execSQL(
                "ALTER TABLE `recipe_version` ADD COLUMN `servings` INTEGER NOT NULL DEFAULT 1",
            )
            db.execSQL(CREATE_CALCULATION_RECORD)
            db.execSQL(CREATE_CALCULATION_RECORD_LINE)
            CREATE_INDICES.forEach(db::execSQL)
        }
    }

private const val CREATE_CALCULATION_RECORD = """
    CREATE TABLE IF NOT EXISTS `calculation_record` (
        `id` TEXT NOT NULL,
        `recipe_version_id` TEXT NOT NULL,
        `servings` REAL NOT NULL,
        `engine_version` TEXT NOT NULL,
        `payload_hash` TEXT NOT NULL,
        `status` TEXT NOT NULL,
        `created_at` INTEGER NOT NULL,
        `confirmed_at` INTEGER,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`recipe_version_id`) REFERENCES `recipe_version`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val CREATE_CALCULATION_RECORD_LINE = """
    CREATE TABLE IF NOT EXISTS `calculation_record_line` (
        `id` TEXT NOT NULL,
        `calculation_record_id` TEXT NOT NULL,
        `line_no` INTEGER NOT NULL,
        `ingredient_id` TEXT NOT NULL,
        `quantity` REAL NOT NULL,
        `unit_id` TEXT NOT NULL,
        `is_optional` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`calculation_record_id`) REFERENCES `calculation_record`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`ingredient_id`) REFERENCES `ingredient`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`unit_id`) REFERENCES `unit`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private val CREATE_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_recipe_version_id` " +
            "ON `calculation_record` (`recipe_version_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_calculation_record_payload_hash` " +
            "ON `calculation_record` (`payload_hash`)",
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_recipe_version_id_created_at` " +
            "ON `calculation_record` (`recipe_version_id`, `created_at`)",
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_status` " +
            "ON `calculation_record` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_line_calculation_record_id` " +
            "ON `calculation_record_line` (`calculation_record_id`)",
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_line_ingredient_id` " +
            "ON `calculation_record_line` (`ingredient_id`)",
        "CREATE INDEX IF NOT EXISTS `index_calculation_record_line_unit_id` " +
            "ON `calculation_record_line` (`unit_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS " +
            "`index_calculation_record_line_calculation_record_id_line_no` " +
            "ON `calculation_record_line` (`calculation_record_id`, `line_no`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS " +
            "`index_calculation_record_line_calculation_record_id_ingredient_id_unit_id` " +
            "ON `calculation_record_line` (`calculation_record_id`, `ingredient_id`, `unit_id`)",
    )
