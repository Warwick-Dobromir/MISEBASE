package com.culinary.core.data.mapper

import com.culinary.core.database.entity.InventoryArchiveEntity
import com.culinary.core.database.entity.InventoryArchiveProductEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryFreezeRecordEntity
import com.culinary.core.database.entity.InventoryIssueSlipEntity
import com.culinary.core.database.entity.InventoryIssueSlipLineEntity
import com.culinary.core.database.entity.InventoryLocationTransferEntity
import com.culinary.core.database.entity.InventoryPrintJobEntity
import com.culinary.core.database.entity.InventoryStocktakeEntity
import com.culinary.core.database.entity.InventoryStocktakeLineEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.model.ArchiveKindDb
import com.culinary.core.database.model.BatchStatusDb
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.FreezeActionDb
import com.culinary.core.database.model.IssueSlipTypeDb
import com.culinary.core.database.model.PrintJobKindDb
import com.culinary.core.database.model.PrintJobStatusDb
import com.culinary.core.database.model.StocktakeScopeDb
import com.culinary.core.database.model.StocktakeStatusDb
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.FreezeAction
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.IssueRole
import com.culinary.core.domain.model.IssueSlip
import com.culinary.core.domain.model.IssueSlipLine
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.LocationTransferRecord
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StocktakeLine
import com.culinary.core.domain.model.StocktakeScope
import com.culinary.core.domain.model.StocktakeStatus
import com.culinary.core.domain.model.Unit
import java.time.Instant
import java.time.LocalDate

/**
 * 库存档案化 Entity ↔ 领域模型（v9）。
 *
 * 时间统一用 epoch 毫秒存取，`production_date`/`expiry_date` 用 ISO 字符串，
 * 枚举显式穷举；新增枚举值时必须同步，避免 `valueOf` 在运行时抛异常。
 */
@Suppress("TooManyFunctions") // 一组 v9 聚合的映射集中在一个文件，拆分不会减少映射数量
object InventoryArchiveMapper {
    fun toDomain(
        entity: InventoryArchiveEntity,
        stockUnit: Unit,
    ): InventoryArchive =
        InventoryArchive(
            id = entity.id,
            name = entity.name,
            englishName = entity.englishName,
            categoryId = entity.categoryId,
            kind = toDomain(entity.archiveKind),
            sauceId = entity.sauceId,
            stockUnit = stockUnit,
            minimumStock = entity.minimumStock,
            targetStock = entity.targetStock,
            imageUri = entity.imageUri,
            storageMethod = entity.storageMethod,
            storageTemperature = entity.storageTemperature,
            defaultLocationId = entity.defaultLocationId?.let(::LocationId),
            description = entity.description,
            status = toDomain(entity.status),
        )

    fun toEntity(
        archive: InventoryArchive,
        normalizedName: String,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?,
        revision: Int,
    ): InventoryArchiveEntity =
        InventoryArchiveEntity(
            id = archive.id,
            name = archive.name,
            normalizedName = normalizedName,
            englishName = archive.englishName,
            categoryId = archive.categoryId,
            archiveKind = toDb(archive.kind),
            sauceId = archive.sauceId,
            stockUnitId = archive.stockUnit.id.value,
            minimumStock = archive.minimumStock,
            targetStock = archive.targetStock,
            imageUri = archive.imageUri,
            storageMethod = archive.storageMethod,
            storageTemperature = archive.storageTemperature,
            defaultLocationId = archive.defaultLocationId?.value,
            description = archive.description,
            status = toDb(archive.status),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            revision = revision,
        )

    fun toDomain(
        entity: InventoryArchiveProductEntity,
        product: ProductEntity,
        stockUnit: Unit,
        contentUnit: Unit,
    ): ArchiveProductLink =
        ArchiveProductLink(
            product = ProductMapper.toDomain(product, stockUnit, contentUnit),
            conversionRatio = entity.conversionRatio,
            isDefault = entity.isDefault,
        )

    fun toDomain(
        entity: InventoryBatchEntity,
        stockUnit: Unit,
        archiveName: String,
        productName: String?,
        locationName: String?,
        inboundQuantity: Double?,
        outboundQuantity: Double?,
    ): InventoryBatchRecord =
        InventoryBatchRecord(
            id = entity.id,
            archiveId = entity.archiveId,
            archiveName = archiveName,
            sourceProductId = entity.productId,
            sourceProductName = productName,
            locationId = LocationId(entity.locationId),
            locationName = locationName,
            batchCode = entity.batchCode,
            receivedAt = Instant.ofEpochMilli(entity.receivedAt),
            productionDate = entity.productionDate?.let(LocalDate::parse),
            expiresOn = entity.expiryDate?.let(LocalDate::parse),
            onHand = entity.quantityOnHand,
            reserved = entity.quantityReserved,
            originalQuantity = inboundQuantity ?: entity.quantityOnHand,
            outboundQuantity = outboundQuantity ?: 0.0,
            unit = stockUnit,
            status = toDomain(entity.status),
            frozenAt = entity.frozenAt?.let(Instant::ofEpochMilli),
            frozenReason = entity.frozenReason,
            recycledAt = entity.recycledAt?.let(Instant::ofEpochMilli),
            isTemporary = entity.isTemporary,
            temporarySince = entity.temporarySince?.let(Instant::ofEpochMilli),
        )

