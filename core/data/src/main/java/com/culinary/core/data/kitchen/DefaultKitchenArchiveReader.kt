package com.culinary.core.data.kitchen

import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.kitchen.ListKitchenReferencesUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 六库统一只读投影。
 *
 * 只组合现有 Use Case 的 Flow；搜索文本也只由这些真实档案字段组成，
 * 不访问 DAO、不写库、不创建记录。
 */
class DefaultKitchenArchiveReader
    @Inject
    constructor(
        private val listSauces: ListSaucesUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listRecipes: ListRecipesUseCase,
        private val listKitchenReferences: ListKitchenReferencesUseCase,
    ) : KitchenArchiveReader {
        override fun observeEntries(): Flow<List<KitchenArchiveEntry>> =
            combine(
                listSauces(),
                listIngredients(),
                listRecipes(),
                listKitchenReferences(),
            ) { sauces, ingredients, recipes, references ->
                buildList {
                    sauces.forEach { add(it.toArchiveEntry()) }
                    ingredients.forEach { add(it.toArchiveEntry()) }
                    recipes.forEach { add(it.toArchiveEntry()) }
                    references.forEach { add(it.toArchiveEntry()) }
                }
            }
    }

private fun Sauce.toArchiveEntry(): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id.value,
        kind = KitchenArchiveKind.SAUCE,
        name = name,
        subtitle = flavorProfile ?: usageNotes,
        category = family,
        searchText =
            listOfNotNull(
                name,
                family,
                texture,
                flavorProfile,
                usageNotes,
                storageNotes,
                englishName,
                mainIngredients,
                preparationSummary,
                keyParameters,
                adjustableNotes,
                seedNote,
            ).joinToString(" "),
    )

private fun Ingredient.toArchiveEntry(): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id.value,
        kind = KitchenArchiveKind.INGREDIENT,
        name = name,
        subtitle = description ?: exampleProducts ?: storageNotes,
        category = category,
        searchText =
            listOfNotNull(
                name,
                category,
                description,
                englishName,
                exampleProducts,
                storageNotes,
                allergenNotes,
                seedNote,
            ).joinToString(" "),
    )

private fun KitchenReference.toArchiveEntry(): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id.value,
        kind = kind.toArchiveKind(),
        name = name,
        subtitle = description,
        category = category,
        searchText = listOfNotNull(name, category, description, englishName, seedNote).joinToString(" "),
    )

private fun Recipe.toArchiveEntry(): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id.value,
        kind = KitchenArchiveKind.RECIPE,
        name = name,
        subtitle = englishName,
        category = category.name,
        searchText = listOfNotNull(name, englishName, allergenNotes, ingredientSummary, seedNote).joinToString(" "),
    )

private fun KitchenReferenceKind.toArchiveKind(): KitchenArchiveKind =
    when (this) {
        KitchenReferenceKind.TECHNIQUE -> KitchenArchiveKind.TECHNIQUE
        KitchenReferenceKind.PRODUCTION_STANDARD -> KitchenArchiveKind.PRODUCTION_STANDARD
        KitchenReferenceKind.STORAGE_STANDARD -> KitchenArchiveKind.STORAGE_STANDARD
    }
