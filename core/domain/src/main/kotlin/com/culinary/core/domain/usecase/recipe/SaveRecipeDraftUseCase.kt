package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
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
 * [RecipeVersionInput.versionId] 非空时更新同一份未发布草稿，避免每次保存都产生重复草稿；
 * 为空时创建新草稿。已发布版本永不覆盖（D-006 / MB-D-013）。
 */
class SaveRecipeDraftUseCase(
    private val repository: RecipeRepository,
    private val idGenerator: IdGenerator,
    private val transactionRunner: TransactionRunner,
) {
    private val resolver = RecipeDraftResolver(repository)

    suspend operator fun invoke(input: RecipeVersionInput): Outcome<RecipeVersionId> {
        RecipeInputValidator.validate(input)?.let { return it }
        val existing =
            input.versionId?.let { versionId ->
                val version = repository.getVersion(versionId)
                if (version == null) {
                    return invalid("recipe.version", "draft not found")
                }
                if (version.status != VersionStatus.DRAFT) {
                    return invalid("recipe.version", "only draft versions can be edited")
                }
                if (version.recipeId != input.recipeId) {
                    return invalid("recipe.version", "draft belongs to another recipe")
                }
                version
            }
        return transactionRunner
            .write {
                val id = existing?.id ?: RecipeVersionId(idGenerator.newId())
                val versionNo = existing?.versionNo ?: repository.nextVersionNo(input.recipeId)
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

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}
