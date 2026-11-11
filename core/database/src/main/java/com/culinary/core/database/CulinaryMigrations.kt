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
 * v7 → v8：酱汁/菜谱版本增加最小制作范围数值与单位；只加可空列，历史数据保持 null。
 * v8 → v9：库存档案/档案-商品关联、批次生产日期与冻结/回收/临时标记、冻结与库位转移记录、
 * 出库单与明细、打印任务、盘点单与明细；历史批次/流水按商品回填档案归属，不丢旧数据。
 */
internal object CulinaryMigrations {
    val MIGRATION_1_2: Migration = MIGRATION_1_2_IMPL

    val MIGRATION_2_3: Migration = MIGRATION_2_3_IMPL

    val MIGRATION_3_4: Migration = MIGRATION_3_4_IMPL

    val MIGRATION_4_5: Migration = MIGRATION_4_5_IMPL

    val MIGRATION_5_6: Migration = MIGRATION_5_6_IMPL
    val MIGRATION_6_7: Migration = MIGRATION_6_7_IMPL
    val MIGRATION_7_8: Migration = MIGRATION_7_8_IMPL
    val MIGRATION_8_9: Migration = MIGRATION_8_9_IMPL

    val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        )
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

/**
 * v7：菜谱步骤结构化。只加可空列，不重建表、不改既有数据；
 * 读取端在 `steps_json` 为空时回退 `instructions_md`，历史菜谱步骤不丢失。
 */
private val MIGRATION_6_7_IMPL =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CulinarySchemaExtras.dropPartialIndexes(db)
            db.execSQL("ALTER TABLE `recipe_version` ADD COLUMN `steps_json` TEXT")
        }
    }

/**
 * v8：最小制作范围。只加可空列，不重建表、不改既有产量/难度/步骤数据；
 * 旧版本两列保持 null，UI 显示为未设置。
 */
private val MIGRATION_7_8_IMPL =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CulinarySchemaExtras.dropPartialIndexes(db)
            db.execSQL("ALTER TABLE `sauce_version` ADD COLUMN `min_range_qty` REAL")
            db.execSQL("ALTER TABLE `sauce_version` ADD COLUMN `min_range_unit_id` TEXT")
            db.execSQL("ALTER TABLE `recipe_version` ADD COLUMN `min_range_qty` REAL")
            db.execSQL("ALTER TABLE `recipe_version` ADD COLUMN `min_range_unit_id` TEXT")
            db.execSQL("ALTER TABLE `recipe` ADD COLUMN `notes` TEXT")
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

// ---------------------------------------------------------------------------
// v8 → v9：库存档案化（MB-INV 任务书 §22）
// ---------------------------------------------------------------------------

private val MIGRATION_8_9_IMPL =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Room 只校验表/列/索引/外键，不校验触发器；部分索引必须先移除，
            // 由 onOpen 的 CulinarySchemaExtras.apply 按原定义重建。
            CulinarySchemaExtras.dropPartialIndexes(db)

            // product_id 在 v9 变为可空（酱汁现货批次没有采购商品），
            // SQLite 无法直接放宽 NOT NULL，只能重建表；defer_foreign_keys
            // 让"建新表→搬数据→删旧表→改名"在提交时一次性校验外键。
            db.execSQL("PRAGMA defer_foreign_keys = 1")
            db.execSQL(CREATE_INVENTORY_BATCH_V9)
            db.execSQL(COPY_INVENTORY_BATCH_V9)
            db.execSQL(CREATE_INVENTORY_LEDGER_V9)
            db.execSQL(COPY_INVENTORY_LEDGER_V9)
            db.execSQL("DROP TABLE `inventory_ledger`")
            db.execSQL("DROP TABLE `inventory_batch`")
            db.execSQL("ALTER TABLE `inventory_batch_v9` RENAME TO `inventory_batch`")
            db.execSQL("ALTER TABLE `inventory_ledger_v9` RENAME TO `inventory_ledger`")
            V9_BATCH_LEDGER_INDICES.forEach(db::execSQL)

            db.execSQL(CREATE_INVENTORY_ARCHIVE)
            db.execSQL(CREATE_INVENTORY_ARCHIVE_PRODUCT)
            db.execSQL(CREATE_INVENTORY_FREEZE_RECORD)
            db.execSQL(CREATE_INVENTORY_LOCATION_TRANSFER)
            db.execSQL(CREATE_INVENTORY_ISSUE_SLIP)
            db.execSQL(CREATE_INVENTORY_ISSUE_SLIP_LINE)
            db.execSQL(CREATE_INVENTORY_PRINT_JOB)
            db.execSQL(CREATE_INVENTORY_STOCKTAKE)
            db.execSQL(CREATE_INVENTORY_STOCKTAKE_LINE)
            V9_INDICES.forEach(db::execSQL)
            V9_ARCHIVE_BACKFILL.forEach(db::execSQL)

            // CHECK 与 append-only 触发器随旧表删除；onOpen 的 CulinarySchemaExtras.apply
            // 会用新谓词重建，旧状态约束不会再拦住 FROZEN/RECYCLED。
        }
    }

