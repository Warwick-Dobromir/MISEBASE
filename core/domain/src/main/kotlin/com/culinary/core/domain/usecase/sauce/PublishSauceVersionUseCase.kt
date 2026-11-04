package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 发布酱汁版本。
 *
 * 已发布版本不可再次发布（返回 `CUL-CON-001`），内容不可原地修改；
 * 发布后该版本成为档案的当前版本。
 */
class PublishSauceVersionUseCase(
    private val repository: SauceRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(versionId: SauceVersionId): Outcome<SauceVersionId> =
        transactionRunner
            .write {
                when (val result = repository.getVersion(versionId)) {
                    is Outcome.Failure -> result
                    is Outcome.Success -> {
                        val version = result.value
                        if (version.isPublished) {
                            Outcome.failure(CulinaryError.Conflict(entity = "sauce_version"))
                        } else {
                            repository.savePublished(version.copy(status = VersionStatus.PUBLISHED))
                        }
                    }
                }
            }.flatMap { it }
}
