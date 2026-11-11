package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.FreezeAction
import com.culinary.core.domain.model.FreezeRecord
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.InventoryRules
import com.culinary.core.domain.model.IssueSlip
import com.culinary.core.domain.model.IssueSlipLine
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.LocationTransferRecord
import com.culinary.core.domain.model.NewArchiveBatch
import com.culinary.core.domain.model.NewIssueSlip
import com.culinary.core.domain.model.NewStocktake
import com.culinary.core.domain.model.PrintJobRecord
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.PrintStatus
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Stocktake
import com.culinary.core.domain.model.StocktakeLine
import com.culinary.core.domain.model.StocktakeStatus
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import com.culinary.core.domain.model.Unit as MeasureUnit

/** 领域测试公共夹具：克单位、固定时钟、立即执行的事务边界。 */
internal val Gram: MeasureUnit =
    MeasureUnit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)

internal val TestInstant: Instant = Instant.ofEpochMilli(1_760_000_000_000L)

internal val TestClock: Clock = Clock { TestInstant }

internal object ImmediateRunner : TransactionRunner {
    override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
}

internal fun ingredientArchive(
    id: String,
    name: String,
): InventoryArchive =
    InventoryArchive(
        id = id,
        name = name,
        kind = ArchiveKind.INGREDIENT,
        stockUnit = Gram,
    )

internal fun sauceSpotArchive(
    id: String,
    name: String,
    sauceId: SauceId,
): InventoryArchive =
    InventoryArchive(
        id = id,
        name = name,
        kind = ArchiveKind.SAUCE,
        sauceId = sauceId.value,
        stockUnit = Gram,
    )

/** 取出成功值；失败时用断言错误暴露错误码，便于定位。 */
internal fun <T> Outcome<T>.successValue(): T =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> throw AssertionError("expected Success but was Failure: $error")
    }

/** 取出失败原因；成功时用断言错误暴露实际值。 */
internal fun Outcome<*>.failureError(): CulinaryError =
    when (this) {
        is Outcome.Failure -> error
        is Outcome.Success -> throw AssertionError("expected Failure but was Success: $value")
    }

/**
 * 内存版 [InventoryArchiveRepository]。
 *
 * 不是空壳：批次余额按流水真实扣减、流水按幂等键去重、FIFO 候选真实过滤冻结/回收批次，
 * 并记录写入调用次数，便于断言"整单失败不写任何东西"。
 */
@Suppress("TooManyFunctions")
internal class FakeInventoryArchiveRepository : InventoryArchiveRepository {
    val archives = linkedMapOf<String, InventoryArchive>()
    val batches = linkedMapOf<String, InventoryBatchRecord>()
    val ledger = mutableListOf<InventoryLedgerEntry>()
    val issueSlips = mutableListOf<IssueSlip>()
    val printJobs = mutableListOf<PrintJobRecord>()
    val stocktakes = linkedMapOf<String, Stocktake>()
    val freezeHistory = mutableListOf<FreezeRecord>()

    /** 食材 → 库存档案 ID；未登记的食材视为"没有库存档案"。 */
    val ingredientArchives = linkedMapOf<String, String>()

    var rules: InventoryRules = InventoryRules()

    /** `appendLedger` 被调用的次数（含被幂等键挡下的调用）。 */
    var appendLedgerAttempts = 0
        private set

    /** `saveIssueSlip` 被调用的次数（含幂等键命中已有单的调用）。 */
    var slipSaveCalls = 0
        private set

    /** 实际新建出库单的次数。 */
    var slipCreates = 0
        private set

    var printJobCalls = 0
        private set

    var lastSavedSlip: NewIssueSlip? = null
        private set

    private val slipByKey = mutableMapOf<String, IssueSlip>()

    // ---- 夹具 ----

    fun seedArchive(archive: InventoryArchive): InventoryArchive {
        archives[archive.id] = archive
        return archive
    }

    fun seedIngredientArchive(
        ingredientId: IngredientId,
        archiveId: String,
    ) {
        ingredientArchives[ingredientId.value] = archiveId
    }

