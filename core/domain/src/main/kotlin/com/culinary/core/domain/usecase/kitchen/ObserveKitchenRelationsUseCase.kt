package com.culinary.core.domain.usecase.kitchen

import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.repository.KitchenRelationRepository
import kotlinx.coroutines.flow.Flow

/** 观察某条档案的关联；详情页用于展示可跳转关系。 */
class ObserveKitchenRelationsUseCase(
    private val repository: KitchenRelationRepository,
) {
    operator fun invoke(recordId: String): Flow<List<KitchenRelatedRecord>> = repository.observeForRecord(recordId)
}
