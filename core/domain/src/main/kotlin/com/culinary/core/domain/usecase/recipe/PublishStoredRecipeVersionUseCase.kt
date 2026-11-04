package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 发布已保存的草稿版本。
 *
 * 已发布版本不允许再次发布（`CUL-CON-001`）；发布前重新校验子图循环。
 */
class PublishStoredRecipeVersionUseCase(
    private val repository: RecipeRepository,
    private val transactionRunner: TransactionRunner,
) {
    private val resolver = RecipeDraftResolver(repository)

    suspend operator fun invoke(versionId: RecipeVersionId): Outcome<RecipeVersionId> =
        transactionRunner
            .write {
                val version =
                    repository.getVersion(versionId)
                        ?: return@write Outcome.failure(
                            CulinaryError.NotFound(entity = "recipe_version", id = versionId.value),
                        )
                if (version.isPublished) {
                    Outcome.failure(CulinaryError.Conflict(entity = "recipe_version"))
                } else {
                    when (val valid = resolver.validateExisting(version)) {
                        is Outcome.Failure -> valid
                        is Outcome.Success ->
                            repository.savePublished(version.copy(status = VersionStatus.PUBLISHED))
                    }
                }
            }.flatMap { it }
}
