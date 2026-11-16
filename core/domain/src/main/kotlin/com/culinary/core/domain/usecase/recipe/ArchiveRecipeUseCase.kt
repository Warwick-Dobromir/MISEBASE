package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.transaction.TransactionRunner

/** 归档菜谱档案；不删除历史版本、组成行或图片。 */
class ArchiveRecipeUseCase(
    private val repository: RecipeRepository,
    private val transactionRunner: TransactionRunner,
) {
    suspend operator fun invoke(id: RecipeId): Outcome<Unit> =
        transactionRunner.write { repository.archive(id) }
}
