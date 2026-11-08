package com.culinary.core.domain.usecase.sauce

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelationDraft
import com.culinary.core.domain.repository.KitchenRelationRepository

/** 用编辑器的最终列表替换某酱汁的“衍生酱汁”关系；不重复、按顺序写回。 */
class SaveSauceRelationsUseCase(
    private val repository: KitchenRelationRepository,
) {
    suspend operator fun invoke(
        sauceId: String,
        relations: List<KitchenRelationDraft>,
    ): Outcome<Unit> =
        repository.replaceRelations(
            sourceRecordId = sauceId,
            sourceKind = KitchenRecordKind.SAUCE,
            targetKind = KitchenRecordKind.SAUCE,
            relations = relations,
        )
}
