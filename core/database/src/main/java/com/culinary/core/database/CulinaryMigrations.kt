package com.culinary.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.culinary.core.database.schema.CulinarySchemaExtras

/**
 * 数据库迁移。
 *
 * v1 → v2：新增计算记录，并为 recipe_version 增加 servings。
 * v2 → v3：只新增 kitchen_reference（烹饪技术 / 制作标准 / 储存标准），
 * 不改既有表、不移动既有数据。
 * v3 → v4：补充六库档案扩展字段、测试种子标记与档案关系表；旧行全部保持 null。
 * v4 → v5：酱汁编辑器所需的描述、难度、图注、步骤 JSON、原料名称覆盖与关系排序；
 * 只增加可空列或带默认值的列，不移动/重写既有数据。
 * v5 → v6：六库分类树/档案分类归属与参考资料专业内容行；新增表，不改旧表数据。
 */
internal object CulinaryMigrations {
    val MIGRATION_1_2: Migration = MIGRATION_1_2_IMPL

    val MIGRATION_2_3: Migration = MIGRATION_2_3_IMPL

    val MIGRATION_3_4: Migration = MIGRATION_3_4_IMPL

    val MIGRATION_4_5: Migration = MIGRATION_4_5_IMPL

    val MIGRATION_5_6: Migration = MIGRATION_5_6_IMPL

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

private val MIGRATION_2_3_IMPL =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v2 运行时同样会创建不在 Room 导出 schema 中的局部索引；
            // 迁移校验前移除，onOpen 会按原定义重建。
            CulinarySchemaExtras.dropPartialIndexes(db)
            db.execSQL(CREATE_KITCHEN_REFERENCE)
            CREATE_KITCHEN_REFERENCE_INDICES.forEach(db::execSQL)
        }
    }

private val MIGRATION_3_4_IMPL =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v3 打开时同样会重建运行时局部索引；Room v4 校验前必须先移除。
            CulinarySchemaExtras.dropPartialIndexes(db)
            SAUCE_V4_COLUMNS.forEach { db.execSQL("ALTER TABLE `sauce` ADD COLUMN $it") }
            INGREDIENT_V4_COLUMNS.forEach { db.execSQL("ALTER TABLE `ingredient` ADD COLUMN $it") }
            RECIPE_V4_COLUMNS.forEach { db.execSQL("ALTER TABLE `recipe` ADD COLUMN $it") }
            REFERENCE_V4_COLUMNS.forEach { db.execSQL("ALTER TABLE `kitchen_reference` ADD COLUMN $it") }
            V4_UNIQUE_SEED_INDICES.forEach(db::execSQL)
            db.execSQL(CREATE_KITCHEN_RELATION)
            CREATE_KITCHEN_RELATION_INDICES.forEach(db::execSQL)
        }
    }

private val MIGRATION_4_5_IMPL =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v4 连接打开时也会重建运行时局部索引；迁移校验前先移除。
            CulinarySchemaExtras.dropPartialIndexes(db)
            SAUCE_V5_COLUMNS.forEach { db.execSQL("ALTER TABLE `sauce` ADD COLUMN $it") }
            db.execSQL("ALTER TABLE `sauce_version` ADD COLUMN `steps_json` TEXT")
            SAUCE_COMPONENT_V5_COLUMNS.forEach {
                db.execSQL("ALTER TABLE `sauce_component` ADD COLUMN $it")
            }
            db.execSQL(
                "ALTER TABLE `kitchen_relation` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

private val MIGRATION_5_6_IMPL =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CulinarySchemaExtras.dropPartialIndexes(db)
            db.execSQL(CREATE_ARCHIVE_CATEGORY)
            db.execSQL(CREATE_ARCHIVE_CATEGORY_ASSIGNMENT)
            db.execSQL(CREATE_KITCHEN_REFERENCE_CONTENT)
            CREATE_ARCHIVE_CATEGORY_INDICES.forEach(db::execSQL)
            CREATE_ARCHIVE_CATEGORY_ASSIGNMENT_INDICES.forEach(db::execSQL)
            CREATE_KITCHEN_REFERENCE_CONTENT_INDICES.forEach(db::execSQL)
        }
    }