private const val CREATE_INVENTORY_ARCHIVE = """
    CREATE TABLE IF NOT EXISTS `inventory_archive` (
        `id` TEXT NOT NULL,
        `name` TEXT NOT NULL,
        `normalized_name` TEXT NOT NULL,
        `english_name` TEXT,
        `category_id` TEXT,
        `archive_kind` TEXT NOT NULL,
        `sauce_id` TEXT,
        `stock_unit_id` TEXT NOT NULL,
        `minimum_stock` REAL,
        `target_stock` REAL,
        `image_uri` TEXT,
        `storage_method` TEXT,
        `storage_temperature` TEXT,
        `default_location_id` TEXT,
        `description` TEXT,
        `status` TEXT NOT NULL,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `deleted_at` INTEGER,
        `revision` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`category_id`) REFERENCES `archive_category`(`id`)
            ON UPDATE NO ACTION ON DELETE SET NULL,
        FOREIGN KEY(`sauce_id`) REFERENCES `sauce`(`id`)
            ON UPDATE NO ACTION ON DELETE SET NULL,
        FOREIGN KEY(`stock_unit_id`) REFERENCES `unit`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`default_location_id`) REFERENCES `storage_location`(`id`)
            ON UPDATE NO ACTION ON DELETE SET NULL
    )
"""

private const val CREATE_INVENTORY_ARCHIVE_PRODUCT = """
    CREATE TABLE IF NOT EXISTS `inventory_archive_product` (
        `id` TEXT NOT NULL,
        `archive_id` TEXT NOT NULL,
        `product_id` TEXT NOT NULL,
        `conversion_ratio` REAL NOT NULL,
        `is_default` INTEGER NOT NULL,
        `created_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`archive_id`) REFERENCES `inventory_archive`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`product_id`) REFERENCES `product`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val CREATE_INVENTORY_FREEZE_RECORD = """
    CREATE TABLE IF NOT EXISTS `inventory_freeze_record` (
        `id` TEXT NOT NULL,
        `batch_id` TEXT NOT NULL,
        `action` TEXT NOT NULL,
        `reason` TEXT NOT NULL,
        `operator` TEXT,
        `occurred_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`batch_id`) REFERENCES `inventory_batch`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val CREATE_INVENTORY_LOCATION_TRANSFER = """
    CREATE TABLE IF NOT EXISTS `inventory_location_transfer` (
        `id` TEXT NOT NULL,
        `batch_id` TEXT NOT NULL,
        `from_location_id` TEXT NOT NULL,
        `to_location_id` TEXT NOT NULL,
        `quantity` REAL NOT NULL,
        `note` TEXT,
        `occurred_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`batch_id`) REFERENCES `inventory_batch`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`from_location_id`) REFERENCES `storage_location`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`to_location_id`) REFERENCES `storage_location`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val CREATE_INVENTORY_ISSUE_SLIP = """
    CREATE TABLE IF NOT EXISTS `inventory_issue_slip` (
        `id` TEXT NOT NULL,
        `slip_no` TEXT NOT NULL,
        `type` TEXT NOT NULL,
        `status` TEXT NOT NULL,
        `recipe_version_id` TEXT,
        `recipe_name` TEXT,
        `total_lines` INTEGER NOT NULL,
        `idempotency_key` TEXT NOT NULL,
        `note` TEXT,
        `operator` TEXT,
        `occurred_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`)
    )
"""

