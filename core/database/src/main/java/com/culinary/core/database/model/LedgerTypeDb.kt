package com.culinary.core.database.model

/** 库存流水类型（A06 §2/§6.3）；流水只追加，禁止 UPDATE/DELETE。 */
enum class LedgerTypeDb {
    PURCHASE_RECEIPT,
    PRODUCTION_ISSUE,
    RESERVE,
    RELEASE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    WASTE,
    TRANSFER_OUT,
    TRANSFER_IN,
    RETURN,
    VOID_RECEIPT,
}
