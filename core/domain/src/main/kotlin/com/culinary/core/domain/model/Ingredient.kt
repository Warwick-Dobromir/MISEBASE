package com.culinary.core.domain.model

/**
 * 食材档案（A06 §3.2 的领域视图 + Stage 3 档案字段）。
 *
 * 食材与商品分离：食材是知识资产，商品是采购/库存实体。
 */
data class Ingredient(
    val id: IngredientId,
    val name: String,
    val normalizedName: String,
    val defaultUnitId: UnitId?,
    val status: EntityStatus,
    val category: String? = null,
    val scientificName: String? = null,
    val description: String? = null,
    val flavorProfile: String? = null,
    val seasonality: String? = null,
    val origin: String? = null,
    val selectionNotes: String? = null,
    val storageNotes: String? = null,
    val prepNotes: String? = null,
    val allergenNotes: String? = null,
    val englishName: String? = null,
    val exampleProducts: String? = null,
    val seedNote: String? = null,
    val seedId: String? = null,
    val seedVersion: String? = null,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    init {
        require(name.isNotBlank()) { "ingredient name must not be blank" }
        require(normalizedName.isNotBlank()) { "ingredient normalizedName must not be blank" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE

    val isSeed: Boolean get() = seedId != null
}
