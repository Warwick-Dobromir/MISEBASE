package com.culinary.feature.ingredients

import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 从当前正式配方的组成数据反向计算食材应用关系。
 *
 * 不依赖手工维护的 kitchen_relation 记录，避免漏掉实际使用该食材的酱汁或菜谱。
 */
class GetIngredientApplicationsUseCase
    @Inject
    constructor(
        private val sauceRepository: SauceRepository,
        private val recipeRepository: RecipeRepository,
    ) {
        suspend operator fun invoke(ingredientId: IngredientId): List<KitchenRelatedRecord> {
            val result = mutableListOf<KitchenRelatedRecord>()

            sauceRepository.observeActive().first().forEach { sauce ->
                val current = sauceRepository.findCurrentVersion(sauce.id) ?: return@forEach
                val usesIngredient = current.components.any { component ->
                    (component.target as? ComponentTarget.Ingredient)?.id == ingredientId
                }
                if (usesIngredient) {
                    result += KitchenRelatedRecord(
                        sourceRecordId = ingredientId.value,
                        sourceKind = KitchenRecordKind.INGREDIENT,
                        targetRecordId = sauce.id.value,
                        targetKind = KitchenRecordKind.SAUCE,
                        targetLabel = sauce.name,
                        relationLabel = "组成食材",
                    )
                }
            }

            recipeRepository.observeActive().first().forEach { recipe ->
                val current = recipeRepository.getCurrentVersion(recipe.id) ?: return@forEach
                val usesIngredient = current.components.any { component ->
                    (component.target as? ComponentTarget.Ingredient)?.id == ingredientId
                }
                if (usesIngredient) {
                    result += KitchenRelatedRecord(
                        sourceRecordId = ingredientId.value,
                        sourceKind = KitchenRecordKind.INGREDIENT,
                        targetRecordId = recipe.id.value,
                        targetKind = KitchenRecordKind.RECIPE,
                        targetLabel = recipe.name,
                        relationLabel = "组成食材",
                    )
                }
            }

            return result.sortedWith(
                compareBy<KitchenRelatedRecord> { it.targetKind.name }
                    .thenBy { it.sortOrder }
                    .thenBy { it.targetLabel },
            )
        }
    }
