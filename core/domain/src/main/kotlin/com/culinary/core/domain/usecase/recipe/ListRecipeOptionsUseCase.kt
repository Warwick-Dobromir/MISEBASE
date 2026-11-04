package com.culinary.core.domain.usecase.recipe

import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.first

/** 子菜谱选择项：档案 + 当前版本（可为空，表示尚无可引用版本）。 */
data class RecipeOption(
    val recipe: Recipe,
    val currentVersion: RecipeVersion?,
)

/** 读取可引用的菜谱与各自当前版本，供编辑器选择子菜谱。 */
class ListRecipeOptionsUseCase(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(): List<RecipeOption> =
        repository.observeActive().first().map { recipe ->
            RecipeOption(recipe = recipe, currentVersion = repository.getCurrentVersion(recipe.id))
        }
}