    fun toDomain(entity: InventoryFreezeRecordEntity): FreezeRecord =
        FreezeRecord(
            id = entity.id,
            batchId = entity.batchId,
            action = toDomain(entity.action),
            reason = entity.reason,
            operator = entity.operator,
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
        )

    fun toDomain(
        entity: InventoryLocationTransferEntity,
        fromLocationName: String? = null,
        toLocationName: String? = null,
    ): LocationTransferRecord =
        LocationTransferRecord(
            id = entity.id,
            batchId = entity.batchId,
            fromLocationName = fromLocationName,
            toLocationName = toLocationName,
            quantity = entity.quantity,
            note = entity.note,
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
        )

    fun toDomain(
        entity: InventoryIssueSlipEntity,
        lines: List<InventoryIssueSlipLineEntity>,
    ): IssueSlip =
        IssueSlip(
            id = entity.id,
            slipNo = entity.slipNo,
            type = toDomain(entity.type),
            recipeName = entity.recipeName,
            occurredAt = Instant.ofEpochMilli(entity.occurredAt),
            note = entity.note,
            lines = lines.sortedBy { it.lineNo }.map { toDomain(it) },
        )

    fun toDomain(entity: InventoryIssueSlipLineEntity): IssueSlipLine =
        IssueSlipLine(
            lineNo = entity.lineNo,
            archiveName = entity.archiveName,
            batchCode = entity.batchCode,
            locationName = entity.locationName,
            quantity = entity.quantity,
            unitSymbol = entity.unitSymbol,
            role = toIssueRole(entity.role),
        )

    /** 领域角色 → 明细行的 `role` 列；未知值不写入，调用方只需传已知枚举。 */
    fun toRoleString(role: IssueRole): String =
        when (role) {
            IssueRole.INGREDIENT -> InventoryIssueSlipLineEntity.ROLE_INGREDIENT
            IssueRole.SAUCE -> InventoryIssueSlipLineEntity.ROLE_SAUCE
            IssueRole.SAUCE_GAP -> InventoryIssueSlipLineEntity.ROLE_SAUCE_GAP
        }

    fun toDomain(entity: InventoryPrintJobEntity): PrintJobRecord =
        PrintJobRecord(
            id = entity.id,
            kind = toDomain(entity.kind),
            refType = entity.refType,
            refId = entity.refId,
            status = toDomain(entity.status),
            attempts = entity.attempts,
            reprintCount = entity.reprintCount,
            lastError = entity.lastError,
            paperWidthMm = entity.paperWidthMm,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            printedAt = entity.printedAt?.let(Instant::ofEpochMilli),
        )

    fun toDomain(
        entity: InventoryStocktakeEntity,
        lines: List<InventoryStocktakeLineEntity>,
    ): Stocktake =
        Stocktake(
            id = entity.id,
            scope = toDomain(entity.scopeType),
            scopeLabel = entity.scopeLabel.orEmpty(),
            status = toDomain(entity.status),
            note = entity.note,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            confirmedAt = entity.confirmedAt?.let(Instant::ofEpochMilli),
            lines = lines.sortedBy { it.lineNo }.map { toDomain(it) },
        )

    fun toDomain(entity: InventoryStocktakeLineEntity): StocktakeLine =
        StocktakeLine(
            id = entity.id,
            lineNo = entity.lineNo,
            batchId = entity.batchId,
            archiveId = entity.archiveId,
            archiveName = entity.archiveName,
            batchCode = entity.batchCode,
            locationName = entity.locationName,
            bookQty = entity.bookQty,
            countedQty = entity.countedQty,
            unitSymbol = entity.unitSymbol,
            frozen = entity.frozen,
        )

    fun toDomain(value: EntityStatusDb): EntityStatus =
        when (value) {
            EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
            EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
        }

    fun toDb(value: EntityStatus): EntityStatusDb =
        when (value) {
            EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
            EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
        }

    fun toDomain(value: ArchiveKindDb): ArchiveKind =
        when (value) {
            ArchiveKindDb.INGREDIENT -> ArchiveKind.INGREDIENT
            ArchiveKindDb.SAUCE -> ArchiveKind.SAUCE
        }

