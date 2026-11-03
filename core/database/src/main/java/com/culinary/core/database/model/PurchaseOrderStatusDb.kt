package com.culinary.core.database.model

/** 采购单状态（A06 §2）。 */
enum class PurchaseOrderStatusDb {
    DRAFT,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED,
}
