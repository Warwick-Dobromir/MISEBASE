package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** 批次生命周期（v9）：正常 / 冻结 / 回收，另有耗尽、过期、报废。 */
enum class BatchLifecycleStatus {
    ACTIVE,
    FROZEN,
    RECYCLED,
    DEPLETED,
    EXPIRED,
    DISCARDED,
}

/**
 * 批次视图（v9）：档案 + 库位 + 原始入库 + 累计出库 + 生命周期。
 *
 * 冻结批次不出库但仍可盘点；回收站批次只保留历史，不计入在库。
 */
data class InventoryBatchRecord(
    val id: String,
    val archiveId: String?,
    val archiveName: String,
    val sourceProductId: String? = null,
    val sourceProductName: String? = null,
    val locationId: LocationId,
    val locationName: String? = null,
    val batchCode: String? = null,
    val receivedAt: Instant,
    val productionDate: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val onHand: Double,
    val reserved: Double = 0.0,
    val originalQuantity: Double = onHand,
    val outboundQuantity: Double = 0.0,
    val unit: Unit,
    val status: BatchLifecycleStatus = BatchLifecycleStatus.ACTIVE,
    val frozenAt: Instant? = null,
    val frozenReason: String? = null,
    val recycledAt: Instant? = null,
    val isTemporary: Boolean = false,
    val temporarySince: Instant? = null,
) {
    val available: Double get() = onHand - reserved
    val isFrozen: Boolean get() = status == BatchLifecycleStatus.FROZEN
    val isRecycled: Boolean get() = status == BatchLifecycleStatus.RECYCLED
    val isLive: Boolean get() = status == BatchLifecycleStatus.ACTIVE || status == BatchLifecycleStatus.FROZEN

    /** FIFO 键：先进先出，按入库时间升序。 */
    val fifoKey: Instant get() = receivedAt
}

/** 批次详情聚合：档案、流水、冻结与转移记录。 */
data class InventoryBatchDetail(
    val batch: InventoryBatchRecord,
    val archive: InventoryArchive,
    val ledger: List<InventoryLedgerEntry>,
    val freezeRecords: List<FreezeRecord>,
    val transfers: List<LocationTransferRecord>,
    val printJob: PrintJobRecord? = null,
)
