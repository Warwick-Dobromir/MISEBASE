package com.culinary.core.domain.model

import java.time.Instant

/**
 * 库存流水（A06 §6.3，v9 增加档案归属与零变动类型）。
 *
 * 只追加、不修改；`idempotencyKey` 全局唯一，重复提交同一键不得产生第二条流水。
 * `onHandAfter`/`reservedAfter` 是操作后的物化余额，供审计与幂等复核。
 *
 * 冻结 / 解冻 / 库位转移不改变数量，允许 `onHandDelta = reservedDelta = 0`，
 * 但仍作为流水留存，保证"状态变化也有记录可追溯"。
 */
data class InventoryLedgerEntry(
    val id: LedgerEntryId,
    val batchId: BatchId,
    /** 来源采购商品；酱汁现货等没有采购商品的流水为 null。 */
    val productId: ProductId? = null,
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
    /** 归属库存档案（v9）；历史流水在迁移时回填。 */
    val archiveId: String? = null,
    /** 操作来源/操作人预留（v9 单用户：默认 null 表示本机）。 */
    val operator: String? = null,
    /** 关联打印任务（v9）。 */
    val printJobId: String? = null,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
        require(onHandDelta.isFinite()) { "onHandDelta must be finite, was $onHandDelta" }
        require(reservedDelta.isFinite()) { "reservedDelta must be finite, was $reservedDelta" }
        require(allowsZeroDelta(type) || onHandDelta != 0.0 || reservedDelta != 0.0) {
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

    companion object {
        /** 只记录状态/位置变化、不改变数量的流水类型。 */
        fun allowsZeroDelta(type: LedgerEntryType): Boolean =
            type == LedgerEntryType.FREEZE ||
                type == LedgerEntryType.UNFREEZE ||
                type == LedgerEntryType.LOCATION_TRANSFER
    }
}
