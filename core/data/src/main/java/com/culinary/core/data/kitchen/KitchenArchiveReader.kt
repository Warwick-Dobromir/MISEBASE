package com.culinary.core.data.kitchen

import kotlinx.coroutines.flow.Flow

/** 厨房六个资料库的统一只读投影类型。 */
enum class KitchenArchiveKind {
    SAUCE,
    INGREDIENT,
    RECIPE,
    TECHNIQUE,
    PRODUCTION_STANDARD,
    STORAGE_STANDARD,
}

/**
 * 资料库列表与全局搜索共用的只读条目。
 *
 * 不含原始配方内容，仅提供分类、名称、说明和稳定 id；点击后由 app
 * 通过既有详情路由打开真实档案，不复制业务字段。
 */
data class KitchenArchiveEntry(
    val id: String,
    val kind: KitchenArchiveKind,
    val name: String,
    val subtitle: String?,
    val category: String?,
    val searchText: String,
)

/** 跨六库只读数据端口；全局搜索和统一资料库页面共用。 */
interface KitchenArchiveReader {
    fun observeEntries(): Flow<List<KitchenArchiveEntry>>
}
