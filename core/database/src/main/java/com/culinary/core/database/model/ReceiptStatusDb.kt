package com.culinary.core.database.model

/** 收货单状态（A06 §2）；POSTED 后不可改，作废走 VOID。 */
enum class ReceiptStatusDb {
    DRAFT,
    POSTED,
    VOID,
}
