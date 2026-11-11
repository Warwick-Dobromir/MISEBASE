package com.culinary.core.database.model

/**
 * 库存流水类型（A06 §2/§6.3，v9 增加冻结/解冻/库位转移）；流水只追加，禁止 UPDATE/DELETE。
 *
 * `FREEZE`/`UNFREEZE`/`LOCATION_TRANSFER` 不改变数量，`on_hand_delta` 为 0，
 * 但仍作为流水留存，保证"冻结/转移也有记录可追溯"。
 */
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
    FREEZE,
    UNFREEZE,
    LOCATION_TRANSFER,
}
