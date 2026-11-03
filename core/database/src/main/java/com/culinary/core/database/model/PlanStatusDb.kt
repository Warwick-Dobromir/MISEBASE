package com.culinary.core.database.model

/** 制作计划状态（A06 §2）。 */
enum class PlanStatusDb {
    DRAFT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}
