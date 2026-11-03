package com.culinary.core.database.model

/** 采购需求状态（A06 §8.1）。 */
enum class ProcurementStatusDb {
    OPEN,
    ORDERED,
    PARTIAL,
    RECEIVED,
    CANCELLED,
}
