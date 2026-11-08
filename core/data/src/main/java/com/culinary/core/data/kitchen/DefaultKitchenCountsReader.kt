package com.culinary.core.data.kitchen

import com.culinary.core.domain.model.ARCHIVE_CATEGORY_TEST_BATCH
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.kitchen.ListKitchenReferencesUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 厨房首页数量实现：组合既有档案与新增参考资料的 Flow。
 *
 * 只读、不写库；每次源数据变化都会重新发出 [KitchenCounts]。
 */
class DefaultKitchenCountsReader
    @Inject
    constructor(
        private val listSauces: ListSaucesUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listRecipes: ListRecipesUseCase,
        private val listKitchenReferences: ListKitchenReferencesUseCase,
    ) : KitchenCountsReader {
        override fun observeCounts(): Flow<KitchenCounts> =
            combine(
                listSauces(),
                listIngredients(),
                listRecipes(),
                listKitchenReferences(),
            ) { sauces, ingredients, recipes, references ->
                val formalSauces = sauces.filter { it.seedVersion != ARCHIVE_CATEGORY_TEST_BATCH }
                val formalIngredients = ingredients.filter { it.seedVersion != ARCHIVE_CATEGORY_TEST_BATCH }
                val formalRecipes = recipes.filter { it.seedVersion != ARCHIVE_CATEGORY_TEST_BATCH }
                val formalReferences =
                    references.filter { it.seedVersion != ARCHIVE_CATEGORY_TEST_BATCH }
                KitchenCounts(
                    sauceCount = formalSauces.size,
                    ingredientCount = formalIngredients.size,
                    recipeCount = formalRecipes.size,
                    cookingTechniqueCount =
                        formalReferences.count { it.kind == KitchenReferenceKind.TECHNIQUE },
                    productionStandardCount =
                        formalReferences.count { it.kind == KitchenReferenceKind.PRODUCTION_STANDARD },
                    storageStandardCount =
                        formalReferences.count { it.kind == KitchenReferenceKind.STORAGE_STANDARD },
                )
            }
    }