    @Suppress("LongParameterList")
    fun seedBatch(
        id: String,
        archive: InventoryArchive,
        onHand: Double,
        receivedAt: Instant = TestInstant,
        reserved: Double = 0.0,
        status: BatchLifecycleStatus = BatchLifecycleStatus.ACTIVE,
    ): InventoryBatchRecord {
        val batch =
            InventoryBatchRecord(
                id = id,
                archiveId = archive.id,
                archiveName = archive.name,
                locationId = LocationId("loc-cold"),
                locationName = "冷库 A",
                batchCode = "B-$id",
                receivedAt = receivedAt,
                onHand = onHand,
                reserved = reserved,
                unit = archive.stockUnit,
                status = status,
                frozenAt = if (status == BatchLifecycleStatus.FROZEN) TestInstant else null,
                frozenReason = if (status == BatchLifecycleStatus.FROZEN) "测试冻结" else null,
            )
        batches[id] = batch
        return batch
    }

    fun ledgerFor(batchId: String): List<InventoryLedgerEntry> = ledger.filter { it.batchId.value == batchId }

    // ---- 档案 ----

    override suspend fun listArchives(): Outcome<List<InventoryArchive>> = Outcome.success(archives.values.toList())

    override suspend fun findArchive(id: String): Outcome<InventoryArchive?> = Outcome.success(archives[id])

    override suspend fun saveArchive(archive: InventoryArchive): Outcome<String> = error("unused")

    override suspend fun softDeleteArchive(
        id: String,
        at: Instant,
    ): Outcome<Unit> = error("unused")

    override suspend fun findArchiveForSauce(sauceId: String): Outcome<InventoryArchive?> =
        Outcome.success(archives.values.firstOrNull { it.sauceId == sauceId })

    override suspend fun archiveLinks(archiveId: String): Outcome<List<ArchiveProductLink>> = error("unused")

    override suspend fun linkProduct(
        archiveId: String,
        productId: String,
        conversionRatio: Double,
        isDefault: Boolean,
    ): Outcome<Unit> = error("unused")

    override suspend fun unlinkProduct(
        archiveId: String,
        productId: String,
    ): Outcome<Unit> = error("unused")

    override suspend fun archiveForProduct(productId: String): Outcome<String?> = error("unused")

    override suspend fun archiveForIngredient(ingredientId: IngredientId): Outcome<String?> =
        Outcome.success(ingredientArchives[ingredientId.value])

    // ---- 批次 ----

    override suspend fun listBatches(archiveId: String): Outcome<List<InventoryBatchRecord>> =
        Outcome.success(batches.values.filter { it.archiveId == archiveId })

    override suspend fun listLiveBatches(): Outcome<List<InventoryBatchRecord>> =
        Outcome.success(batches.values.filter { it.isLive })

    override suspend fun findBatch(batchId: String): Outcome<InventoryBatchRecord?> = Outcome.success(batches[batchId])

    /** FIFO 候选：仅 ACTIVE 且有可用量的批次，按入库时间升序；冻结/回收批次被真实过滤。 */
    override suspend fun listFifoBatches(archiveId: String): Outcome<List<InventoryBatchRecord>> =
        Outcome.success(
            batches.values
                .filter {
                    it.archiveId == archiveId &&
                        it.status == BatchLifecycleStatus.ACTIVE &&
                        it.available > EPSILON
                }.sortedBy { it.fifoKey },
        )

    override suspend fun listRecycledBatches(): Outcome<List<InventoryBatchRecord>> =
        Outcome.success(batches.values.filter { it.isRecycled })

    override suspend fun listTemporaryBatches(): Outcome<List<InventoryBatchRecord>> =
        Outcome.success(batches.values.filter { it.isTemporary })

    override suspend fun countBatches(archiveId: String): Outcome<Int> =
        Outcome.success(batches.values.count { it.archiveId == archiveId })

    override suspend fun createBatch(batch: NewArchiveBatch): Outcome<String> = error("unused")

