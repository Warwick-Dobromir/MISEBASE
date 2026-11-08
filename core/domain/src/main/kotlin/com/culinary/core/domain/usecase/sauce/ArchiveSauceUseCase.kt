package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.transaction.TransactionRunner

/** 归档酱汁档案；不删除历史版本、组成行或图片。 */
class ArchiveSauceUseCase(
    private val repository: SauceRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(id: SauceId): Outcome<Unit> = transactionRunner.write { repository.archive(id) }
}
