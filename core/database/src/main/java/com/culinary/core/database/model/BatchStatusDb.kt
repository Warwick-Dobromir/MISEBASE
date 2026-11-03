package com.culinary.core.database.model

/** 批次状态（A06 §6.2）；批次不硬删，耗尽置 DEPLETED。 */
enum class BatchStatusDb {
    ACTIVE,
    DEPLETED,
    EXPIRED,
    DISCARDED,
}
