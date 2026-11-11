package com.culinary.core.domain.model

/**
 * 库存流水类型（A06 §2/§6.3，与 `LedgerTypeDb` 取值一一对应）。
 *
 * 流水只追加；`on_hand_delta` 与 `reserved_delta` 由类型决定语义：
 * 出库两者同时扣减（已预留时），预留只增加 `reserved_delta`。
 */
enum class LedgerEntryType {
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

    /** v9：冻结/解冻/库位转移不改变数量，只留痕。 */
    FREEZE,
    UNFREEZE,
    LOCATION_TRANSFER,
}
