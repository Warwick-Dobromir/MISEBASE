package com.culinary.core.database.schema

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 无法表达、但 A06 要求的 schema 附加项。
 *
 * 目前是 `unit` 的 CHECK 约束：Room 只会生成列与索引，不会生成 `CHECK`，
 * 因此用 BEFORE INSERT/UPDATE 触发器等价实现，并抛 `SQLITE_CONSTRAINT`。
 *
 * 全部语句都带 `IF NOT EXISTS`，可安全地在每次打开时重放。
 */
internal object CulinarySchemaExtras {
    fun apply(db: SupportSQLiteDatabase) {
        db.execSQL(UNIT_CHECK_INSERT)
        db.execSQL(UNIT_CHECK_UPDATE)
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
}