    fun toDb(value: ArchiveKind): ArchiveKindDb =
        when (value) {
            ArchiveKind.INGREDIENT -> ArchiveKindDb.INGREDIENT
            ArchiveKind.SAUCE -> ArchiveKindDb.SAUCE
        }

    fun toDomain(value: BatchStatusDb): BatchLifecycleStatus =
        when (value) {
            BatchStatusDb.ACTIVE -> BatchLifecycleStatus.ACTIVE
            BatchStatusDb.FROZEN -> BatchLifecycleStatus.FROZEN
            BatchStatusDb.RECYCLED -> BatchLifecycleStatus.RECYCLED
            BatchStatusDb.DEPLETED -> BatchLifecycleStatus.DEPLETED
            BatchStatusDb.EXPIRED -> BatchLifecycleStatus.EXPIRED
            BatchStatusDb.DISCARDED -> BatchLifecycleStatus.DISCARDED
        }

    fun toDomain(value: FreezeActionDb): FreezeAction =
        when (value) {
            FreezeActionDb.FREEZE -> FreezeAction.FREEZE
            FreezeActionDb.UNFREEZE -> FreezeAction.UNFREEZE
        }

    fun toDomain(value: IssueSlipTypeDb): IssueType =
        when (value) {
            IssueSlipTypeDb.MANUAL -> IssueType.MANUAL
            IssueSlipTypeDb.RECIPE -> IssueType.RECIPE
            IssueSlipTypeDb.SAUCE -> IssueType.SAUCE
        }

    fun toDb(value: IssueType): IssueSlipTypeDb =
        when (value) {
            IssueType.MANUAL -> IssueSlipTypeDb.MANUAL
            IssueType.RECIPE -> IssueSlipTypeDb.RECIPE
            IssueType.SAUCE -> IssueSlipTypeDb.SAUCE
        }

    fun toDomain(value: PrintJobKindDb): PrintKind =
        when (value) {
            PrintJobKindDb.RECEIPT -> PrintKind.RECEIPT
            PrintJobKindDb.ISSUE -> PrintKind.ISSUE
            PrintJobKindDb.LABEL -> PrintKind.LABEL
        }

    fun toDb(value: PrintKind): PrintJobKindDb =
        when (value) {
            PrintKind.RECEIPT -> PrintJobKindDb.RECEIPT
            PrintKind.ISSUE -> PrintJobKindDb.ISSUE
            PrintKind.LABEL -> PrintJobKindDb.LABEL
        }

    fun toDomain(value: PrintJobStatusDb): PrintStatus =
        when (value) {
            PrintJobStatusDb.PENDING -> PrintStatus.PENDING
            PrintJobStatusDb.PRINTED -> PrintStatus.PRINTED
            PrintJobStatusDb.FAILED -> PrintStatus.FAILED
        }

    fun toDb(value: PrintStatus): PrintJobStatusDb =
        when (value) {
            PrintStatus.PENDING -> PrintJobStatusDb.PENDING
            PrintStatus.PRINTED -> PrintJobStatusDb.PRINTED
            PrintStatus.FAILED -> PrintJobStatusDb.FAILED
        }

    fun toDomain(value: StocktakeScopeDb): StocktakeScope =
        when (value) {
            StocktakeScopeDb.ALL -> StocktakeScope.ALL
            StocktakeScopeDb.CATEGORY -> StocktakeScope.CATEGORY
            StocktakeScopeDb.LOCATION -> StocktakeScope.LOCATION
            StocktakeScopeDb.ARCHIVE -> StocktakeScope.ARCHIVE
        }

    fun toDb(value: StocktakeScope): StocktakeScopeDb =
        when (value) {
            StocktakeScope.ALL -> StocktakeScopeDb.ALL
            StocktakeScope.CATEGORY -> StocktakeScopeDb.CATEGORY
            StocktakeScope.LOCATION -> StocktakeScopeDb.LOCATION
            StocktakeScope.ARCHIVE -> StocktakeScopeDb.ARCHIVE
        }

    fun toDomain(value: StocktakeStatusDb): StocktakeStatus =
        when (value) {
            StocktakeStatusDb.DRAFT -> StocktakeStatus.DRAFT
            StocktakeStatusDb.CONFIRMED -> StocktakeStatus.CONFIRMED
        }

    /** 明细角色是自由文本列；未知值按菜谱直接食材处理，避免历史单据读不出来。 */
    private fun toIssueRole(raw: String): IssueRole {
        val name = raw.trim().uppercase()
        return IssueRole.entries.firstOrNull { it.name == name } ?: IssueRole.INGREDIENT
    }
}
