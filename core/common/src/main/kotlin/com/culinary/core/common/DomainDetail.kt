package com.culinary.core.common

/**
 * 领域失败的结构化明细（E03 §2.3）。
 *
 * 领域错误必须携带足够上下文，让 UI 能说清"哪条配方、哪个单位、哪批库存"。
 * 这里只使用不透明字符串，避免 `core:common` 反向依赖领域模型。
 */
sealed interface DomainDetail {
    /** 配方/酱汁展开时发现的循环引用，[path] 形如 `recipe-v2 -> recipe-v1 -> recipe-v2`。 */
    data class RecipeCycle(
        val path: List<String>,
    ) : DomainDetail

    /** 单位不可换算；[ingredientId] 为空表示没有食材级换算依据。 */
    data class UnitIncompatible(
        val fromUnit: String,
        val toUnit: String,
        val ingredientId: String?,
    ) : DomainDetail

    /** 实体状态不允许当前操作，例如"仅有已确认计划才能生成快照"。 */
    data class InvalidState(
        val entity: String,
        val expected: String,
        val actual: String,
    ) : DomainDetail

    /** 库存不足；数值以计量单位表示，调用方负责附带单位码。 */
    data class InsufficientStock(
        val productId: String,
        val requested: Double,
        val available: Double,
    ) : DomainDetail
}
