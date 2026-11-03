package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 归档食材（软删除，A06）。
 *
 * 归档后不再出现在在用列表，但记录保留；物理删除不提供入口。
 */
class ArchiveIngredientUseCase(
    private val repository: IngredientRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(id: IngredientId): Outcome<Unit> =
        transactionRunner.write { repository.archive(id) }.flatMap { it }
}
