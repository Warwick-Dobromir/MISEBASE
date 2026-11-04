package com.culinary.core.domain.usecase.calculation

import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.transaction.TransactionRunner
import java.time.Instant

/**
 * 确认计算记录（MB-D-017）。
 *
 * 只在确认时落库；`PREVIEW` 不进仓库。确认后的记录不可变，
 * 重复确认由 [CalculationRecord.confirm] 拒绝。
 */
class ConfirmCalculationRecordUseCase(
    private val repository: CalculationRecordRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(
        record: CalculationRecord,
        confirmedAt: Instant,
    ): Outcome<CalculationRecord> =
        transactionRunner
            .write {
                when (val confirmed = record.confirm(confirmedAt)) {
                    is Outcome.Failure -> confirmed
                    is Outcome.Success -> repository.saveConfirmed(confirmed.value)
                }
            }.flatMap { it }
}
