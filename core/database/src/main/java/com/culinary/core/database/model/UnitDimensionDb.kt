package com.culinary.core.database.model

/**
 * 数据库层的单位维度（A06 §2）。
 *
 * `core:database` 不依赖 `core:domain`（A03A §6.2），因此枚举在数据库层独立声明；
 * 由 `core:data` 的 Mapper 与领域枚举互转。值必须与 A06 完全一致。
 */
enum class UnitDimensionDb {
    MASS,
    VOLUME,
    COUNT,
}
