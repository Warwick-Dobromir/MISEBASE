package com.culinary.core.data.kitchen

import kotlinx.coroutines.flow.Flow

/**
 * 厨房首页各分类的真实档案数。
 *
 * 酱汁、食材、菜谱由现有 Repository 的 Flow 持续观察，新增/删除后自动更新。
 * 烹饪技术、制作标准、储存标准在当前数据库 schema 中尚无对应持久化表，
 * 因此明确保持 0；不借用其他分类的数量混算。
 */
data class KitchenCounts(
    val sauceCount: Int = 0,
    val ingredientCount: Int = 0,
    val recipeCount: Int = 0,
    val cookingTechniqueCount: Int = 0,
    val productionStandardCount: Int = 0,
    val storageStandardCount: Int = 0,
) {
    init {
        require(sauceCount >= 0) { "sauceCount must be >= 0" }
        require(ingredientCount >= 0) { "ingredientCount must be >= 0" }
        require(recipeCount >= 0) { "recipeCount must be >= 0" }
        require(cookingTechniqueCount >= 0) { "cookingTechniqueCount must be >= 0" }
        require(productionStandardCount >= 0) { "productionStandardCount must be >= 0" }
        require(storageStandardCount >= 0) { "storageStandardCount must be >= 0" }
    }
}

/**
 * 厨房首页数量端口。
 *
 * 放在 `core:data`，避免 app 直接依赖 core:domain，也不接触 DAO。
 */
interface KitchenCountsReader {
    fun observeCounts(): Flow<KitchenCounts>
}
