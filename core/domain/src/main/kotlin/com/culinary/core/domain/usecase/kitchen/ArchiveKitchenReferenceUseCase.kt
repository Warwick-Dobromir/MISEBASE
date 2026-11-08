package com.culinary.core.domain.usecase.kitchen

import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.repository.KitchenReferenceRepository
import com.culinary.core.domain.transaction.TransactionRunner

/** 归档新增参考资料；记录保留，不物理删除。 */
class ArchiveKitchenReferenceUseCase(
    private val repository: KitchenReferenceRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(id: KitchenReferenceId): Outcome<Unit> =
        transactionRunner.write { repository.archive(id) }.flatMap { it }
}
