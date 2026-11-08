package com.culinary.core.database.schema

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 无法表达、但 A06 要求的 schema 附加项：
 *
 * 1. `CHECK` 约束由 BEFORE INSERT/UPDATE 触发器等价实现（Room 只生成列与索引）；
 * 2. `WHERE deleted_at IS NULL` 的部分唯一索引（名称复用）；
 * 3. "每个聚合至多一个当前版本"的部分唯一索引；
 * 4. 已发布版本内容不可修改的触发器（应用层校验之外的第二道保护）。
 *
 * 全部语句都带 `IF NOT EXISTS`，可安全地在每次打开时重放。
 */
internal object CulinarySchemaExtras {
    /**
     * v1 运行时由本对象创建、但不属于 Room 导出 schema 的部分索引。
     *
     * Room 在迁移后会逐表比对索引集合；这些"额外索引"会让 v1→v2 校验失败。
     * 迁移先删除它们，`onOpen` 再按原定义重建，索引定义与语义不变。
     */
    fun dropPartialIndexes(db: SupportSQLiteDatabase) {
        PARTIAL_INDEX_NAMES.forEach { name -> db.execSQL("DROP INDEX IF EXISTS `$name`") }
    }

    fun apply(db: SupportSQLiteDatabase) {
        PARTIAL_INDEXES.forEach(db::execSQL)

        // v2 的 recipe_version 约束新增 servings；旧库可能已有 v1 触发器，
        // 必须先删除再重建，否则 IF NOT EXISTS 会保留旧定义。
        dropRecipeVersionTriggers(db)

        installChecks(db, "unit", UNIT_PREDICATE)
        installChecks(db, "ingredient", INGREDIENT_PREDICATE)
        installChecks(db, "ingredient_unit_conversion", CONVERSION_PREDICATE)
        installChecks(db, "sauce", SAUCE_PREDICATE)
        installChecks(db, "recipe", RECIPE_PREDICATE)
        installChecks(db, "sauce_version", VERSION_PREDICATE)
        installChecks(db, "recipe_version", RECIPE_VERSION_PREDICATE)
        installChecks(db, "sauce_component", COMPONENT_PREDICATE)
        installChecks(db, "recipe_component", COMPONENT_PREDICATE)
        installChecks(db, "calculation_record", CALCULATION_RECORD_PREDICATE)
        installChecks(db, "calculation_record_line", CALCULATION_RECORD_LINE_PREDICATE)
        installChecks(db, "supplier", SUPPLIER_PREDICATE)
        installChecks(db, "product", PRODUCT_PREDICATE)
        installChecks(db, "barcode", BARCODE_PREDICATE)
        installChecks(db, "storage_location", LOCATION_PREDICATE)
        installChecks(db, "inventory_batch", BATCH_PREDICATE)
        installChecks(db, "inventory_ledger", LEDGER_PREDICATE)
        installChecks(db, "production_plan", PLAN_PREDICATE)
        installChecks(db, "production_plan_item", PLAN_ITEM_PREDICATE)
        installChecks(db, "production_plan_snapshot", SNAPSHOT_PREDICATE)
        installChecks(db, "material_requirement", REQUIREMENT_PREDICATE)
        installChecks(db, "material_requirement_source", REQUIREMENT_SOURCE_PREDICATE)
        installChecks(db, "inventory_reservation", RESERVATION_PREDICATE)
        installChecks(db, "procurement_requirement", PROCUREMENT_PREDICATE)
        installChecks(db, "purchase_order", PURCHASE_ORDER_PREDICATE)
        installChecks(db, "purchase_order_item", PURCHASE_ORDER_ITEM_PREDICATE)
        installChecks(db, "goods_receipt", RECEIPT_PREDICATE)
        installChecks(db, "goods_receipt_item", RECEIPT_ITEM_PREDICATE)
        installChecks(db, "image_asset", IMAGE_PREDICATE)
        installChecks(db, "change_log", CHANGE_LOG_PREDICATE)
        installChecks(db, "backup_record", BACKUP_PREDICATE)
        installChecks(db, "app_metadata", APP_METADATA_PREDICATE)

        installPublishedImmutability(db, "sauce_version", "sauce_id")
        installPublishedImmutability(
            db = db,
            table = "recipe_version",
            ownerColumn = "recipe_id",
            extraContentChanges = listOf("NEW.servings <> OLD.servings"),
        )
        installAppendOnly(db, "inventory_ledger")
        installAppendOnly(db, "production_plan_snapshot")
        installAppendOnly(db, "change_log")
        installCalculationRecordImmutability(db)
        installCalculationRecordLineGuard(db)
        installPlanDeletionGuard(db)
    }

