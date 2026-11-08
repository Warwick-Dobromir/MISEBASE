package com.culinary.core.domain.usecase.kitchen

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.repository.KitchenReferenceRepository

/** 读取厨房参考资料详情。 */
class GetKitchenReferenceUseCase(
    private val repository: KitchenReferenceRepository,
) {
    suspend operator fun invoke(id: KitchenReferenceId): Outcome<KitchenReference> = repository.get(id)
}