    /** 真实扣减批次余额并按幂等键去重；返回 false 表示该键已有流水、本次未写入。 */
    override suspend fun appendLedger(entry: InventoryLedgerEntry): Outcome<Boolean> {
        appendLedgerAttempts += 1
        if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
        val batch =
            batches[entry.batchId.value]
                ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", entry.batchId.value))
        val newOnHand = batch.onHand + entry.onHandDelta
        val newReserved = batch.reserved + entry.reservedDelta
        if (newOnHand < -EPSILON || newReserved < -EPSILON || newReserved > newOnHand + EPSILON) {
            return Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = entry.archiveId ?: batch.archiveId.orEmpty(),
                            requested = -entry.onHandDelta,
                            available = batch.available,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )
        }
        batches[batch.id] = batch.copy(onHand = newOnHand, reserved = newReserved)
        ledger += entry.copy(onHandAfter = newOnHand, reservedAfter = newReserved)
        return Outcome.success(true)
    }

    override suspend fun findLedgerByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
        Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

    override suspend fun ledgerForArchive(archiveId: String): Outcome<List<InventoryLedgerEntry>> =
        Outcome.success(ledger.filter { it.archiveId == archiveId })

    override suspend fun ledgerForBatch(batchId: String): Outcome<List<InventoryLedgerEntry>> =
        Outcome.success(ledgerFor(batchId))

    override suspend fun recentLedger(limit: Int): Outcome<List<InventoryLedgerEntry>> =
        Outcome.success(ledger.takeLast(limit))

    override suspend fun findLedger(id: String): Outcome<InventoryLedgerEntry?> =
        Outcome.success(ledger.firstOrNull { it.id.value == id })

    override suspend fun ledgerByReference(
        refType: String,
        refId: String,
    ): Outcome<List<InventoryLedgerEntry>> =
        Outcome.success(ledger.filter { it.referenceType == refType && it.referenceId == refId })

    // ---- 冻结 / 转移 ----

    override suspend fun freezeBatch(
        batchId: String,
        reason: String,
        operator: String?,
        at: Instant,
    ): Outcome<Unit> {
        val batch = batches[batchId] ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", batchId))
        batches[batchId] =
            batch.copy(status = BatchLifecycleStatus.FROZEN, frozenAt = at, frozenReason = reason)
        freezeHistory +=
            FreezeRecord("freeze-${freezeHistory.size + 1}", batchId, FreezeAction.FREEZE, reason, operator, at)
        return Outcome.success(Unit)
    }

    override suspend fun unfreezeBatch(
        batchId: String,
        reason: String,
        operator: String?,
        at: Instant,
    ): Outcome<Unit> {
        val batch = batches[batchId] ?: return Outcome.failure(CulinaryError.NotFound("inventory_batch", batchId))
        batches[batchId] =
            batch.copy(status = BatchLifecycleStatus.ACTIVE, frozenAt = null, frozenReason = null)
        freezeHistory +=
            FreezeRecord("freeze-${freezeHistory.size + 1}", batchId, FreezeAction.UNFREEZE, reason, operator, at)
        return Outcome.success(Unit)
    }

    override suspend fun freezeRecords(batchId: String): Outcome<List<FreezeRecord>> =
        Outcome.success(freezeHistory.filter { it.batchId == batchId })

    override suspend fun transferBatch(
        batchId: String,
        toLocationId: String,
        note: String?,
        at: Instant,
    ): Outcome<Unit> = error("unused")

    override suspend fun transfers(batchId: String): Outcome<List<LocationTransferRecord>> = error("unused")

    override suspend fun restoreBatch(
        batchId: String,
        at: Instant,
    ): Outcome<Unit> = error("unused")

    override suspend fun purgeBatch(
        batchId: String,
        at: Instant,
    ): Outcome<Unit> = error("unused")

    override suspend fun markTemporary(
        batchId: String,
        temporary: Boolean,
        at: Instant,
    ): Outcome<Unit> = error("unused")

    // ---- 出库单 ----

    override suspend fun saveIssueSlip(slip: NewIssueSlip): Outcome<IssueSlip> {
        slipSaveCalls += 1
        slipByKey[slip.idempotencyKey]?.let { return Outcome.success(it) }
        val created =
            IssueSlip(
                id = "slip-${issueSlips.size + 1}",
                slipNo = slip.slipNo,
                type = slip.type,
                recipeName = slip.recipeName,
                occurredAt = slip.occurredAt,
                note = slip.note,
                lines =
                    slip.lines.map { line ->
                        IssueSlipLine(
                            lineNo = line.lineNo,
                            archiveName = line.archiveName,
                            batchCode = line.batchCode,
                            locationName = line.locationName,
                            quantity = line.quantity,
                            unitSymbol = line.unitSymbol,
                            role = line.role,
                        )
                    },
            )
        issueSlips += created
        slipByKey[slip.idempotencyKey] = created
        slipCreates += 1
        lastSavedSlip = slip
        return Outcome.success(created)
    }

    override suspend fun findIssueSlipByKey(key: String): Outcome<IssueSlip?> = Outcome.success(slipByKey[key])

    override suspend fun findIssueSlip(id: String): Outcome<IssueSlip?> =
        Outcome.success(issueSlips.firstOrNull { it.id == id })

    override suspend fun recentIssueSlips(limit: Int): Outcome<List<IssueSlip>> =
        Outcome.success(issueSlips.takeLast(limit))

    override suspend fun issueTypeCounts(): Outcome<Map<IssueType, Int>> =
        Outcome.success(issueSlips.groupingBy { it.type }.eachCount())

    // ---- 打印任务 ----

    override suspend fun createPrintJob(
        kind: PrintKind,
        refType: String,
        refId: String,
        paperWidthMm: Int,
        at: Instant,
    ): Outcome<PrintJobRecord> {
        printJobCalls += 1
        val job =
            PrintJobRecord(
                id = "print-${printJobs.size + 1}",
                kind = kind,
                refType = refType,
                refId = refId,
                status = PrintStatus.PENDING,
                paperWidthMm = paperWidthMm,
                createdAt = at,
            )
        printJobs += job
        return Outcome.success(job)
    }

    override suspend fun updatePrintJob(
        id: String,
        status: PrintStatus,
        lastError: String?,
        at: Instant,
        countReprint: Boolean,
    ): Outcome<PrintJobRecord> = error("unused")

    override suspend fun latestPrintJob(
        refType: String,
        refId: String,
    ): Outcome<PrintJobRecord?> = error("unused")

    override suspend fun printJob(id: String): Outcome<PrintJobRecord?> = error("unused")

    override suspend fun recentPrintJobs(limit: Int): Outcome<List<PrintJobRecord>> = error("unused")

    // ---- 盘点 ----

    override suspend fun createStocktake(session: NewStocktake): Outcome<Stocktake> {
        val id = "stocktake-${stocktakes.size + 1}"
        val created =
            Stocktake(
                id = id,
                scope = session.scope,
                scopeLabel = session.scopeLabel,
                status = StocktakeStatus.DRAFT,
                note = session.note,
                createdAt = session.createdAt,
                lines =
                    session.lines.map { line ->
                        StocktakeLine(
                            id = "$id-line-${line.lineNo}",
                            lineNo = line.lineNo,
                            batchId = line.batchId,
                            archiveId = line.archiveId,
                            archiveName = line.archiveName,
                            batchCode = line.batchCode,
                            locationName = line.locationName,
                            bookQty = line.bookQty,
                            countedQty = line.bookQty,
                            unitSymbol = line.unitSymbol,
                            frozen = line.frozen,
                        )
                    },
            )
        stocktakes[id] = created
        return Outcome.success(created)
    }

    override suspend fun findStocktake(id: String): Outcome<Stocktake?> = Outcome.success(stocktakes[id])

    override suspend fun recentStocktakes(limit: Int): Outcome<List<Stocktake>> =
        Outcome.success(stocktakes.values.toList().takeLast(limit))

    override suspend fun updateStocktakeCounted(
        stocktakeId: String,
        lineId: String,
        countedQty: Double,
    ): Outcome<Stocktake> {
        val stocktake =
            stocktakes[stocktakeId]
                ?: return Outcome.failure(CulinaryError.NotFound("inventory_stocktake", stocktakeId))
        val updated =
            stocktake.copy(
                lines = stocktake.lines.map { if (it.id == lineId) it.copy(countedQty = countedQty) else it },
            )
        stocktakes[stocktakeId] = updated
        return Outcome.success(updated)
    }

    override suspend fun confirmStocktake(
        id: String,
        at: Instant,
    ): Outcome<Stocktake> {
        val stocktake =
            stocktakes[id] ?: return Outcome.failure(CulinaryError.NotFound("inventory_stocktake", id))
        val confirmed = stocktake.copy(status = StocktakeStatus.CONFIRMED, confirmedAt = at)
        stocktakes[id] = confirmed
        return Outcome.success(confirmed)
    }

    // ---- 库位 ----

    override suspend fun listLocations(): Outcome<List<StorageLocation>> = error("unused")

    override suspend fun saveLocation(
        location: StorageLocation,
        kind: String?,
        sortOrder: Int,
    ): Outcome<LocationId> = error("unused")

    override suspend fun batchCountAtLocation(locationId: String): Outcome<Int> = error("unused")

    // ---- 规则 ----

    override suspend fun readRules(): InventoryRules = rules

    override suspend fun writeRules(rules: InventoryRules): Outcome<Unit> {
        this.rules = rules
        return Outcome.success(Unit)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

internal class FakeRecipeRepository : RecipeRepository {
    val recipes = linkedMapOf<RecipeId, Recipe>()
    val versions = linkedMapOf<RecipeVersionId, RecipeVersion>()

    override fun observeActive(): Flow<List<Recipe>> = flowOf(recipes.values.toList())

    override suspend fun get(id: RecipeId): Outcome<Recipe> =
        recipes[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound("recipe", id.value))

    override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

    override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? =
        versions.values.firstOrNull { it.recipeId == recipeId && it.isCurrent }

    override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> =
        versions.values.filter { it.recipeId == recipeId }

    override suspend fun nextVersionNo(recipeId: RecipeId): Int =
        (versions.values.maxOfOrNull { it.versionNo } ?: 0) + 1

    override suspend fun save(recipe: Recipe): Outcome<RecipeId> {
        recipes[recipe.id] = recipe
        return Outcome.success(recipe.id)
    }

    override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId = error("unused")

    override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> = error("unused")
}

internal class FakeSauceRepository : SauceRepository {
    val sauces = linkedMapOf<SauceId, Sauce>()
    val versions = linkedMapOf<SauceVersionId, SauceVersion>()

    override fun observeActive(): Flow<List<Sauce>> = flowOf(sauces.values.toList())

    override suspend fun get(id: SauceId): Outcome<Sauce> =
        sauces[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound("sauce", id.value))

    override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
        versions[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound("sauce_version", id.value))

    override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> =
        versions.values.filter { it.sauceId == sauceId }

    override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? =
        versions.values.firstOrNull { it.sauceId == sauceId && it.isCurrent }

    override suspend fun nextVersionNo(sauceId: SauceId): Int = (versions.values.maxOfOrNull { it.versionNo } ?: 0) + 1

    override suspend fun save(sauce: Sauce): Outcome<SauceId> {
        sauces[sauce.id] = sauce
        return Outcome.success(sauce.id)
    }

    override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> = error("unused")

    override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> = error("unused")

    override suspend fun archive(id: SauceId): Outcome<Unit> = error("unused")
}

internal class FakeIngredientRepository : IngredientRepository {
    val ingredients = linkedMapOf<IngredientId, Ingredient>()

    override fun observeActive(): Flow<List<Ingredient>> = flowOf(ingredients.values.toList())

    override suspend fun get(id: IngredientId): Outcome<Ingredient> =
        ingredients[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

    override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = error("unused")

    override suspend fun archive(id: IngredientId): Outcome<Unit> = error("unused")
}

internal class FakeUnitRepository(
    vararg units: MeasureUnit,
) : UnitRepository {
    private val units = units.toList()

    override suspend fun all(): List<MeasureUnit> = units

    override suspend fun find(id: UnitId): MeasureUnit? = units.firstOrNull { it.id == id }

    override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> = emptyList()
}

/** 盘点路径不读分类树；这里显式失败，避免误用成 always-success 空壳。 */
internal object UnusedArchiveCategoryRepository : ArchiveCategoryRepository {
    override fun observeActive(): Flow<List<ArchiveCategory>> = error("unused")

    override fun observeAssignments(): Flow<List<ArchiveCategoryAssignment>> = error("unused")

    override suspend fun get(id: String): ArchiveCategory? = error("unused")

    override suspend fun getForRecord(
        kind: KitchenRecordKind,
        recordId: String,
    ): ArchiveCategory? = error("unused")

    override suspend fun assign(
        kind: KitchenRecordKind,
        recordId: String,
        categoryId: String,
    ): Outcome<Unit> = error("unused")

    override suspend fun clearTestBatch(testBatch: String): Outcome<Int> = error("unused")

    override suspend fun libraryOf(kind: KitchenRecordKind): ArchiveLibrary = error("unused")
}
