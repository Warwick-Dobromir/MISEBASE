package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.PurchaseListResult
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 生成采购清单（MB-D-016）并落一条确认的计算记录（MB-D-017）。
 *
 * 清单本身不落库；只有计算记录与需求行持久化，供之后回放。
 */
class GeneratePurchaseListUseCase(
    private val buildPlan: BuildRequirementPlanUseCase,
    private val calculationRepository: CalculationRecordRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        recipeVersionId: RecipeVersionId,
        targetServings: Double,
    ): Outcome<PurchaseListResult> =
        transactionRunner
            .write {
                when (val plan = buildPlan(recipeVersionId, targetServings)) {
                    is Outcome.Failure -> plan
                    is Outcome.Success -> {
                        val confirmed =
                            when (val record = confirm(plan.value.record)) {
                                is Outcome.Failure -> return@write record
                                is Outcome.Success -> record.value
                            }
                        Outcome.success(PurchaseListResult(plan.value, confirmed))
                    }
                }
            }.flatMap { it }

    private suspend fun confirm(record: CalculationRecord): Outcome<CalculationRecord> =
        when (val confirmed = record.confirm(clock.now())) {
            is Outcome.Failure -> confirmed
            is Outcome.Success -> calculationRepository.saveConfirmed(confirmed.value)
        }
}
