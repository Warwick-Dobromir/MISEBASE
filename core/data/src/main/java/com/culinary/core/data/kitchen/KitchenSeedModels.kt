package com.culinary.core.data.kitchen

import kotlinx.serialization.Serializable

/** 种子 JSON 的版本化根节点；字段名与 assets/kitchen_seed_v1.json 对齐。 */
@Serializable
data class KitchenSeedFile(
    val version: String,
    val generatedAt: String? = null,
    val source: String? = null,
    val ingredients: List<KitchenSeedIngredient> = emptyList(),
    val sauces: List<KitchenSeedSauce> = emptyList(),
    val recipes: List<KitchenSeedRecipe> = emptyList(),
    val references: List<KitchenSeedReference> = emptyList(),
    val relations: List<KitchenSeedRelation> = emptyList(),
)

@Serializable
data class KitchenSeedIngredient(
    val seedId: String,
    val name: String,
    val englishName: String? = null,
    val category: String? = null,
    val unitCode: String,
    val exampleProducts: String? = null,
    val storageNotes: String? = null,
    val allergenNotes: String? = null,
    val seedNote: String? = null,
)

@Serializable
data class KitchenSeedSauce(
    val seedId: String,
    val name: String,
    val englishName: String? = null,
    val family: String? = null,
    val usageNotes: String? = null,
    val mainIngredients: String? = null,
    val preparationSummary: String? = null,
    val keyParameters: String? = null,
    val storageNotes: String? = null,
    val finishedStandard: String? = null,
    val adjustableNotes: String? = null,
    val seedNote: String? = null,
    val techniqueSeeds: List<String> = emptyList(),
    val standardSeeds: List<String> = emptyList(),
    val ingredientSeeds: List<String> = emptyList(),
)

@Serializable
data class KitchenSeedRecipe(
    val seedId: String,
    val name: String,
    val englishName: String? = null,
    val category: String = "OTHER",
    val servings: Int = 1,
    val yieldUnitCode: String = "portion",
    val ingredientText: String? = null,
    val instructions: String? = null,
    val allergenNotes: String? = null,
    val sauceSeeds: List<String> = emptyList(),
    val techniqueSeeds: List<String> = emptyList(),
    val standardSeeds: List<String> = emptyList(),
    val ingredientSeeds: List<String> = emptyList(),
    val components: List<KitchenSeedComponent> = emptyList(),
)

@Serializable
data class KitchenSeedComponent(
    val ingredientSeedId: String,
    val label: String? = null,
    val quantity: Double,
    val unitCode: String,
)

@Serializable
data class KitchenSeedReference(
    val seedId: String,
    val kind: String,
    val name: String,
    val englishName: String? = null,
    val category: String? = null,
    val description: String? = null,
    val seedNote: String? = null,
    val relatedSeeds: List<String> = emptyList(),
)

@Serializable
data class KitchenSeedRelation(
    val sourceSeedId: String,
    val targetSeedId: String,
    val relationLabel: String,
)

data class KitchenSeedCounts(
    val ingredients: Int = 0,
    val sauces: Int = 0,
    val recipes: Int = 0,
    val references: Int = 0,
    val relations: Int = 0,
)

data class KitchenSeedStatus(
    val version: String? = null,
    val disabled: Boolean = false,
    val counts: KitchenSeedCounts = KitchenSeedCounts(),
) {
    val isImported: Boolean get() = version != null && counts.ingredients > 0
}

data class KitchenSeedImportResult(
    val imported: Boolean,
    val version: String,
    val counts: KitchenSeedCounts,
)
