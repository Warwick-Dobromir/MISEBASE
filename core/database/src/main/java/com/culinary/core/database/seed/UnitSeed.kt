package com.culinary.core.database.seed

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 单位种子数据（A06 §3.1）。
 *
 * 用固定 UUID v4 形态的常量 ID，保证每次安装的种子一致（D-005：TEXT 小写 UUID v4）。
 * `INSERT OR IGNORE` 让种子在重复打开时幂等，不覆盖用户对单位的修改。
 */
internal fun seedUnits(db: SupportSQLiteDatabase) {
    SEED_UNITS.forEach { unit ->
        db.execSQL(
            "INSERT OR IGNORE INTO unit " +
                "(id, code, name, symbol, dimension, base_factor, is_base, is_approximate, " +
                "display_precision, is_active, sort_order, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf<Any?>(
                unit.id,
                unit.code,
                unit.name,
                unit.symbol,
                unit.dimension,
                unit.baseFactor,
                if (unit.isBase) 1 else 0,
                if (unit.isApproximate) 1 else 0,
                unit.displayPrecision,
                unit.sortOrder,
                SEED_CREATED_AT,
            ),
        )
    }
}

private const val SEED_CREATED_AT: Long = 1_760_000_000_000L

private data class SeedUnit(
    val ordinal: Int,
    val code: String,
    val name: String,
    val symbol: String,
    val dimension: String,
    val baseFactor: Double,
    val isBase: Boolean,
    val isApproximate: Boolean,
    val displayPrecision: Int,
    val sortOrder: Int,
) {
    val id: String
        get() = "00000000-0000-4000-8000-" + ordinal.toString().padStart(12, '0')
}

private val SEED_UNITS =
    listOf(
        SeedUnit(1, "mg", "毫克", "mg", "MASS", 0.001, isBase = false, isApproximate = false, 0, 0),
        SeedUnit(2, "g", "克", "g", "MASS", 1.0, isBase = true, isApproximate = false, 1, 1),
        SeedUnit(3, "kg", "千克", "kg", "MASS", 1000.0, isBase = false, isApproximate = false, 2, 2),
        SeedUnit(4, "ml", "毫升", "ml", "VOLUME", 1.0, isBase = true, isApproximate = false, 0, 3),
        SeedUnit(5, "l", "升", "l", "VOLUME", 1000.0, isBase = false, isApproximate = false, 2, 4),
        SeedUnit(6, "tsp", "茶匙", "tsp", "VOLUME", 5.0, isBase = false, isApproximate = true, 2, 5),
        SeedUnit(7, "tbsp", "汤匙", "tbsp", "VOLUME", 15.0, isBase = false, isApproximate = true, 2, 6),
        SeedUnit(8, "cup", "杯", "cup", "VOLUME", 240.0, isBase = false, isApproximate = true, 2, 7),
        SeedUnit(9, "fl_oz", "液量盎司", "fl oz", "VOLUME", 29.5735, isBase = false, isApproximate = true, 2, 8),
        SeedUnit(10, "piece", "个", "个", "COUNT", 1.0, isBase = true, isApproximate = false, 0, 9),
        SeedUnit(11, "bunch", "把", "把", "COUNT", 1.0, isBase = false, isApproximate = false, 0, 10),
        SeedUnit(12, "clove", "瓣", "瓣", "COUNT", 1.0, isBase = false, isApproximate = false, 0, 11),
        SeedUnit(13, "slice", "片", "片", "COUNT", 1.0, isBase = false, isApproximate = false, 0, 12),
        SeedUnit(14, "pack", "包", "包", "COUNT", 1.0, isBase = false, isApproximate = false, 0, 13),
        SeedUnit(15, "portion", "份", "份", "COUNT", 1.0, isBase = false, isApproximate = false, 0, 14),
    )
