package com.culinary.core.domain.usecase.kitchen

import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.repository.KitchenReferenceRepository
import kotlinx.coroutines.flow.Flow

/** 观察某一类厨房参考资料（技术或标准）。 */
class ListKitchenReferencesUseCase(
    private val repository: KitchenReferenceRepository,
) {
    operator fun invoke(kind: KitchenReferenceKind): Flow<List<KitchenReference>> = repository.observeActive(kind)

    operator fun invoke(): Flow<List<KitchenReference>> = repository.observeAllActive()
}
