package com.culinary.core.domain.usecase.sauce

import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.repository.SauceRepository
import kotlinx.coroutines.flow.Flow

/** 观察在用酱汁列表。 */
class ListSaucesUseCase(
    private val repository: SauceRepository,
) {
    operator fun invoke(): Flow<List<Sauce>> = repository.observeActive()
}
