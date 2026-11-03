package com.culinary.core.database.schema

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 无法表达、但 A06 要求的 schema 附加项：
 *
 * 1. `CHECK` 约束由 BEFORE INSERT/UPDATE 触发器等价实现（Room 只生成列与索引）；
 * 2. `ingredient.normalized_name` 的部分唯一索引（`WHERE deleted_at IS NULL`）。
 *
 * 全部语句都带 `IF NOT EXISTS`，可安全地在每次打开时重放。
 */
internal object CulinarySchemaExtras {
    fun apply(db: SupportSQLiteDatabase) {
        db.execSQL(INGREDIENT_ACTIVE_NAME_INDEX)
        db.execSQL(UNIT_CHECK_INSERT)
        db.execSQL(UNIT_CHECK_UPDATE)
        db.execSQL(INGREDIENT_CHECK_INSERT)
        db.execSQL(INGREDIENT_CHECK_UPDATE)
        db.execSQL(CONVERSION_CHECK_INSERT)
        db.execSQL(CONVERSION_CHECK_UPDATE)
    }

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

    private val INGREDIENT_ACTIVE_NAME_INDEX =
        """
        CREATE UNIQUE INDEX IF NOT EXISTS ux_ingredient_normalized_name
        ON ingredient(normalized_name)
        WHERE deleted_at IS NULL
        """.trimIndent()

    private val UNIT_CHECK_INSERT =
        """
        CREATE TRIGGER IF NOT EXISTS trg_unit_check_insert
        BEFORE INSERT ON unit
        WHEN $UNIT_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: unit');
        END
        """.trimIndent()

    private val UNIT_CHECK_UPDATE =
        """
        CREATE TRIGGER IF NOT EXISTS trg_unit_check_update
        BEFORE UPDATE ON unit
        WHEN $UNIT_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: unit');
        END
        """.trimIndent()

    private val INGREDIENT_CHECK_INSERT =
        """
        CREATE TRIGGER IF NOT EXISTS trg_ingredient_check_insert
        BEFORE INSERT ON ingredient
        WHEN $INGREDIENT_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: ingredient');
        END
        """.trimIndent()

    private val INGREDIENT_CHECK_UPDATE =
        """
        CREATE TRIGGER IF NOT EXISTS trg_ingredient_check_update
        BEFORE UPDATE ON ingredient
        WHEN $INGREDIENT_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: ingredient');
        END
        """.trimIndent()

    private val CONVERSION_CHECK_INSERT =
        """
        CREATE TRIGGER IF NOT EXISTS trg_ingredient_unit_conversion_check_insert
        BEFORE INSERT ON ingredient_unit_conversion
        WHEN $CONVERSION_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: ingredient_unit_conversion');
        END
        """.trimIndent()

    private val CONVERSION_CHECK_UPDATE =
        """
        CREATE TRIGGER IF NOT EXISTS trg_ingredient_unit_conversion_check_update
        BEFORE UPDATE ON ingredient_unit_conversion
        WHEN $CONVERSION_PREDICATE
        BEGIN
            SELECT RAISE(ABORT, 'CHECK constraint failed: ingredient_unit_conversion');
        END
        """.trimIndent()
}