private const val CREATE_ARCHIVE_CATEGORY = """
    CREATE TABLE IF NOT EXISTS `archive_category` (
        `id` TEXT NOT NULL,
        `library` TEXT NOT NULL,
        `parent_id` TEXT,
        `name` TEXT NOT NULL,
        `normalized_name` TEXT NOT NULL,
        `english_name` TEXT,
        `sort_order` INTEGER NOT NULL,
        `status` TEXT NOT NULL,
        `test_batch` TEXT,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `revision` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`parent_id`) REFERENCES `archive_category`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private val CREATE_ARCHIVE_CATEGORY_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_archive_category_library_parent_id_sort_order` " +
            "ON `archive_category` (`library`, `parent_id`, `sort_order`)",
        "CREATE INDEX IF NOT EXISTS `index_archive_category_test_batch` " +
            "ON `archive_category` (`test_batch`)",
    )

private const val CREATE_ARCHIVE_CATEGORY_ASSIGNMENT = """
    CREATE TABLE IF NOT EXISTS `archive_category_assignment` (
        `id` TEXT NOT NULL,
        `record_kind` TEXT NOT NULL,
        `record_id` TEXT NOT NULL,
        `category_id` TEXT NOT NULL,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`category_id`) REFERENCES `archive_category`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private val CREATE_ARCHIVE_CATEGORY_ASSIGNMENT_INDICES =
    listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_archive_category_assignment_record_kind_record_id` " +
            "ON `archive_category_assignment` (`record_kind`, `record_id`)",
        "CREATE INDEX IF NOT EXISTS `index_archive_category_assignment_category_id` " +
            "ON `archive_category_assignment` (`category_id`)",
    )

private const val CREATE_KITCHEN_REFERENCE_CONTENT = """
    CREATE TABLE IF NOT EXISTS `kitchen_reference_content` (
        `id` TEXT NOT NULL,
        `reference_id` TEXT NOT NULL,
        `section` TEXT NOT NULL,
        `label` TEXT,
        `value` TEXT NOT NULL,
        `sort_order` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`reference_id`) REFERENCES `kitchen_reference`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
"""

private val CREATE_KITCHEN_REFERENCE_CONTENT_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_kitchen_reference_content_reference_id_section_sort_order` " +
            "ON `kitchen_reference_content` (`reference_id`, `section`, `sort_order`)",
        "CREATE INDEX IF NOT EXISTS `index_kitchen_reference_content_reference_id` " +
            "ON `kitchen_reference_content` (`reference_id`)",
    )

private val SAUCE_V5_COLUMNS =
    listOf(
        "`description` TEXT",
        "`difficulty` TEXT",
        "`hero_caption` TEXT",
    )

private val SAUCE_COMPONENT_V5_COLUMNS =
    listOf(
        "`label_override` TEXT",
        "`label_override_en` TEXT",
    )

private val SAUCE_V4_COLUMNS =
    listOf(
        "`english_name` TEXT",
        "`main_ingredients` TEXT",
        "`preparation_summary` TEXT",
        "`key_parameters` TEXT",
        "`adjustable_notes` TEXT",
        "`seed_note` TEXT",
        "`seed_id` TEXT",
        "`seed_version` TEXT",
    )

private val INGREDIENT_V4_COLUMNS =
    listOf(
        "`english_name` TEXT",
        "`example_products` TEXT",
        "`seed_note` TEXT",
        "`seed_id` TEXT",
        "`seed_version` TEXT",
    )

private val RECIPE_V4_COLUMNS =
    listOf(
        "`english_name` TEXT",
        "`allergen_notes` TEXT",
        "`seed_note` TEXT",
        "`seed_id` TEXT",
        "`seed_version` TEXT",
    )

private val REFERENCE_V4_COLUMNS =
    listOf(
        "`english_name` TEXT",
        "`seed_note` TEXT",
        "`seed_id` TEXT",
        "`seed_version` TEXT",
    )

private val V4_UNIQUE_SEED_INDICES =
    listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sauce_seed_id` ON `sauce` (`seed_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_ingredient_seed_id` ON `ingredient` (`seed_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipe_seed_id` ON `recipe` (`seed_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_kitchen_reference_seed_id` " +
            "ON `kitchen_reference` (`seed_id`)",
    )

private const val CREATE_KITCHEN_RELATION = """
    CREATE TABLE IF NOT EXISTS `kitchen_relation` (
        `id` TEXT NOT NULL,
        `source_record_id` TEXT NOT NULL,
        `source_kind` TEXT NOT NULL,
        `target_record_id` TEXT NOT NULL,
        `target_kind` TEXT NOT NULL,
        `target_label` TEXT NOT NULL,
        `relation_label` TEXT NOT NULL,
        `seed_version` TEXT,
        `created_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
    )
"""

private val CREATE_KITCHEN_RELATION_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_kitchen_relation_source_record_id` " +
            "ON `kitchen_relation` (`source_record_id`)",
        "CREATE INDEX IF NOT EXISTS `index_kitchen_relation_target_record_id` " +
            "ON `kitchen_relation` (`target_record_id`)",
        "CREATE INDEX IF NOT EXISTS `index_kitchen_relation_seed_version` " +
            "ON `kitchen_relation` (`seed_version`)",
    )

private const val CREATE_KITCHEN_REFERENCE = """
    CREATE TABLE IF NOT EXISTS `kitchen_reference` (
        `id` TEXT NOT NULL,
        `kind` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `normalized_name` TEXT NOT NULL,
        `category` TEXT,
        `description` TEXT,
        `status` TEXT NOT NULL,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `deleted_at` INTEGER,
        `revision` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
    )
"""

private val CREATE_KITCHEN_REFERENCE_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_kitchen_reference_kind` " +
            "ON `kitchen_reference` (`kind`)",
        "CREATE INDEX IF NOT EXISTS `index_kitchen_reference_status` " +
            "ON `kitchen_reference` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_kitchen_reference_kind_normalized_name` " +
            "ON `kitchen_reference` (`kind`, `normalized_name`)",
    )

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
