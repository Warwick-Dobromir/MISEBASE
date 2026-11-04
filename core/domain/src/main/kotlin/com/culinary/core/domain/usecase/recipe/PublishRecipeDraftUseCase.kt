package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.transaction.TransactionRunner

/**
 * 保存并发布菜谱版本（MB-D-013）。
 *
 * 发布时把"当前版本"解析为明确版本 ID，做循环校验后再写入 `PUBLISHED`；
 * 校验失败不写入半成品。
 */
class PublishRecipeDraftUseCase(
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
                    is Outcome.Success -> {
                        val version =
                            RecipeInputValidator.version(
                                input = input,
                                id = id,
                                versionNo = versionNo,
                                status = VersionStatus.PUBLISHED,
                                components = pinned.value.components,
                            )
                        when (val valid = resolver.validate(version, pinned.value.versions)) {
                            is Outcome.Failure -> valid
                            is Outcome.Success -> repository.savePublished(version)
                        }
                    }
                }
            }.flatMap { it }
    }
}
