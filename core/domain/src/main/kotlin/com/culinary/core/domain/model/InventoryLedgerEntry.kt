package com.culinary.core.domain.model

import java.time.Instant

/**
 * 库存流水（A06 §6.3）。
 *
 * 只追加、不修改；`idempotencyKey` 全局唯一，重复提交同一键不得产生第二条流水。
 * `onHandAfter`/`reservedAfter` 是操作后的物化余额，供审计与幂等复核。
 */
data class InventoryLedgerEntry(
    val id: LedgerEntryId,
    val batchId: BatchId,
    val productId: ProductId,
    val locationId: LocationId,
    val type: LedgerEntryType,
    val stockUnit: Unit,
    val onHandDelta: Double,
    val reservedDelta: Double,
    val onHandAfter: Double,
    val reservedAfter: Double,
    val idempotencyKey: String,
    val occurredAt: Instant,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val note: String? = null,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(onHandDelta.isFinite()) { "onHandDelta must be finite, was $onHandDelta" }
        require(reservedDelta.isFinite()) { "reservedDelta must be finite, was $reservedDelta" }
        require(onHandDelta != 0.0 || reservedDelta != 0.0) {
            "ledger entry must change onHand or reserved"
        }
        require(onHandAfter.isFinite() && onHandAfter >= 0.0) {
            "onHandAfter must be finite and >= 0, was $onHandAfter"
        }
        require(reservedAfter.isFinite() && reservedAfter >= 0.0) {
            "reservedAfter must be finite and >= 0, was $reservedAfter"
        }
        require(reservedAfter <= onHandAfter) {
            "reservedAfter ($reservedAfter) must not exceed onHandAfter ($onHandAfter)"
        }
    }

    /** 出库/消耗类流水（在手量减少）。 */
    val reducesOnHand: Boolean get() = onHandDelta < 0.0
}
