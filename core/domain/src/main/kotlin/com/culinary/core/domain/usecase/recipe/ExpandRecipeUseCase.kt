package com.culinary.core.domain.usecase.recipe

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ExpandedRecipe
import com.culinary.core.domain.model.ExpandedRecipeLine
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.rule.recipe.RecipeExploder
import com.culinary.core.domain.rule.recipe.RecipeGraphSource

/**
 * 按目标份数展开菜谱（只做展示，不做库存/采购）。
 *
 * 可选配料显式包含并标记，供详情页展示；缺口计算默认排除由 Stage 4 处理。
 */
class ExpandRecipeUseCase(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
) {
    private val exploder = RecipeExploder()

    suspend operator fun invoke(
        versionId: RecipeVersionId,
        targetServings: Double,
    ): Outcome<ExpandedRecipe> {
        if (!targetServings.isFinite() || targetServings <= 0.0) {
            return Outcome.failure(CulinaryError.Validation(field = "servings", rule = "must be a finite positive number"))
        }
        val root = recipeRepository.getVersion(versionId)
            ?: return Outcome.failure(CulinaryError.NotFound(entity = "recipe_version", id = versionId.value))
        val versions = prefetch(versionId) ?: return missingGraph()
        val source = RecipeGraphSource { versions[it] }
        val exploded =
            when (
                val result =
                    exploder.explodeForServings(
                        rootVersionId = versionId,
                        servings = Servings(targetServings),
                        source = source,
                        includeOptional = true,
                    )
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val labels = mutableMapOf<IngredientId, String>()
        val lines =
            exploded.map { requirement ->
                val label =
                    labels.getOrPut(requirement.ingredientId) {
                        when (val result = ingredientRepository.get(requirement.ingredientId)) {
                            is Outcome.Success -> result.value.name
                            is Outcome.Failure -> "已删除食材"
                        }
                    }
                ExpandedRecipeLine(
                    label = label,
                    quantity = requirement.quantity,
                    isOptional = requirement.isOptional,
                )
            }
        return Outcome.success(
            ExpandedRecipe(
                baseServings = root.servings,
                targetServings = targetServings.toInt(),
                lines = lines,
            ),
        )
    }

    private suspend fun prefetch(root: RecipeVersionId): Map<RecipeVersionId, RecipeVersion>? {
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (versions.containsKey(id)) continue
            val version = recipeRepository.getVersion(id) ?: return null
            versions[id] = version
            version.childRecipeVersions().forEach(queue::addLast)
        }
        return versions
    }

    private fun missingGraph(): Outcome<Nothing> =
        Outcome.failure(CulinaryError.NotFound(entity = "recipe_version", id = "component"))
}
