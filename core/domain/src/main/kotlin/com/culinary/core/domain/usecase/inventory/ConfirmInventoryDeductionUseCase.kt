package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.ProductRequirement
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.rule.inventory.DeductionPlan
import com.culinary.core.domain.rule.inventory.InventoryDeductionPlanner
import com.culinary.core.domain.rule.inventory.InventoryPreview
import com.culinary.core.domain.rule.inventory.InventoryPreviewCalculator
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.Instant

/** 确认扣减的输入；[referenceId] 是幂等作用域（计算记录/计划 ID）。 */
data class ConfirmInventoryDeductionInput(
    val requirements: List<ProductRequirement>,
    val referenceId: String,
    val occurredAt: Instant,
    val referenceType: String = "calculation_record",
) {
    init {
        require(referenceId.isNotBlank()) { "referenceId must not be blank" }
    }
}

/** 确认结果：预览、实际写入的流水、因幂等键已存在而跳过的键。 */
data class InventoryDeductionResult(
    val preview: InventoryPreview,
    val appliedEntries: List<InventoryLedgerEntry>,
    val skippedIdempotentKeys: List<String>,
) {
    val wroteEntries: Boolean get() = appliedEntries.isNotEmpty()
}

/** 预览需求/可用/缺口（MB-D-014）；只读，不写流水、不建预留。 */
class PreviewInventoryDeductionUseCase(
    private val inventoryRepository: InventoryRepository,
    private val calculator: InventoryPreviewCalculator = InventoryPreviewCalculator(),
) {
    suspend operator fun invoke(requirements: List<ProductRequirement>): Outcome<InventoryPreview> =
        when (val items = inventoryRepository.listItems()) {
            is Outcome.Failure -> items
            is Outcome.Success -> calculator.preview(requirements, items.value)
        }
}

/**
 * 确认扣减用例（MB-D-014）。
 *
 * 确认在**一个事务**内重新读取库存快照、生成计划并追加流水。
 * 不创建预留；幂等键重复时跳过写入，保证重复确认不重复扣减。
 */
class ConfirmInventoryDeductionUseCase(
    private val inventoryRepository: InventoryRepository,
    private val transactionRunner: TransactionRunner,
    private val idGenerator: IdGenerator,
    private val planner: InventoryDeductionPlanner = InventoryDeductionPlanner(),
) {
    suspend operator fun invoke(input: ConfirmInventoryDeductionInput): Outcome<InventoryDeductionResult> =
        transactionRunner
            .write {
                when (val items = inventoryRepository.listItems()) {
                    is Outcome.Failure -> items
                    is Outcome.Success ->
                        when (
                            val plan =
                                planner.plan(
                                    requirements = input.requirements,
                                    items = items.value,
                                    referenceType = input.referenceType,
                                    referenceId = input.referenceId,
                                    occurredAt = input.occurredAt,
                                    idGenerator = idGenerator,
                                )
                        ) {
                            is Outcome.Failure -> plan
                            is Outcome.Success -> appendAll(plan.value)
                        }
                }
            }.flatMap { it }

    private suspend fun appendAll(plan: DeductionPlan): Outcome<InventoryDeductionResult> {
        val applied = mutableListOf<InventoryLedgerEntry>()
        val skipped = mutableListOf<String>()
        for (entry in plan.entries) {
            when (val appended = inventoryRepository.appendIfAbsent(entry)) {
                is Outcome.Failure -> return appended
                is Outcome.Success ->
                    if (appended.value) {
                        applied += entry
                    } else {
                        skipped += entry.idempotencyKey
                    }
            }
        }
        return Outcome.success(
            InventoryDeductionResult(
                preview = plan.preview,
                appliedEntries = applied,
                skippedIdempotentKeys = skipped,
            ),
        )
    }
}