    /** 删除 v1 的 recipe_version 触发器，使新增的 servings 约束可以生效。 */
    private fun dropRecipeVersionTriggers(db: SupportSQLiteDatabase) {
        listOf(
            "trg_recipe_version_check_insert",
            "trg_recipe_version_check_update",
            "trg_recipe_version_published_immutable",
        ).forEach { trigger -> db.execSQL("DROP TRIGGER IF EXISTS `$trigger`") }
    }

    /**
     * 计算记录确认后不可变：任何 UPDATE 都拒绝；已确认记录也不允许删除。
     * `PREVIEW → CONFIRMED` 的首次确认发生在 OLD.status 仍为 PREVIEW 时，可以写入。
     */
    private fun installCalculationRecordImmutability(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_calculation_record_confirmed_immutable " +
                "BEFORE UPDATE ON calculation_record " +
                "WHEN OLD.status = 'CONFIRMED' " +
                "BEGIN SELECT RAISE(ABORT, 'confirmed calculation record is immutable'); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_calculation_record_confirmed_no_delete " +
                "BEFORE DELETE ON calculation_record " +
                "WHEN OLD.status = 'CONFIRMED' " +
                "BEGIN SELECT RAISE(ABORT, 'confirmed calculation record cannot be deleted'); END",
        )
    }

    /**
     * 需求行不可变：已确认记录的行不允许新增/修改/删除。
     * 记录头被删除时由外键 CASCADE 清理行；已确认记录本身已被禁止删除。
     */
    private fun installCalculationRecordLineGuard(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_calculation_record_line_no_insert " +
                "BEFORE INSERT ON calculation_record_line " +
                "WHEN (SELECT status FROM calculation_record " +
                "WHERE id = NEW.calculation_record_id) = 'CONFIRMED' " +
                "BEGIN SELECT RAISE(ABORT, 'confirmed calculation record lines are immutable'); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_calculation_record_line_no_update " +
                "BEFORE UPDATE ON calculation_record_line " +
                "WHEN (SELECT status FROM calculation_record " +
                "WHERE id = OLD.calculation_record_id) = 'CONFIRMED' " +
                "OR (SELECT status FROM calculation_record " +
                "WHERE id = NEW.calculation_record_id) = 'CONFIRMED' " +
                "BEGIN SELECT RAISE(ABORT, 'confirmed calculation record lines are immutable'); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_calculation_record_line_no_delete " +
                "BEFORE DELETE ON calculation_record_line " +
                "WHEN (SELECT status FROM calculation_record " +
                "WHERE id = OLD.calculation_record_id) = 'CONFIRMED' " +
                "BEGIN SELECT RAISE(ABORT, 'confirmed calculation record lines are immutable'); END",
        )
    }

    /**
     * 计划删除守卫：只有草稿计划允许删除（A04 §"删除草稿 只删草稿"）。
     *
     * 已确认/进行中/已完成/已取消的计划只能保留或取消，不能删除；这样历史快照
     * 不会因为计划被删而丢失。快照本身另有 append-only 触发器，即使级联到快照
     * 也会中止，形成第二道保护。
     */
    private fun installPlanDeletionGuard(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_production_plan_delete_guard " +
                "BEFORE DELETE ON production_plan " +
                "WHEN OLD.status <> 'DRAFT' " +
                "BEGIN SELECT RAISE(ABORT, 'only draft plans can be deleted; cancel instead'); END",
        )
    }

    /** 库存流水只追加：UPDATE/DELETE 一律拒绝（A06 §6.3 / §10.1）。 */
    private fun installAppendOnly(
        db: SupportSQLiteDatabase,
        table: String,
    ) {
        listOf("UPDATE", "DELETE").forEach { operation ->
            val suffix = operation.lowercase()
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS trg_${table}_no_$suffix " +
                    "BEFORE $operation ON $table " +
                    "BEGIN SELECT RAISE(ABORT, '$table is append-only'); END",
            )
        }
    }

    private fun installChecks(
        db: SupportSQLiteDatabase,
        table: String,
        predicate: String,
    ) {
        listOf("INSERT", "UPDATE").forEach { operation ->
            val suffix = operation.lowercase()
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS trg_${table}_check_$suffix " +
                    "BEFORE $operation ON $table WHEN $predicate " +
                    "BEGIN SELECT RAISE(ABORT, 'CHECK constraint failed: $table'); END",
            )
        }
    }

    private fun installPublishedImmutability(
        db: SupportSQLiteDatabase,
        table: String,
        ownerColumn: String,
        extraContentChanges: List<String> = emptyList(),
    ) {
        val contentChanged =
            (
                listOf(
                    "NEW.yield_qty <> OLD.yield_qty",
                    "NEW.yield_unit_id <> OLD.yield_unit_id",
                    "NEW.instructions_md IS NOT OLD.instructions_md",
                    "NEW.version_no <> OLD.version_no",
                    "NEW.$ownerColumn <> OLD.$ownerColumn",
                ) + extraContentChanges
            ).joinToString(separator = "\nOR ")
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_${table}_published_immutable " +
                "BEFORE UPDATE ON $table " +
                "WHEN OLD.status = 'PUBLISHED' AND ($contentChanged) " +
                "BEGIN SELECT RAISE(ABORT, 'published version is immutable'); END",
        )
    }

    private val PARTIAL_INDEX_NAMES =
        listOf(
            "ux_ingredient_normalized_name",
            "ux_sauce_normalized_name",
            "ux_recipe_normalized_name",
            "ux_sauce_current",
            "ux_recipe_current",
            "ux_supplier_normalized_name",
            "ux_barcode_primary",
            "ux_image_primary",
            "ux_reservation_active",
        )

    private val PARTIAL_INDEXES =
        listOf(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_ingredient_normalized_name
            ON ingredient(normalized_name) WHERE deleted_at IS NULL
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sauce_normalized_name
            ON sauce(normalized_name) WHERE deleted_at IS NULL
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_recipe_normalized_name
            ON recipe(normalized_name) WHERE deleted_at IS NULL
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_sauce_current
            ON sauce_version(sauce_id) WHERE is_current = 1
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_recipe_current
            ON recipe_version(recipe_id) WHERE is_current = 1
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_supplier_normalized_name
            ON supplier(normalized_name) WHERE deleted_at IS NULL
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_barcode_primary
            ON barcode(product_id) WHERE is_primary = 1
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_image_primary
            ON image_asset(owner_type, owner_id)
            WHERE role = 'PRIMARY' AND deleted_at IS NULL
            """.trimIndent(),
            """
            CREATE UNIQUE INDEX IF NOT EXISTS ux_reservation_active
            ON inventory_reservation(requirement_id, batch_id) WHERE status = 'ACTIVE'
            """.trimIndent(),
        )

    private const val UNIT_PREDICATE = """
        NEW.dimension NOT IN ('MASS', 'VOLUME', 'COUNT')
        OR NEW.base_factor <= 0
        OR NEW.display_precision < 0
        OR NEW.display_precision > 6
        OR NEW.is_base NOT IN (0, 1)
        OR NEW.is_approximate NOT IN (0, 1)
        OR NEW.is_active NOT IN (0, 1)
    """

    private const val INGREDIENT_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
    """

    private const val CONVERSION_PREDICATE = """
        NEW.factor <= 0
        OR NEW.from_unit_id = NEW.to_unit_id
    """

    private const val SAUCE_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
        OR (NEW.shelf_life_days IS NOT NULL AND NEW.shelf_life_days < 0)
        OR (NEW.difficulty IS NOT NULL AND NEW.difficulty NOT IN ('EASY', 'MEDIUM', 'HARD'))
    """

    private const val RECIPE_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
        OR (NEW.prep_minutes IS NOT NULL AND NEW.prep_minutes < 0)
        OR (NEW.cook_minutes IS NOT NULL AND NEW.cook_minutes < 0)
        OR (NEW.difficulty IS NOT NULL AND NEW.difficulty NOT IN ('EASY', 'MEDIUM', 'HARD'))
        OR NEW.category NOT IN ('STOCK', 'PREP', 'COMPONENT', 'DISH', 'OTHER')
    """

    private const val VERSION_PREDICATE = """
        NEW.status NOT IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')
        OR NEW.yield_qty <= 0
        OR NEW.version_no < 1
        OR NEW.is_current NOT IN (0, 1)
        OR (NEW.published_at IS NOT NULL AND NEW.published_at < 0)
    """

    /** 在共享版本约束上追加 v2 的 servings > 0；仅用于 recipe_version。 */
    private const val RECIPE_VERSION_PREDICATE = """
        NEW.status NOT IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')
        OR NEW.yield_qty <= 0
        OR NEW.version_no < 1
        OR NEW.is_current NOT IN (0, 1)
        OR (NEW.published_at IS NOT NULL AND NEW.published_at < 0)
        OR NEW.servings < 1
    """

    private const val CALCULATION_RECORD_PREDICATE = """
        NEW.status NOT IN ('PREVIEW', 'CONFIRMED')
        OR NEW.servings <= 0
        OR NEW.engine_version = ''
        OR NEW.payload_hash = ''
        OR length(NEW.payload_hash) <> 64
        OR NEW.payload_hash GLOB '*[^0-9a-f]*'
        OR NEW.created_at < 0
        OR (NEW.confirmed_at IS NOT NULL AND NEW.confirmed_at < 0)
        OR (NEW.status = 'CONFIRMED' AND NEW.confirmed_at IS NULL)
        OR (NEW.status = 'PREVIEW' AND NEW.confirmed_at IS NOT NULL)
    """

    private const val CALCULATION_RECORD_LINE_PREDICATE = """
        NEW.line_no < 1
        OR NEW.quantity <= 0
        OR NEW.is_optional NOT IN (0, 1)
    """

    private const val COMPONENT_PREDICATE = """
        NEW.component_type NOT IN ('INGREDIENT', 'SAUCE', 'RECIPE')
        OR NEW.quantity <= 0
        OR NEW.is_optional NOT IN (0, 1)
        OR NEW.line_no < 1
        OR (NEW.component_type = 'INGREDIENT' AND (
            NEW.ingredient_id IS NULL
            OR NEW.referenced_sauce_id IS NOT NULL
            OR NEW.referenced_sauce_version_id IS NOT NULL
            OR NEW.referenced_recipe_id IS NOT NULL
            OR NEW.referenced_recipe_version_id IS NOT NULL))
        OR (NEW.component_type = 'SAUCE' AND (
            NEW.ingredient_id IS NOT NULL
            OR NEW.referenced_sauce_id IS NULL
            OR NEW.referenced_recipe_id IS NOT NULL
            OR NEW.referenced_recipe_version_id IS NOT NULL))
        OR (NEW.component_type = 'RECIPE' AND (
            NEW.ingredient_id IS NOT NULL
            OR NEW.referenced_sauce_id IS NOT NULL
            OR NEW.referenced_sauce_version_id IS NOT NULL
            OR NEW.referenced_recipe_id IS NULL))
    """

    private const val SUPPLIER_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
        OR NEW.name = ''
    """

    private const val PRODUCT_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.content_qty <= 0
        OR NEW.stock_to_content_factor <= 0
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
        OR (NEW.shelf_life_days IS NOT NULL AND NEW.shelf_life_days < 0)
        OR (NEW.last_unit_price_minor IS NOT NULL AND NEW.last_unit_price_minor < 0)
    """

    private const val BARCODE_PREDICATE = """
        NEW.code = ''
        OR NEW.is_primary NOT IN (0, 1)
    """

    private const val LOCATION_PREDICATE = """
        NEW.kind NOT IN ('PANTRY', 'FRIDGE', 'FREEZER', 'DRY', 'OTHER')
        OR NEW.status NOT IN ('ACTIVE', 'ARCHIVED')
        OR NEW.revision < 1
        OR NEW.sort_order < 0
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
    """

    private const val BATCH_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'DEPLETED', 'EXPIRED', 'DISCARDED')
        OR NEW.quantity_on_hand < 0
        OR NEW.quantity_reserved < 0
        OR NEW.quantity_reserved > NEW.quantity_on_hand
        OR NEW.revision < 1
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
    """

    private const val LEDGER_PREDICATE = """
        NEW.type NOT IN (
            'PURCHASE_RECEIPT', 'PRODUCTION_ISSUE', 'RESERVE', 'RELEASE',
            'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'WASTE', 'TRANSFER_OUT',
            'TRANSFER_IN', 'RETURN', 'VOID_RECEIPT')
        OR NEW.on_hand_after < 0
        OR NEW.reserved_after < 0
        OR NEW.reserved_after > NEW.on_hand_after
        OR NEW.idempotency_key = ''
        OR NEW.occurred_at < 0
        OR NEW.created_at < 0
    """
    private const val PLAN_PREDICATE = """
        NEW.status NOT IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
        OR NEW.revision < 1
        OR NEW.title = ''
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
    """

    private const val PLAN_ITEM_PREDICATE = """
        NEW.status NOT IN ('PENDING', 'DONE', 'CANCELLED')
        OR NEW.quantity <= 0
        OR NEW.sort_order < 0
        OR NEW.created_at < 0
        OR NEW.updated_at < 0
    """

    private const val SNAPSHOT_PREDICATE = """
        NEW.revision_no < 1
        OR NEW.requirement_count < 0
        OR NEW.payload_json = ''
        OR NEW.payload_hash = ''
        OR NEW.created_at < 0
    """

    private const val REQUIREMENT_PREDICATE = """
        NEW.required_qty < 0
        OR NEW.available_qty_snapshot < 0
        OR NEW.shortage_qty < 0
        OR NEW.is_optional NOT IN (0, 1)
        OR NEW.conversion_status NOT IN ('OK', 'UNCONVERTIBLE', 'MISSING_PRODUCT', 'UNIT_MISMATCH')
        OR NEW.dimension NOT IN ('MASS', 'VOLUME', 'COUNT')
        OR NEW.aggregation_key = ''
    """

    private const val REQUIREMENT_SOURCE_PREDICATE = """
        NEW.component_type NOT IN ('INGREDIENT', 'SAUCE', 'RECIPE')
        OR NEW.quantity <= 0
        OR NEW.depth < 0
        OR NEW.is_optional NOT IN (0, 1)
        OR NEW.source_path = ''
    """

    private const val RESERVATION_PREDICATE = """
        NEW.status NOT IN ('ACTIVE', 'RELEASED', 'CONSUMED')
        OR NEW.quantity <= 0
        OR NEW.released_at IS NOT NULL AND NEW.status = 'ACTIVE'
    """
    private const val PROCUREMENT_PREDICATE = """
        NEW.status NOT IN ('OPEN', 'ORDERED', 'PARTIAL', 'RECEIVED', 'CANCELLED')
        OR NEW.requested_qty <= 0
    """

    private const val PURCHASE_ORDER_PREDICATE = """
        NEW.status NOT IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')
        OR NEW.revision < 1
        OR NEW.code = ''
        OR NEW.allow_over_receipt NOT IN (0, 1)
    """

    private const val PURCHASE_ORDER_ITEM_PREDICATE = """
        NEW.status NOT IN ('OPEN', 'PARTIAL', 'RECEIVED', 'CANCELLED')
        OR NEW.ordered_qty <= 0
        OR NEW.received_qty < 0
        OR NEW.cancelled_qty < 0
        OR NEW.cancelled_qty > NEW.ordered_qty
    """

    private const val RECEIPT_PREDICATE = """
        NEW.status NOT IN ('DRAFT', 'POSTED', 'VOID')
        OR NEW.receipt_no = ''
        OR NEW.received_at < 0
        OR (NEW.posted_at IS NOT NULL AND NEW.posted_at < 0)
        OR (NEW.voided_at IS NOT NULL AND NEW.voided_at < 0)
    """

    private const val RECEIPT_ITEM_PREDICATE = """
        NEW.quantity <= 0
    """
    private const val IMAGE_PREDICATE = """
        NEW.role NOT IN ('PRIMARY', 'PROCESS', 'REFERENCE')
        OR NEW.owner_type NOT IN (
            'INGREDIENT', 'SAUCE', 'SAUCE_VERSION', 'RECIPE', 'RECIPE_VERSION',
            'PRODUCT', 'INVENTORY_BATCH', 'PLAN', 'RECEIPT')
        OR NEW.relative_path = ''
        OR NEW.mime_type = ''
        OR NEW.sha256 = ''
        OR NEW.byte_size < 0
        OR NEW.sort_order < 0
    """

    private const val CHANGE_LOG_PREDICATE = """
        NEW.action NOT IN (
            'CREATE', 'UPDATE', 'DELETE', 'PUBLISH', 'CONFIRM', 'RECEIVE',
            'ADJUST', 'IMPORT', 'RESTORE')
        OR NEW.entity_type = ''
        OR NEW.entity_id = ''
    """

    private const val BACKUP_PREDICATE = """
        NEW.kind NOT IN ('AUTO', 'MANUAL', 'PRE_MIGRATION', 'PRE_RESTORE')
        OR NEW.status NOT IN ('SUCCESS', 'FAILED', 'VERIFIED')
        OR NEW.file_name = ''
        OR NEW.sha256 = ''
        OR NEW.size_bytes < 0
        OR NEW.schema_version < 1
    """

    private const val APP_METADATA_PREDICATE = """
        NEW.key = ''
    """
}