private const val CREATE_INVENTORY_ISSUE_SLIP_LINE = """
    CREATE TABLE IF NOT EXISTS `inventory_issue_slip_line` (
        `id` TEXT NOT NULL,
        `slip_id` TEXT NOT NULL,
        `line_no` INTEGER NOT NULL,
        `archive_id` TEXT NOT NULL,
        `archive_name` TEXT NOT NULL,
        `batch_id` TEXT NOT NULL,
        `batch_code` TEXT,
        `location_id` TEXT NOT NULL,
        `location_name` TEXT,
        `quantity` REAL NOT NULL,
        `unit_id` TEXT NOT NULL,
        `unit_symbol` TEXT NOT NULL,
        `role` TEXT NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`slip_id`) REFERENCES `inventory_issue_slip`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
"""

private const val CREATE_INVENTORY_PRINT_JOB = """
    CREATE TABLE IF NOT EXISTS `inventory_print_job` (
        `id` TEXT NOT NULL,
        `kind` TEXT NOT NULL,
        `ref_type` TEXT NOT NULL,
        `ref_id` TEXT NOT NULL,
        `status` TEXT NOT NULL,
        `attempts` INTEGER NOT NULL,
        `reprint_count` INTEGER NOT NULL,
        `last_error` TEXT,
        `paper_width_mm` INTEGER NOT NULL,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `printed_at` INTEGER,
        PRIMARY KEY(`id`)
    )
"""

private const val CREATE_INVENTORY_STOCKTAKE = """
    CREATE TABLE IF NOT EXISTS `inventory_stocktake` (
        `id` TEXT NOT NULL,
        `scope_type` TEXT NOT NULL,
        `scope_ref` TEXT,
        `scope_label` TEXT,
        `status` TEXT NOT NULL,
        `note` TEXT,
        `line_count` INTEGER NOT NULL,
        `diff_count` INTEGER NOT NULL,
        `created_at` INTEGER NOT NULL,
        `confirmed_at` INTEGER,
        PRIMARY KEY(`id`)
    )
"""

private const val CREATE_INVENTORY_STOCKTAKE_LINE = """
    CREATE TABLE IF NOT EXISTS `inventory_stocktake_line` (
        `id` TEXT NOT NULL,
        `stocktake_id` TEXT NOT NULL,
        `line_no` INTEGER NOT NULL,
        `archive_id` TEXT NOT NULL,
        `archive_name` TEXT NOT NULL,
        `batch_id` TEXT NOT NULL,
        `batch_code` TEXT,
        `location_name` TEXT,
        `book_qty` REAL NOT NULL,
        `counted_qty` REAL NOT NULL,
        `unit_id` TEXT NOT NULL,
        `unit_symbol` TEXT NOT NULL,
        `frozen` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`stocktake_id`) REFERENCES `inventory_stocktake`(`id`)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
"""

