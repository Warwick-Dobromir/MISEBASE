package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelationDraft
import com.culinary.core.domain.repository.KitchenRelationRepository

/**
 * 保存菜谱的关联档案：酱汁与菜谱分两类写回，互不清除对方。
 *
 * 关系目标必须是真实档案 ID；删除关系只删除关系行，不删除被关联档案。
 */
class SaveRecipeRelationsUseCase(
    private val repository: KitchenRelationRepository,
) {
    suspend operator fun invoke(
        recipeId: String,
        sauceRelations: List<KitchenRelationDraft>,
        recipeRelations: List<KitchenRelationDraft>,
    ): Outcome<Unit> =
        repository
            .replaceRelations(
                sourceRecordId = recipeId,
                sourceKind = KitchenRecordKind.RECIPE,
                targetKind = KitchenRecordKind.SAUCE,
                relations = sauceRelations,
            ).flatMap {
                repository.replaceRelations(
                    sourceRecordId = recipeId,
                    sourceKind = KitchenRecordKind.RECIPE,
                    targetKind = KitchenRecordKind.RECIPE,
                    relations = recipeRelations,
                )
            }
}
