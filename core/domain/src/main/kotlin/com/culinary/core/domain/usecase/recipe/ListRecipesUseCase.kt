package com.culinary.core.domain.usecase.recipe

import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow

/** 观察在用菜谱列表。 */
class ListRecipesUseCase(
    private val repository: RecipeRepository,
) {
    operator fun invoke(): Flow<List<Recipe>> = repository.observeActive()
}