private val V9_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_category_id` ON `inventory_archive` (`category_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_archive_kind` ON `inventory_archive` (`archive_kind`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_status` ON `inventory_archive` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_sauce_id` ON `inventory_archive` (`sauce_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_stock_unit_id` ON `inventory_archive` (`stock_unit_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_default_location_id` " +
            "ON `inventory_archive` (`default_location_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_normalized_name` " +
            "ON `inventory_archive` (`normalized_name`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_archive_product_archive_id_product_id` " +
            "ON `inventory_archive_product` (`archive_id`, `product_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_archive_product_product_id` " +
            "ON `inventory_archive_product` (`product_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_freeze_record_batch_id_occurred_at` " +
            "ON `inventory_freeze_record` (`batch_id`, `occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_freeze_record_action` " +
            "ON `inventory_freeze_record` (`action`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_location_transfer_batch_id_occurred_at` " +
            "ON `inventory_location_transfer` (`batch_id`, `occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_location_transfer_from_location_id` " +
            "ON `inventory_location_transfer` (`from_location_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_location_transfer_to_location_id` " +
            "ON `inventory_location_transfer` (`to_location_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_issue_slip_slip_no` " +
            "ON `inventory_issue_slip` (`slip_no`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_issue_slip_idempotency_key` " +
            "ON `inventory_issue_slip` (`idempotency_key`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_issue_slip_occurred_at` " +
            "ON `inventory_issue_slip` (`occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_issue_slip_recipe_version_id` " +
            "ON `inventory_issue_slip` (`recipe_version_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_issue_slip_line_slip_id_line_no` " +
            "ON `inventory_issue_slip_line` (`slip_id`, `line_no`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_issue_slip_line_batch_id` " +
            "ON `inventory_issue_slip_line` (`batch_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_issue_slip_line_archive_id` " +
            "ON `inventory_issue_slip_line` (`archive_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_print_job_ref_type_ref_id` " +
            "ON `inventory_print_job` (`ref_type`, `ref_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_print_job_status` " +
            "ON `inventory_print_job` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_print_job_kind` " +
            "ON `inventory_print_job` (`kind`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_stocktake_status` " +
            "ON `inventory_stocktake` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_stocktake_created_at` " +
            "ON `inventory_stocktake` (`created_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_stocktake_line_stocktake_id_line_no` " +
            "ON `inventory_stocktake_line` (`stocktake_id`, `line_no`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_stocktake_line_batch_id` " +
            "ON `inventory_stocktake_line` (`batch_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_stocktake_line_archive_id` " +
            "ON `inventory_stocktake_line` (`archive_id`)",
    )


private const val CREATE_INVENTORY_BATCH_V9 = """
    CREATE TABLE IF NOT EXISTS `inventory_batch_v9` (
        `id` TEXT NOT NULL,
        `product_id` TEXT,
        `archive_id` TEXT,
        `location_id` TEXT NOT NULL,
        `batch_code` TEXT,
        `received_at` INTEGER NOT NULL,
        `production_date` TEXT,
        `expiry_date` TEXT,
        `quantity_on_hand` REAL NOT NULL,
        `quantity_reserved` REAL NOT NULL,
        `stock_unit_id` TEXT NOT NULL,
        `unit_cost_minor` INTEGER,
        `currency_code` TEXT NOT NULL,
        `source_type` TEXT,
        `source_id` TEXT,
        `status` TEXT NOT NULL,
        `frozen_at` INTEGER,
        `frozen_reason` TEXT,
        `recycled_at` INTEGER,
        `is_temporary` INTEGER NOT NULL DEFAULT 0,
        `temporary_since` INTEGER,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `revision` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`product_id`) REFERENCES `product`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`location_id`) REFERENCES `storage_location`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`stock_unit_id`) REFERENCES `unit`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val COPY_INVENTORY_BATCH_V9 = """
    INSERT INTO `inventory_batch_v9` (
        `id`, `product_id`, `archive_id`, `location_id`, `batch_code`, `received_at`,
        `production_date`, `expiry_date`, `quantity_on_hand`, `quantity_reserved`,
        `stock_unit_id`, `unit_cost_minor`, `currency_code`, `source_type`, `source_id`,
        `status`, `frozen_at`, `frozen_reason`, `recycled_at`, `is_temporary`,
        `temporary_since`, `created_at`, `updated_at`, `revision`)
    SELECT
        `id`, `product_id`, 'arch_' || `product_id`, `location_id`, `batch_code`,
        `received_at`, NULL, `expiry_date`, `quantity_on_hand`, `quantity_reserved`,
        `stock_unit_id`, `unit_cost_minor`, `currency_code`, `source_type`, `source_id`,
        `status`, NULL, NULL, NULL, 0, NULL, `created_at`, `updated_at`, `revision`
    FROM `inventory_batch`
