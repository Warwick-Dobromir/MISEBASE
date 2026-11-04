package com.culinary.core.domain.model

/**
 * 食材档案（A06 §3.2 的领域视图 + Stage 3 档案字段）。
 *
 * 食材与商品分离：食材是知识资产，商品是采购/库存实体（D-004、P01）。
 * [category] 与 [defaultUnitId] 是 Stage 3 明确要求维护的档案字段；换算与别名仍按需扩展。
 */
data class Ingredient(
    val id: IngredientId,
    val name: String,
    val normalizedName: String,
    val defaultUnitId: UnitId?,
    val status: EntityStatus,
    val category: String? = null,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "ingredient name must not be blank" }
        require(normalizedName.isNotBlank()) { "ingredient normalizedName must not be blank" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE
}
