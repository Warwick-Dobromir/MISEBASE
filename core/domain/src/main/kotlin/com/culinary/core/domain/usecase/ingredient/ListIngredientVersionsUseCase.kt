package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientVersion
import com.culinary.core.domain.repository.IngredientRepository
import javax.inject.Inject

/** 读取食材不可变历史版本。 */
class ListIngredientVersionsUseCase @Inject constructor(
    private val repository: IngredientRepository,
) {
    suspend operator fun invoke(id: IngredientId): List<IngredientVersion> = repository.listVersions(id)
}