"""

private const val CREATE_INVENTORY_LEDGER_V9 = """
    CREATE TABLE IF NOT EXISTS `inventory_ledger_v9` (
        `id` TEXT NOT NULL,
        `batch_id` TEXT NOT NULL,
        `product_id` TEXT,
        `archive_id` TEXT,
        `location_id` TEXT NOT NULL,
        `type` TEXT NOT NULL,
        `on_hand_delta` REAL NOT NULL,
        `reserved_delta` REAL NOT NULL,
        `stock_unit_id` TEXT NOT NULL,
        `on_hand_after` REAL NOT NULL,
        `reserved_after` REAL NOT NULL,
        `from_location_id` TEXT,
        `to_location_id` TEXT,
        `reference_type` TEXT,
        `reference_id` TEXT,
        `idempotency_key` TEXT NOT NULL,
        `note` TEXT,
        `operator` TEXT,
        `print_job_id` TEXT,
        `occurred_at` INTEGER NOT NULL,
        `created_at` INTEGER NOT NULL,
        PRIMARY KEY(`id`),
        FOREIGN KEY(`batch_id`) REFERENCES `inventory_batch`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`product_id`) REFERENCES `product`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`location_id`) REFERENCES `storage_location`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT,
        FOREIGN KEY(`stock_unit_id`) REFERENCES `unit`(`id`)
            ON UPDATE NO ACTION ON DELETE RESTRICT
    )
"""

private const val COPY_INVENTORY_LEDGER_V9 = """
    INSERT INTO `inventory_ledger_v9` (
        `id`, `batch_id`, `product_id`, `archive_id`, `location_id`, `type`,
        `on_hand_delta`, `reserved_delta`, `stock_unit_id`, `on_hand_after`,
        `reserved_after`, `from_location_id`, `to_location_id`, `reference_type`,
        `reference_id`, `idempotency_key`, `note`, `operator`, `print_job_id`,
        `occurred_at`, `created_at`)
    SELECT
        `id`, `batch_id`, `product_id`, 'arch_' || `product_id`, `location_id`, `type`,
        `on_hand_delta`, `reserved_delta`, `stock_unit_id`, `on_hand_after`,
        `reserved_after`, `from_location_id`, `to_location_id`, `reference_type`,
        `reference_id`, `idempotency_key`, `note`, NULL, NULL,
        `occurred_at`, `created_at`
    FROM `inventory_ledger`
"""

private val V9_BATCH_LEDGER_INDICES =
    listOf(
        "CREATE INDEX IF NOT EXISTS `index_inventory_batch_product_id_status` " +
            "ON `inventory_batch` (`product_id`, `status`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_batch_expiry_date` " +
            "ON `inventory_batch` (`expiry_date`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_batch_location_id` " +
            "ON `inventory_batch` (`location_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_batch_stock_unit_id` " +
            "ON `inventory_batch` (`stock_unit_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_ledger_batch_id_occurred_at` " +
            "ON `inventory_ledger` (`batch_id`, `occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_ledger_product_id_occurred_at` " +
            "ON `inventory_ledger` (`product_id`, `occurred_at`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_ledger_reference_type_reference_id` " +
            "ON `inventory_ledger` (`reference_type`, `reference_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_ledger_location_id` " +
            "ON `inventory_ledger` (`location_id`)",
        "CREATE INDEX IF NOT EXISTS `index_inventory_ledger_stock_unit_id` " +
            "ON `inventory_ledger` (`stock_unit_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_ledger_idempotency_key` " +
            "ON `inventory_ledger` (`idempotency_key`)",
    )

/**
 * 回填：每个采购商品生成一个同名库存档案（`arch_<product_id>`），
 * 旧批次与旧流水已在重建表时按商品挂到档案上。旧数据一行不丢。
 */
private val V9_ARCHIVE_BACKFILL =
    listOf(
        "INSERT OR IGNORE INTO `inventory_archive` (" +
            "`id`, `name`, `normalized_name`, `archive_kind`, `stock_unit_id`, " +
            "`default_location_id`, `status`, `created_at`, `updated_at`, `revision`) " +
            "SELECT 'arch_' || `id`, `name`, `normalized_name`, 'INGREDIENT', `stock_unit_id`, " +
            "`default_location_id`, 'ACTIVE', `created_at`, `updated_at`, 1 FROM `product`",
        "INSERT OR IGNORE INTO `inventory_archive_product` (" +
            "`id`, `archive_id`, `product_id`, `conversion_ratio`, `is_default`, `created_at`) " +
            "SELECT 'ap_' || `id`, 'arch_' || `id`, `id`, 1.0, 1, `created_at` FROM `product`",
    )
