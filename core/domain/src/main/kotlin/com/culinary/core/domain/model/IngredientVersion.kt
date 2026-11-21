package com.culinary.core.domain.model

/** 食材档案的不可变历史版本摘要。 */
data class IngredientVersion(
    val id: String,
    val ingredientId: IngredientId,
    val versionNo: Int,
    val status: String,
    val createdAt: Long,
)
