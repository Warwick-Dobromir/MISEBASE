package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 保存菜谱草稿版本：解析"当前版本"引用并写入明确版本 ID。
 *
 * 草稿允许后续再编辑；已发布版本永不覆盖（D-006 / MB-D-013）。
 */
class SaveRecipeDraftUseCase(
    private val repository: RecipeRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    private val resolver = RecipeDraftResolver(repository)

    suspend operator fun invoke(input: RecipeVersionInput): Outcome<RecipeVersionId> {
        RecipeInputValidator.validate(input)?.let { return it }
        return transactionRunner
            .write {
                val id = RecipeVersionId(idGenerator.newId())
                val versionNo = repository.nextVersionNo(input.recipeId)
                val draft = resolver.toDraft(input, id, versionNo)
                when (val pinned = resolver.pin(draft)) {
                    is Outcome.Failure -> pinned
                    is Outcome.Success ->
                        Outcome.success(
                            repository.saveDraft(
                                RecipeInputValidator.version(
                                    input = input,
                                    id = id,
                                    versionNo = versionNo,
                                    status = VersionStatus.DRAFT,
                                    components = pinned.value.components,
                                ),
                            ),
                        )
                }
            }.flatMap { it }
    }
}
