package com.culinary.core.database.model

/** 采购明细状态（A06 §2）。 */
enum class PurchaseOrderItemStatusDb {
    OPEN,
    PARTIAL,
    RECEIVED,
    CANCELLED,
}
