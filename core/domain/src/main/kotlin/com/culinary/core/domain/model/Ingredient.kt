package com.culinary.core.domain.model

/**
 * 食材档案（A06 §3.2 的领域视图，阶段 2 只取最小字段）。
 *
 * 食材与商品分离：食材是知识资产，商品是采购/库存实体（D-004、P01）。
 * 别名、换算、图片在后续阶段扩展，不在这里预建空字段。
 */
data class Ingredient(
    val id: IngredientId,
    val name: String,
    val normalizedName: String,
    val defaultUnitId: UnitId?,
    val status: EntityStatus,
) {
    init {
        require(name.isNotBlank()) { "ingredient name must not be blank" }
        require(normalizedName.isNotBlank()) { "ingredient normalizedName must not be blank" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE
}
