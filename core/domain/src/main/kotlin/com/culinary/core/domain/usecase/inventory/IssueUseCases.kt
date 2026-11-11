package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.BatchAllocation
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.IssuePlan
import com.culinary.core.domain.model.IssuePlanLine
import com.culinary.core.domain.model.IssueResult
import com.culinary.core.domain.model.IssueRole
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.NewIssueSlip
import com.culinary.core.domain.model.NewIssueSlipLine
import com.culinary.core.domain.model.PrintKind
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceGap
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** 出库意图的一行：档案 + 角色 + 数量（已换算为档案库存单位）。 */
data class IssueIntentItem(
    val archiveId: String,
    val role: IssueRole,
    val quantity: Double,
    val manualBatchId: String? = null,
)

/** 出库意图：确认时在事务内重新按 FIFO 分配，避免使用过期计划。 */
data class IssueIntent(
    val type: IssueType,
    val items: List<IssueIntentItem>,
    val idempotencyKey: String,
    val note: String? = null,
    val recipeVersionId: String? = null,
    val recipeName: String? = null,
    val sauceGaps: List<SauceGap> = emptyList(),
    val operator: String? = null,
)

/** 按 FIFO 把数量分配到批次；冻结批次不参与，同一批次不会被重复占用。 */
class FifoBatchAllocator(
    private val repository: InventoryArchiveRepository,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    private val consumed = mutableMapOf<String, Double>()

    suspend fun allocate(
        archive: InventoryArchive,
        required: Double,
        manualBatchId: String? = null,
    ): Outcome<List<BatchAllocation>> {
        val batches =
            when (val result = repository.listFifoBatches(archive.id)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val candidates =
            if (manualBatchId != null) {
                batches.filter { it.id == manualBatchId }.also {
                    if (it.isEmpty()) {
                        return Outcome.failure(
                            CulinaryError.Validation(
                                field = "batchId",
                                rule = "batch is frozen, recycled or has no available quantity",
                            ),
                        )
                    }
                }
            } else {
                batches
            }
        val allocations = mutableListOf<BatchAllocation>()
        var remaining = required
        for (batch in candidates) {
            if (remaining <= EPSILON) break
            val already = consumed[batch.id] ?: 0.0
            val free = (batch.available - already).coerceAtLeast(0.0)
            if (free <= EPSILON) continue
            val take = minOf(remaining, free)
            allocations +=
                BatchAllocation(
                    batchId = batch.id,
                    batchCode = batch.batchCode,
                    locationId = batch.locationId,
                    locationName = batch.locationName,
                    quantity = take,
                    expiresOn = batch.expiresOn,
                )
            consumed[batch.id] = already + take
            remaining -= take
        }
        return Outcome.success(allocations)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/**
 * 手工出库计划：按 FIFO 分配到批次，冻结批次被跳过。
 *
 * 只读；确认由 [ConfirmIssueUseCase] 在事务内重新分配并写流水。
 */
class BuildManualIssuePlanUseCase(
    private val repository: InventoryArchiveRepository,
    private val unitRepository: UnitRepository,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(
        archiveId: String,
        quantity: Double,
        unitId: String,
        manualBatchId: String? = null,
    ): Outcome<IssuePlan> {
        if (!quantity.isFinite() || quantity <= 0.0) {
            return Outcome.failure(
                CulinaryError.Validation(field = "quantity", rule = "must be a finite positive number"),
            )
        }
        val archive =
            when (val result = repository.findArchive(archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("inventory_archive", archiveId))
            }
        val unit =
            unitRepository.find(
                com.culinary.core.domain.model
                    .UnitId(unitId),
            )
                ?: return Outcome.failure(CulinaryError.NotFound("unit", unitId))
        val converted =
            when (val result = unitConverter.convert(Quantity(quantity, unit), archive.stockUnit)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val allocations =
            when (
                val result =
                    FifoBatchAllocator(
                        repository,
                        unitConverter,
                    ).allocate(archive, converted.amount, manualBatchId)
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val line =
            IssuePlanLine(
                lineNo = 1,
                archiveId = archive.id,
                archiveName = archive.name,
                role =
                    if (archive.kind == com.culinary.core.domain.model.ArchiveKind.SAUCE) {
                        IssueRole.SAUCE
                    } else {
                        IssueRole.INGREDIENT
                    },
                unit = archive.stockUnit,
                required = converted.amount,
                allocations = allocations,
            )
        return Outcome.success(
            IssuePlan(type = IssueType.MANUAL, lines = listOf(line)),
        )
    }
}

/**
 * 菜谱出库计划（v9 三层检查）：
 *
 * 1. 菜谱直接使用的基础食材 → 扣对应档案批次；
 * 2. 菜谱使用的酱汁 → 先扣"已制作入库"的酱汁现货批次；
 * 3. 现货不足时，只对缺口按最小制作容量向上取整，展开酱汁配方计算基础食材。
 *
 * 任一项不足时整单失败（确认阶段统一校验），不会出现部分扣减。
 */
class BuildRecipeIssuePlanUseCase(
    private val repository: InventoryArchiveRepository,
    private val recipeRepository: RecipeRepository,
    private val sauceRepository: SauceRepository,
    private val ingredientRepository: IngredientRepository,
    private val unitRepository: UnitRepository,
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    suspend operator fun invoke(
        recipeVersionId: RecipeVersionId,
        servings: Double,
    ): Outcome<IssuePlan> {
        if (!servings.isFinite() || servings <= 0.0) {
            return Outcome.failure(
                CulinaryError.Validation(field = "servings", rule = "must be a finite positive number"),
            )
        }
        val root =
            recipeRepository.getVersion(recipeVersionId)
                ?: return Outcome.failure(
                    CulinaryError.NotFound("recipe_version", recipeVersionId.value),
                )
        val recipe =
            when (val result = recipeRepository.get(root.recipeId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val collected = CollectedPlan()
        val factor = servings / root.servings.toDouble()
        val error =
            expandRecipe(
                versionId = recipeVersionId,
                factor = factor,
                role = IssueRole.INGREDIENT,
                optionalPath = false,
                collected = collected,
                visiting = LinkedHashSet(),
            )
        if (error != null) return Outcome.failure(error)
        val lines =
            when (val result = allocate(collected)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        return Outcome.success(
            IssuePlan(
                type = IssueType.RECIPE,
                lines = lines,
                sauceGaps = collected.gaps.toList(),
                recipeVersionId = recipeVersionId.value,
                recipeName = recipe.name,
            ),
        )
    }

    @Suppress("LongParameterList", "ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun expandRecipe(
        versionId: RecipeVersionId,
        factor: Double,
        role: IssueRole,
        optionalPath: Boolean,
        collected: CollectedPlan,
        visiting: MutableSet<String>,
    ): CulinaryError? {
        val node = "recipe:${versionId.value}"
        if (!visiting.add(node)) {
            return CulinaryError.Domain(
                detail =
                    com.culinary.core.common.DomainDetail
                        .RecipeCycle(listOf(node)),
                code = com.culinary.core.common.ErrorCode.REP_001,
            )
        }
        val version =
            recipeRepository.getVersion(versionId)
                ?: return CulinaryError.NotFound("recipe_version", versionId.value)
        for (component in version.components) {
            if (component.isOptional) continue
            val scaled = Quantity(component.quantity.amount * factor, component.quantity.unit)
            val componentRole = if (optionalPath) role else role
            val error =
                when (val target = component.target) {
                    is ComponentTarget.Ingredient ->
                        addIngredient(target.id, scaled, componentRole, collected)

                    is ComponentTarget.Recipe -> {
                        val child =
                            recipeRepository.getVersion(target.versionId)
                                ?: return CulinaryError.NotFound("recipe_version", target.versionId.value)
                        val childFactor =
                            when (val converted = unitConverter.convert(scaled, child.yield.unit)) {
                                is Outcome.Failure -> return converted.error
                                is Outcome.Success -> converted.value.amount / child.yield.amount
                            }
                        expandRecipe(
                            versionId = target.versionId,
                            factor = childFactor,
                            role = componentRole,
                            optionalPath = optionalPath || component.isOptional,
                            collected = collected,
                            visiting = visiting,
                        )
                    }

                    is ComponentTarget.Sauce ->
                        expandSauce(target.versionId, scaled, collected)
                }
            if (error != null) {
                visiting -= node
                return error
            }
        }
        visiting -= node
        return null
    }

    @Suppress("LongParameterList", "ReturnCount", "LongMethod")
    private suspend fun expandSauce(
        versionId: SauceVersionId,
        required: Quantity,
        collected: CollectedPlan,
    ): CulinaryError? {
        val sauceVersion =
            when (val result = sauceRepository.getVersion(versionId)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        val sauceName =
            when (val result = sauceRepository.get(sauceVersion.sauceId)) {
                is Outcome.Success -> result.value.name
                is Outcome.Failure -> "酱汁"
            }
        val archive =
            when (val result = repository.findArchiveForSauce(sauceVersion.sauceId.value)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        var spot = 0.0
        var requiredInArchiveUnit = required.amount
        if (archive != null) {
            val converted =
                when (val result = unitConverter.convert(required, archive.stockUnit)) {
                    is Outcome.Failure -> return result.error
                    is Outcome.Success -> result.value
                }
            requiredInArchiveUnit = converted.amount
            spot = availableOf(archive)
        }
        val useSpot = minOf(spot, requiredInArchiveUnit)
        if (archive != null && useSpot > EPSILON) {
            collected.sauceItems += SauceContribution(archive, useSpot)
            collected.names[archive.id] = archive.name
        }
        val gap = (requiredInArchiveUnit - useSpot).coerceAtLeast(0.0)
        if (gap <= EPSILON) {
            collected.gaps +=
                SauceGap(
                    sauceName = sauceName,
                    required = requiredInArchiveUnit,
                    spot = useSpot,
                    gap = 0.0,
                    productionQty = 0.0,
                    minimumBatch = sauceVersion.minRange?.amount,
                    unit = archive?.stockUnit ?: required.unit,
                )
            return null
        }
        val minimum = sauceVersion.minRange
        val productionQty =
            if (minimum == null) {
                gap
            } else {
                val gapInMinUnit =
                    when (
                        val converted =
                            unitConverter.convert(
                                Quantity(gap, archive?.stockUnit ?: required.unit),
                                minimum.unit,
                            )
                    ) {
                        is Outcome.Failure -> return converted.error
                        is Outcome.Success -> converted.value.amount
                    }
                ceil(gapInMinUnit / minimum.amount - 1e-9) * minimum.amount
            }
        val productionUnit = if (minimum != null) minimum.unit else (archive?.stockUnit ?: required.unit)
        collected.gaps +=
            SauceGap(
                sauceName = sauceName,
                required = requiredInArchiveUnit,
                spot = useSpot,
                gap = gap,
                productionQty = productionQty,
                minimumBatch = minimum?.amount,
                unit = archive?.stockUnit ?: required.unit,
            )
        val productionInYieldUnit =
            when (
                val converted =
                    unitConverter.convert(
                        Quantity(productionQty, productionUnit),
                        sauceVersion.yield.unit,
                    )
            ) {
                is Outcome.Failure -> return converted.error
                is Outcome.Success -> converted.value
            }
        val factor = productionInYieldUnit.amount / sauceVersion.yield.amount
        return expandSauceComponents(sauceVersion.id, factor, collected)
    }

    @Suppress("ReturnCount", "LongMethod")
    private suspend fun expandSauceComponents(
        versionId: SauceVersionId,
        factor: Double,
        collected: CollectedPlan,
    ): CulinaryError? {
        val version =
            when (val result = sauceRepository.getVersion(versionId)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        for (component in version.components) {
            if (component.isOptional) continue
            val scaled = Quantity(component.quantity.amount * factor, component.quantity.unit)
            val error =
                when (val target = component.target) {
                    is ComponentTarget.Ingredient ->
                        addIngredient(target.id, scaled, IssueRole.SAUCE_GAP, collected)

                    is ComponentTarget.Recipe ->
                        expandRecipe(
                            versionId = target.versionId,
                            factor = factor,
                            role = IssueRole.SAUCE_GAP,
                            optionalPath = false,
                            collected = collected,
                            visiting = LinkedHashSet(),
                        )

                    is ComponentTarget.Sauce ->
                        expandSauce(target.versionId, scaled, collected)
                }
            if (error != null) return error
        }
        return null
    }

    @Suppress("ReturnCount")
    private suspend fun addIngredient(
        ingredientId: com.culinary.core.domain.model.IngredientId,
        quantity: Quantity,
        role: IssueRole,
        collected: CollectedPlan,
    ): CulinaryError? {
        val archiveId =
            when (val result = repository.archiveForIngredient(ingredientId)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        val ingredientName =
            when (val result = ingredientRepository.get(ingredientId)) {
                is Outcome.Success -> result.value.name
                is Outcome.Failure -> ingredientId.value
            }
        if (archiveId == null) {
            return CulinaryError.Validation(
                field = "archive",
                rule = "食材「$ingredientName」没有库存档案，请先建立档案再出库",
            )
        }
        val archive =
            when (val result = repository.findArchive(archiveId)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success ->
                    result.value
                        ?: return CulinaryError.NotFound("inventory_archive", archiveId)
            }
        val conversions = unitRepository.conversionsFor(ingredientId)
        val converted =
            when (val result = unitConverter.convert(quantity, archive.stockUnit, conversions, ingredientId)) {
                is Outcome.Failure -> return result.error
                is Outcome.Success -> result.value
            }
        collected.ingredientItems += IngredientContribution(archive, role, converted.amount)
        collected.names[archive.id] = archive.name
        return null
    }

    private suspend fun availableOf(archive: InventoryArchive): Double {
        val batches =
            when (val result = repository.listFifoBatches(archive.id)) {
                is Outcome.Failure -> return 0.0
                is Outcome.Success -> result.value
            }
        return batches.filter { !it.isFrozen }.sumOf { it.available }
    }

    @Suppress("LongMethod")
    private suspend fun allocate(collected: CollectedPlan): Outcome<List<IssuePlanLine>> {
        val allocator = FifoBatchAllocator(repository, unitConverter)
        val grouped = mutableListOf<Triple<InventoryArchive, IssueRole, Double>>()
        collected.ingredientItems.forEach { contribution ->
            val existing =
                grouped.indexOfFirst {
                    it.first.id == contribution.archive.id &&
                        it.second == contribution.role
                }
            if (existing >= 0) {
                val current = grouped[existing]
                grouped[existing] = Triple(current.first, current.second, current.third + contribution.quantity)
            } else {
                grouped += Triple(contribution.archive, contribution.role, contribution.quantity)
            }
        }
        collected.sauceItems.forEach { contribution ->
            grouped += Triple(contribution.archive, IssueRole.SAUCE, contribution.quantity)
        }
        val lines = mutableListOf<IssuePlanLine>()
        grouped.forEachIndexed { index, (archive, role, quantity) ->
            val allocations =
                when (val result = allocator.allocate(archive, quantity)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            lines +=
                IssuePlanLine(
                    lineNo = index + 1,
                    archiveId = archive.id,
                    archiveName = archive.name,
                    role = role,
                    unit = archive.stockUnit,
                    required = quantity,
                    allocations = allocations,
                )
        }
        return Outcome.success(lines)
    }

    private class CollectedPlan {
        val ingredientItems = mutableListOf<IngredientContribution>()
        val sauceItems = mutableListOf<SauceContribution>()
        val gaps = mutableListOf<SauceGap>()
        val names = mutableMapOf<String, String>()
    }

    private data class IngredientContribution(
        val archive: InventoryArchive,
        val role: IssueRole,
        val quantity: Double,
    )

    private data class SauceContribution(
        val archive: InventoryArchive,
        val quantity: Double,
    )

    private companion object {
        const val EPSILON = 1e-9
    }
}

/**
 * 确认出库（v9）：整单校验 + 原子扣减。
 *
 * 在事务内重新按 FIFO 分配；任一行不足直接失败，不写任何流水；
 * 全部通过后统一写流水、出库单明细与打印任务，并用幂等键防重复提交。
 */
class ConfirmIssueUseCase(
    private val repository: InventoryArchiveRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(intent: IssueIntent): Outcome<IssueResult> =
        transactionRunner
            .write { execute(intent) }
            .flatMap { it }

    @Suppress("LongMethod", "ReturnCount")
    private suspend fun execute(intent: IssueIntent): Outcome<IssueResult> {
        if (intent.items.isEmpty()) {
            return Outcome.failure(
                CulinaryError.Validation(field = "items", rule = "issue needs at least one material"),
            )
        }
        repository.findIssueSlipByKey(intent.idempotencyKey).let { result ->
            when (result) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value?.let { slip ->
                        return Outcome.success(
                            IssueResult(slip = slip, ledgerEntries = 0, idempotentReplay = true),
                        )
                    }
            }
        }
        val allocator = FifoBatchAllocator(repository)
        val planned = mutableListOf<PlannedLine>()
        for (item in intent.items) {
            val archive =
                when (val result = repository.findArchive(item.archiveId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success ->
                        result.value
                            ?: return Outcome.failure(
                                CulinaryError.NotFound("inventory_archive", item.archiveId),
                            )
                }
            val allocations =
                when (val result = allocator.allocate(archive, item.quantity, item.manualBatchId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            val allocated = allocations.sumOf { it.quantity }
            if (allocated + 1e-9 < item.quantity) {
                return Outcome.failure(
                    CulinaryError.Domain(
                        detail =
                            com.culinary.core.common.DomainDetail.InsufficientStock(
                                productId = archive.id,
                                requested = item.quantity,
                                available = allocated,
                            ),
                        code = com.culinary.core.common.ErrorCode.INV_003,
                    ),
                )
            }
            planned += PlannedLine(archive, item.role, item.quantity, allocations)
        }

        val now = clock.now()
        val slipId = idGenerator.newId()
        val slipNo = slipNumber(now, slipId)
        val lines = mutableListOf<NewIssueSlipLine>()
        planned.forEachIndexed { lineIndex, line ->
            line.allocations.forEach { allocation ->
                lines +=
                    NewIssueSlipLine(
                        lineNo = lines.size + 1,
                        archiveId = line.archive.id,
                        archiveName = line.archive.name,
                        batchId = allocation.batchId,
                        batchCode = allocation.batchCode,
                        locationId = allocation.locationId,
                        locationName = allocation.locationName,
                        quantity = allocation.quantity,
                        unitId = line.archive.stockUnit.id.value,
                        unitSymbol = line.archive.stockUnit.symbol,
                        role = line.role,
                    )
            }
        }
        val slip =
            when (
                val saved =
                    repository.saveIssueSlip(
                        NewIssueSlip(
                            slipNo = slipNo,
                            type = intent.type,
                            occurredAt = now,
                            idempotencyKey = intent.idempotencyKey,
                            recipeVersionId = intent.recipeVersionId,
                            recipeName = intent.recipeName,
                            note = intent.note,
                            operator = intent.operator,
                            lines = lines,
                        ),
                    )
            ) {
                is Outcome.Failure -> return saved
                is Outcome.Success -> saved.value
            }

        var written = 0
        planned.forEachIndexed { lineIndex, line ->
            line.allocations.forEachIndexed { allocationIndex, allocation ->
                val batchNow =
                    when (val result = repository.findBatch(allocation.batchId)) {
                        is Outcome.Failure -> return result
                        is Outcome.Success -> result.value
                    }
                val onHandNow = batchNow?.onHand ?: 0.0
                val entry =
                    InventoryLedgerEntry(
                        id = LedgerEntryId(idGenerator.newId()),
                        batchId =
                            com.culinary.core.domain.model
                                .BatchId(allocation.batchId),
                        productId = null,
                        locationId = allocation.locationId,
                        type = LedgerEntryType.PRODUCTION_ISSUE,
                        stockUnit = line.archive.stockUnit,
                        onHandDelta = -allocation.quantity,
                        reservedDelta = 0.0,
                        onHandAfter = onHandNow - allocation.quantity,
                        reservedAfter = batchNow?.reserved ?: 0.0,
                        idempotencyKey = "issue:${slip.id}:$lineIndex:$allocationIndex",
                        occurredAt = now,
                        referenceType = REFERENCE_TYPE,
                        referenceId = slip.id,
                        note = intent.note,
                        archiveId = line.archive.id,
                        operator = intent.operator,
                    )
                when (val appended = repository.appendLedger(entry)) {
                    is Outcome.Failure -> return appended
                    is Outcome.Success -> if (appended.value) written += 1
                }
            }
        }

        val printJob =
            when (
                val job =
                    repository.createPrintJob(
                        kind = PrintKind.ISSUE,
                        refType = REFERENCE_TYPE,
                        refId = slip.id,
                        paperWidthMm = 80,
                        at = now,
                    )
            ) {
                is Outcome.Failure -> null
                is Outcome.Success -> job.value
            }
        val slips =
            when (val result = repository.findIssueSlip(slip.id)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value ?: slip
            }
        return Outcome.success(
            IssueResult(
                slip = slips,
                ledgerEntries = written,
                printJob = printJob,
            ),
        )
    }

    private fun slipNumber(
        now: Instant,
        slipId: String,
    ): String {
        val stamp =
            DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneId.systemDefault())
                .format(now)
        return "ISS-$stamp-${slipId.takeLast(4).uppercase()}"
    }

    private class PlannedLine(
        val archive: InventoryArchive,
        val role: IssueRole,
        val required: Double,
        val allocations: List<BatchAllocation>,
    )

    private companion object {
        const val REFERENCE_TYPE = "issue_slip"
    }
}
