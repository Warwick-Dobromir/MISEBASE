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
    fun apply(db: SupportSQLiteDatabase) {
        PARTIAL_INDEXES.forEach(db::execSQL)

        installChecks(db, "unit", UNIT_PREDICATE)
        installChecks(db, "ingredient", INGREDIENT_PREDICATE)
        installChecks(db, "ingredient_unit_conversion", CONVERSION_PREDICATE)
        installChecks(db, "sauce", SAUCE_PREDICATE)
        installChecks(db, "recipe", RECIPE_PREDICATE)
        installChecks(db, "sauce_version", VERSION_PREDICATE)
        installChecks(db, "recipe_version", VERSION_PREDICATE)
        installChecks(db, "sauce_component", COMPONENT_PREDICATE)
        installChecks(db, "recipe_component", COMPONENT_PREDICATE)
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

        installPublishedImmutability(db, "sauce_version", "sauce_id")
        installPublishedImmutability(db, "recipe_version", "recipe_id")
        installAppendOnly(db, "inventory_ledger")
        installNoUpdate(db, "production_plan_snapshot")
    }

    /**
     * 计划快照的 payload 不可改（D-007）。
     *
     * 行删除不在这里禁止：`production_plan_snapshot` 由 `production_plan` 级联删除，
     * SQLite 触发器无法区分级联与直接删除。DAO 不提供删除方法，直接删除只能通过原始 SQL。
     */
    private fun installNoUpdate(
        db: SupportSQLiteDatabase,
        table: String,
    ) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_${table}_no_update " +
                "BEFORE UPDATE ON $table " +
                "BEGIN SELECT RAISE(ABORT, '$table payload is immutable'); END",
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
    ) {
        val contentChanged =
            """
            NEW.yield_qty <> OLD.yield_qty
            OR NEW.yield_unit_id <> OLD.yield_unit_id
            OR NEW.instructions_md IS NOT OLD.instructions_md
            OR NEW.version_no <> OLD.version_no
            OR NEW.$ownerColumn <> OLD.$ownerColumn
            """.trimIndent()
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS trg_${table}_published_immutable " +
                "BEFORE UPDATE ON $table " +
                "WHEN OLD.status = 'PUBLISHED' AND ($contentChanged) " +
                "BEGIN SELECT RAISE(ABORT, 'published version is immutable'); END",
        )
    }

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
}
