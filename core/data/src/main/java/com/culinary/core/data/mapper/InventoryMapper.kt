package com.culinary.core.data.mapper

import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryLedgerEntity
import com.culinary.core.database.model.LedgerTypeDb
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Unit
import java.time.Instant
import java.time.LocalDate

/** 库存 Entity ↔ 领域模型；枚举显式穷举，新增类型时必须同步。 */
object InventoryMapper {
    /** 批次 → 商品维度的库存项；没有来源商品的批次（酱汁现货）返回 null。 */
    fun toDomain(
        entity: InventoryBatchEntity,
        stockUnit: Unit,
    ): InventoryItem? {
        val productId = entity.productId ?: return null
        return InventoryItem(
            batchId = BatchId(entity.id),
            productId = ProductId(productId),
            locationId = LocationId(entity.locationId),
            stockUnit = stockUnit,
            onHand = entity.quantityOnHand,
            reserved = entity.quantityReserved,
            expiresOn = entity.expiryDate?.let(LocalDate::parse),
            batchCode = entity.batchCode,
            receivedAt = Instant.ofEpochMilli(entity.receivedAt),
        )
    }

    fun toDomain(
        entity: InventoryLedgerEntity,
        stockUnit: Unit,
    ): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId(entity.id),
            batchId = BatchId(entity.batchId),
            productId = entity.productId?.let(::ProductId),
            locationId = LocationId(entity.locationId),
            type = toDomain(entity.type),
            stockUnit = stockUnit,
            onHandDelta = entity.onHandDelta,
            reservedDelta = entity.reservedDelta,
            onHandAfter = entity.onHandAfter,
            reservedAfter = entity.reservedAfter,
            idempotencyKey = entity.idempotencyKey,
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
            referenceType = entity.referenceType,
            referenceId = entity.referenceId,
            note = entity.note,
            archiveId = entity.archiveId,
            operator = entity.operator,
            printJobId = entity.printJobId,
        )

    fun toEntity(entry: InventoryLedgerEntry): InventoryLedgerEntity =
        InventoryLedgerEntity(
            id = entry.id.value,
            batchId = entry.batchId.value,
            productId = entry.productId?.value,
            locationId = entry.locationId.value,
            type = toDb(entry.type),
            onHandDelta = entry.onHandDelta,
            reservedDelta = entry.reservedDelta,
            stockUnitId = entry.stockUnit.id.value,
            onHandAfter = entry.onHandAfter,
            reservedAfter = entry.reservedAfter,
            referenceType = entry.referenceType,
            referenceId = entry.referenceId,
            idempotencyKey = entry.idempotencyKey,
            note = entry.note,
            occurredAt = entry.occurredAt.toEpochMilli(),
            createdAt = entry.occurredAt.toEpochMilli(),
            archiveId = entry.archiveId,
            operator = entry.operator,
            printJobId = entry.printJobId,
        )

    fun toDomain(type: LedgerTypeDb): LedgerEntryType =
        when (type) {
            LedgerTypeDb.PURCHASE_RECEIPT -> LedgerEntryType.PURCHASE_RECEIPT
            LedgerTypeDb.PRODUCTION_ISSUE -> LedgerEntryType.PRODUCTION_ISSUE
            LedgerTypeDb.RESERVE -> LedgerEntryType.RESERVE
            LedgerTypeDb.RELEASE -> LedgerEntryType.RELEASE
            LedgerTypeDb.ADJUSTMENT_IN -> LedgerEntryType.ADJUSTMENT_IN
            LedgerTypeDb.ADJUSTMENT_OUT -> LedgerEntryType.ADJUSTMENT_OUT
            LedgerTypeDb.WASTE -> LedgerEntryType.WASTE
            LedgerTypeDb.TRANSFER_OUT -> LedgerEntryType.TRANSFER_OUT
            LedgerTypeDb.TRANSFER_IN -> LedgerEntryType.TRANSFER_IN
            LedgerTypeDb.RETURN -> LedgerEntryType.RETURN
            LedgerTypeDb.VOID_RECEIPT -> LedgerEntryType.VOID_RECEIPT
            LedgerTypeDb.FREEZE -> LedgerEntryType.FREEZE
            LedgerTypeDb.UNFREEZE -> LedgerEntryType.UNFREEZE
            LedgerTypeDb.LOCATION_TRANSFER -> LedgerEntryType.LOCATION_TRANSFER
        }

    fun toDb(type: LedgerEntryType): LedgerTypeDb =
        when (type) {
            LedgerEntryType.PURCHASE_RECEIPT -> LedgerTypeDb.PURCHASE_RECEIPT
            LedgerEntryType.PRODUCTION_ISSUE -> LedgerTypeDb.PRODUCTION_ISSUE
            LedgerEntryType.RESERVE -> LedgerTypeDb.RESERVE
            LedgerEntryType.RELEASE -> LedgerTypeDb.RELEASE
            LedgerEntryType.ADJUSTMENT_IN -> LedgerTypeDb.ADJUSTMENT_IN
            LedgerEntryType.ADJUSTMENT_OUT -> LedgerTypeDb.ADJUSTMENT_OUT
            LedgerEntryType.WASTE -> LedgerTypeDb.WASTE
            LedgerEntryType.TRANSFER_OUT -> LedgerTypeDb.TRANSFER_OUT
            LedgerEntryType.TRANSFER_IN -> LedgerTypeDb.TRANSFER_IN
            LedgerEntryType.RETURN -> LedgerTypeDb.RETURN
            LedgerEntryType.VOID_RECEIPT -> LedgerTypeDb.VOID_RECEIPT
            LedgerEntryType.FREEZE -> LedgerTypeDb.FREEZE
            LedgerEntryType.UNFREEZE -> LedgerTypeDb.UNFREEZE
            LedgerEntryType.LOCATION_TRANSFER -> LedgerTypeDb.LOCATION_TRANSFER
        }
}
