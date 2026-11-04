package com.culinary.core.domain.model

import java.time.LocalDate

/**
 * 库存项（批次级，A06 §6.2）。
 *
 * 在手量是物化余额，可用量 = 在手量 − 预留量；余额只能由追加式流水改变，
 * 领域模型不提供"直接改数字"的方法。批次不硬删，耗尽置 DEPLETED。
 */
data class InventoryItem(
    val batchId: BatchId,
    val productId: ProductId,
    val locationId: LocationId,
    val stockUnit: Unit,
    val onHand: Double,
    val reserved: Double = 0.0,
    val expiresOn: LocalDate? = null,
) {
    init {
        require(onHand.isFinite() && onHand >= 0.0) { "onHand must be finite and >= 0, was $onHand" }
        require(reserved.isFinite() && reserved >= 0.0) { "reserved must be finite and >= 0, was $reserved" }
        require(reserved <= onHand) { "reserved ($reserved) must not exceed onHand ($onHand)" }
    }

    /** 可用量：已被预留的数量不能再分配给新需求。 */
    val available: Double get() = onHand - reserved

    val isDepleted: Boolean get() = onHand <= 0.0

    /** FEFO 排序键：先到期先出，无到期日的批次排在最后。 */
    val fefoKey: LocalDate get() = expiresOn ?: LocalDate.MAX
}
