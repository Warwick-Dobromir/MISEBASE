package com.culinary.core.database.model

/** 计算记录状态（MB-D-017）；数据库层独立声明，由 core:data 映射到领域枚举。 */
enum class CalculationRecordStatusDb {
    PREVIEW,
    CONFIRMED,
}
