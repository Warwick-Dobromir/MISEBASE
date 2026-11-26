package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.InventoryRules
import com.culinary.core.domain.model.IssueSlip
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.LocationTransferRecord
import com.culinary.core.domain.model.NewArchiveBatch
import com.culinary.core.domain.model.NewIssueSlip
import com.culinary.core.domain.model.NewStocktake
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StorageLocation
import java.time.Instant

/**
 * 库存档案化读写端口（v9）。
 *
 * 所有数量变化仍然只经过 [appendLedger]：批次余额是物化值，流水是事实。
 * 事务边界由 Use Case 通过 `TransactionRunner` 控制；本端口不自行开事务。
 */
@Suppress("TooManyFunctions")
interface InventoryArchiveRepository {
    // ---- 档案 ----
    suspend fun listArchives(): Outcome<List<InventoryArchive>>

    suspend fun findArchive(id: String): Outcome<InventoryArchive?>

    /** 新建或更新档案；返回档案 ID。 */
    suspend fun saveArchive(archive: InventoryArchive): Outcome<String>

    suspend fun softDeleteArchive(
        id: String,
        at: Instant,
    ): Outcome<Unit>

    suspend fun findArchiveForSauce(sauceId: String): Outcome<InventoryArchive?>

    suspend fun archiveLinks(archiveId: String): Outcome<List<ArchiveProductLink>>

    suspend fun linkProduct(
        archiveId: String,
        productId: String,
        conversionRatio: Double,
        isDefault: Boolean,
    ): Outcome<Unit>

    suspend fun unlinkProduct(
        archiveId: String,
        productId: String,
    ): Outcome<Unit>

    suspend fun archiveForProduct(productId: String): Outcome<String?>

    /** 按食材找到对应的库存档案（经档案-商品关联反查）；用于菜谱出库展开。 */
    suspend fun archiveForIngredient(ingredientId: com.culinary.core.domain.model.IngredientId): Outcome<String?>

    // ---- 批次 ----
    suspend fun listBatches(archiveId: String): Outcome<List<InventoryBatchRecord>>

    suspend fun listLiveBatches(): Outcome<List<InventoryBatchRecord>>

    suspend fun findBatch(batchId: String): Outcome<InventoryBatchRecord?>

    suspend fun listFifoBatches(archiveId: String): Outcome<List<InventoryBatchRecord>>

    suspend fun listRecycledBatches(): Outcome<List<InventoryBatchRecord>>

    suspend fun listTemporaryBatches(): Outcome<List<InventoryBatchRecord>>

    suspend fun countBatches(archiveId: String): Outcome<Int>

    suspend fun createBatch(batch: NewArchiveBatch): Outcome<String>

    /** 追加流水并原子调整批次余额；返回 false 表示幂等键已存在、本次未写入。 */
    suspend fun appendLedger(entry: InventoryLedgerEntry): Outcome<Boolean>

    suspend fun findLedgerByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?>

    suspend fun ledgerForArchive(archiveId: String): Outcome<List<InventoryLedgerEntry>>

    suspend fun ledgerForBatch(batchId: String): Outcome<List<InventoryLedgerEntry>>

    suspend fun recentLedger(limit: Int): Outcome<List<InventoryLedgerEntry>>

    suspend fun findLedger(id: String): Outcome<InventoryLedgerEntry?>

    suspend fun ledgerByReference(
        refType: String,
        refId: String,
    ): Outcome<List<InventoryLedgerEntry>>

    // ---- 冻结 / 转移 ----
    suspend fun freezeBatch(
        batchId: String,
        reason: String,
        operator: String?,
        at: Instant,
    ): Outcome<Unit>

    suspend fun unfreezeBatch(
        batchId: String,
        reason: String,
        operator: String?,
        at: Instant,
    ): Outcome<Unit>

    suspend fun freezeRecords(batchId: String): Outcome<List<FreezeRecord>>

    /** 批次库位转移；不新建批次、不改变总量，写零数量变动的转移流水。 */
    suspend fun transferBatch(
        batchId: String,
        toLocationId: String,
        note: String?,
        at: Instant,
    ): Outcome<Unit>

    suspend fun transfers(batchId: String): Outcome<List<LocationTransferRecord>>

    /** 回收站恢复（保留期内的归零批次）；只恢复批次身份，余额保持原值。 */
    suspend fun restoreBatch(
        batchId: String,
        at: Instant,
    ): Outcome<Unit>

    /** 回收站到期清理：标记为已报废，保留全部历史。 */
    suspend fun purgeBatch(
        batchId: String,
        at: Instant,
    ): Outcome<Unit>

    /** 临时库存转正式（清除临时标记）。 */
    suspend fun markTemporary(
        batchId: String,
        temporary: Boolean,
        at: Instant,
    ): Outcome<Unit>

    // ---- 出库单 ----
    suspend fun saveIssueSlip(slip: NewIssueSlip): Outcome<IssueSlip>

    suspend fun findIssueSlipByKey(key: String): Outcome<IssueSlip?>

    suspend fun findIssueSlip(id: String): Outcome<IssueSlip?>

    suspend fun recentIssueSlips(limit: Int): Outcome<List<IssueSlip>>

    suspend fun issueTypeCounts(): Outcome<Map<IssueType, Int>>

    // ---- 打印任务 ----
    suspend fun createPrintJob(
        kind: PrintKind,
        refType: String,
        refId: String,
        paperWidthMm: Int,
        at: Instant,
    ): Outcome<PrintJobRecord>

    suspend fun updatePrintJob(
        id: String,
        status: PrintStatus,
        lastError: String?,
        at: Instant,
        countReprint: Boolean = false,
    ): Outcome<PrintJobRecord>

    suspend fun latestPrintJob(
        refType: String,
        refId: String,
    ): Outcome<PrintJobRecord?>

    suspend fun printJob(id: String): Outcome<PrintJobRecord?>

    suspend fun recentPrintJobs(limit: Int): Outcome<List<PrintJobRecord>>

    // ---- 盘点 ----
    suspend fun createStocktake(session: NewStocktake): Outcome<Stocktake>

    suspend fun findStocktake(id: String): Outcome<Stocktake?>

    suspend fun recentStocktakes(limit: Int): Outcome<List<Stocktake>>

    suspend fun updateStocktakeCounted(
        stocktakeId: String,
        lineId: String,
        countedQty: Double,
    ): Outcome<Stocktake>

    /** 只标记盘点已确认；实际库存调整由用例逐行写调整流水。 */
    suspend fun confirmStocktake(
        id: String,
        at: Instant,
    ): Outcome<Stocktake>

    // ---- 库位 ----
    suspend fun listLocations(): Outcome<List<StorageLocation>>

    suspend fun saveLocation(
        location: StorageLocation,
        kind: String?,
        sortOrder: Int,
    ): Outcome<LocationId>

    suspend fun batchCountAtLocation(locationId: String): Outcome<Int>

    // ---- 规则 ----
    suspend fun readRules(): InventoryRules

    suspend fun writeRules(rules: InventoryRules): Outcome<Unit>
}
