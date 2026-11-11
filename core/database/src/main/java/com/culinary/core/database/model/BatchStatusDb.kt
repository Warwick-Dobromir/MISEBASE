package com.culinary.core.database.model

/** 批次状态（A06 §6.2，v9 增加冻结与回收）；批次不硬删。 */
enum class BatchStatusDb {
    ACTIVE,
    DEPLETED,
    EXPIRED,
    DISCARDED,
    FROZEN,
    RECYCLED,
}
