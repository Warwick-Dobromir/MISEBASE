package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.ProductRequirement
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementDeductionResult
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.inventory.InventoryDeductionPlanner
import com.culinary.core.domain.rule.unit.UnitConverter
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 确认需求扣减（MB-D-014）并落计算记录（MB-D-017）。
 *
 * 一个事务内重新构建计划、确认记录、按商品扣减；任何缺口或流水失败都会回滚，
 * 不会留下"记录已确认但库存未扣"的半成品。
 */
class ConfirmRequirementDeductionUseCase(
    private val buildPlan: BuildRequirementPlanUseCase,
    private val inventoryRepository: InventoryRepository,
    private val unitRepository: UnitRepository,
    private val calculationRepository: CalculationRecordRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val unitConverter: UnitConverter = UnitConverter(),
    private val planner: InventoryDeductionPlanner = InventoryDeductionPlanner(),
) {
    suspend operator fun invoke(
        recipeVersionId: RecipeVersionId,
        targetServings: Double,
    ): Outcome<RequirementDeductionResult> =
        transactionRunner
            .write { execute(recipeVersionId, targetServings) }
            .flatMap { it }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private suspend fun execute(
        recipeVersionId: RecipeVersionId,
        targetServings: Double,
    ): Outcome<RequirementDeductionResult> {
        val plan =
            when (val result = buildPlan(recipeVersionId, targetServings)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (plan.hasRequiredShortfall) {
            val first = plan.requiredLines.first { !it.isCovered }
            return Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = first.productId?.value.orEmpty(),
                            requested = first.shortfall.amount,
                            available = first.available.amount,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )
        }
        val confirmed =
            when (val result = confirm(plan.record)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val requirements = mutableListOf<ProductRequirement>()
        for (line in plan.requiredLines) {
            val productId =
                line.productId
                    ?: return Outcome.failure(
                        CulinaryError.Validation(
                            field = "productId",
                            rule = "stock product is required for ingredient ${line.ingredientId.value}",
                        ),
                    )
            val product =
                when (val result = inventoryRepository.findProduct(productId)) {
                    is Outcome.Failure -> return result
                    is Outcome.Success ->
                        result.value
                            ?: return Outcome.failure(
                                CulinaryError.NotFound("product", productId.value),
                            )
                }
            val conversions = unitRepository.conversionsFor(line.ingredientId)
            val converted =
                when (
                    val result =
                        unitConverter.convert(
                            line.required,
                            product.stockUnit,
                            conversions,
                            line.ingredientId,
                        )
                ) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            requirements +=
                ProductRequirement(
                    productId = product.id,
                    quantity = Quantity(converted.amount, product.stockUnit),
                    label = line.ingredientName,
                )
        }
        val items =
            when (val result = inventoryRepository.listItems()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val planEntries =
            when (
                val result =
                    planner.plan(
                        requirements = requirements,
                        items = items,
                        referenceType = REFERENCE_TYPE,
                        referenceId = confirmed.id.value,
                        occurredAt = clock.now(),
                        idGenerator = idGenerator,
                    )
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        var applied = 0
        for (entry in planEntries.entries) {
            when (val result = inventoryRepository.appendIfAbsent(entry)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> if (result.value) applied += 1
            }
        }
        return Outcome.success(
            RequirementDeductionResult(plan = plan, record = confirmed, appliedEntryCount = applied),
        )
    }

    private suspend fun confirm(record: CalculationRecord): Outcome<CalculationRecord> =
        when (val confirmed = record.confirm(clock.now())) {
            is Outcome.Failure -> confirmed
            is Outcome.Success -> calculationRepository.saveConfirmed(confirmed.value)
        }

    private companion object {
        const val REFERENCE_TYPE = "calculation_record"
    }
}

/** 读取某个菜谱版本的计算记录，供回放列表使用。 */
class ListCalculationRecordsUseCase(
    private val repository: CalculationRecordRepository,
) {
    suspend operator fun invoke(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>> =
        repository.findByRecipeVersion(recipeVersionId)
}

/** 按 ID 回放一条已确认的计算记录。 */
class GetCalculationRecordUseCase(
    private val repository: CalculationRecordRepository,
) {
    suspend operator fun invoke(id: CalculationRecordId): Outcome<CalculationRecord?> = repository.find(id)
}
