package com.culinary.core.data.kitchen

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
    val categoryId: String? = null,
    val categoryPath: List<String> = emptyList(),
    val testBatch: String? = null,
) {
    val categoryPathLabel: String
        get() = categoryPath.joinToString(" / ")
}

/** 分类目录节点；数量分别表示直接档案与全部后代档案。 */
data class KitchenArchiveCategory(
    val id: String,
    val library: KitchenArchiveKind,
    val parentId: String?,
    val name: String,
    val englishName: String?,
    val sortOrder: Int,
    val testBatch: String?,
    val directCount: Int,
    val descendantCount: Int,
    val childCount: Int,
) {
    val isTest: Boolean get() = testBatch != null
}

/** 六库只读数据端口；目录、搜索和统一资料库页面共用。 */
interface KitchenArchiveReader {
    fun observeEntries(): Flow<List<KitchenArchiveEntry>>

    /** 测试 fake 可沿用空默认实现；生产实现返回真实分类树。 */
    fun observeCategories(library: KitchenArchiveKind): Flow<List<KitchenArchiveCategory>> = flowOf(emptyList())
}
