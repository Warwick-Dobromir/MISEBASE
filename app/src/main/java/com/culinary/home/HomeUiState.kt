package com.culinary.home

import androidx.annotation.StringRes
import com.culinary.R

/**
 * 首页一条今日计划。
 *
 * 只保存本地演示状态，不映射 Room 实体；[titleRes] 保留文案资源，
 * 便于后续把模拟数据替换为正式仓储而不改页面结构。
 */
data class HomePlan(
    val id: String,
    @StringRes val titleRes: Int,
    val completed: Boolean = false,
)

/** 首页一条采购记录。当前为只读演示数据，点击进入明确的占位详情入口。 */
data class HomePurchaseRecord(
    val id: String,
    @StringRes val nameRes: Int,
    val createdDate: String,
    val createdTime: String,
)

/**
 * 首页界面状态（HOME-001）。
 *
 * 计划列表是可交互的内存副本；采购单是只读演示数据。两者都不来自也不写入 Room，
 * 与正式档案/库存数据保持清晰隔离。
 */
data class HomeUiState(
    val plans: List<HomePlan> = HomeMockData.plans,
    val purchases: List<HomePurchaseRecord> = HomeMockData.purchases,
) {
    val completedPlanCount: Int
        get() = plans.count { it.completed }

    val totalPlanCount: Int
        get() = plans.size

    /** 0f..1f；没有计划时视为 0，避免进度条除零。 */
    val planProgress: Float
        get() = if (totalPlanCount == 0) 0f else completedPlanCount.toFloat() / totalPlanCount
}

/**
 * 首页本地演示数据（HOME-001）。
 *
 * 明确隔离：此对象不依赖 Room、DataStore 或网络，也不被任何仓储写入；
 * 计划勾选只修改 [HomeUiState] 中的内存副本，应用进程结束后自然恢复默认。
 */
object HomeMockData {
    val plans: List<HomePlan> =
        listOf(
            HomePlan(id = "stock", titleRes = R.string.home_plan_stock),
            HomePlan(id = "sauce", titleRes = R.string.home_plan_sauce),
            HomePlan(id = "inventory", titleRes = R.string.home_plan_inventory),
            HomePlan(id = "recipes", titleRes = R.string.home_plan_recipes),
        )

    val purchases: List<HomePurchaseRecord> =
        listOf(
            HomePurchaseRecord(
                id = "vegetables",
                nameRes = R.string.home_purchase_vegetables,
                createdDate = "2026.11.08",
                createdTime = "08:30",
            ),
            HomePurchaseRecord(
                id = "dairy",
                nameRes = R.string.home_purchase_dairy,
                createdDate = "2026.11.07",
                createdTime = "16:20",
            ),
            HomePurchaseRecord(
                id = "meat",
                nameRes = R.string.home_purchase_meat,
                createdDate = "2026.11.07",
                createdTime = "10:15",
            ),
        )

    fun purchaseById(id: String): HomePurchaseRecord? = purchases.firstOrNull { it.id == id }
}
