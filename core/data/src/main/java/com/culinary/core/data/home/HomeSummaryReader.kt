package com.culinary.core.data.home

import com.culinary.core.common.Outcome

/**
 * 首页只读汇总（MB-HOME-001）。
 *
 * 这些数字只由既有 Use Case / Repository 的真实数据计算，不缓存、不造数：
 * - [productCount]：存在启用库存批次的商品数；
 * - [batchCount]：启用库存批次数；
 * - [attentionCount]：可用量 ≤ 0 的商品数（复用 [com.culinary.core.domain.model.InventoryOverviewRow.isLow]）。
 */
data class HomeInventorySummary(
    val productCount: Int,
    val batchCount: Int,
    val attentionCount: Int,
) {
    init {
        require(productCount >= 0) { "productCount must be >= 0" }
        require(batchCount >= 0) { "batchCount must be >= 0" }
        require(attentionCount >= 0) { "attentionCount must be >= 0" }
    }

    val isEmpty: Boolean get() = batchCount == 0
}

/** 知识库真实在用档案数：酱汁 / 食材 / 菜谱。 */
data class HomeKnowledgeSummary(
    val sauceCount: Int,
    val ingredientCount: Int,
    val recipeCount: Int,
) {
    init {
        require(sauceCount >= 0) { "sauceCount must be >= 0" }
        require(ingredientCount >= 0) { "ingredientCount must be >= 0" }
        require(recipeCount >= 0) { "recipeCount must be >= 0" }
    }

    val totalCount: Int get() = sauceCount + ingredientCount + recipeCount
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * 首页数据端口。
 *
 * 放在 `core:data` 而不是新增 `feature:home`，是为了在不改变已批准的 14 模块
 * 边界、也不让 `app` 直接依赖 `core:domain` 的前提下复用既有 Use Case。
 * 每个方法独立返回 [Outcome]，首页可以按模块局部降级。
 */
interface HomeSummaryReader {
    suspend fun inventorySummary(): Outcome<HomeInventorySummary>

    suspend fun knowledgeSummary(): Outcome<HomeKnowledgeSummary>
}
