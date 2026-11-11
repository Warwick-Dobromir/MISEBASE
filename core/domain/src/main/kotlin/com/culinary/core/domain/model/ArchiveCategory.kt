package com.culinary.core.domain.model

/** 七大档案库；分类树与 assignment 用这个枚举隔离。 */
enum class ArchiveLibrary {
    SAUCE,
    INGREDIENT,
    RECIPE,
    TECHNIQUE,
    PRODUCTION_STANDARD,
    STORAGE_STANDARD,

    /** 库存分类（v9）：与食材库分类共用同一棵树的技术，但业务归属独立。 */
    INVENTORY,
}

data class ArchiveCategory(
    val id: String,
    val library: ArchiveLibrary,
    val parentId: String?,
    val name: String,
    val normalizedName: String,
    val englishName: String?,
    val sortOrder: Int,
    val status: EntityStatus,
    val testBatch: String?,
    val depth: Int = 1,
    val path: List<String> = listOf(name),
) {
    val isTest: Boolean get() = testBatch != null
    val pathLabel: String get() = path.joinToString(" / ")
}

/** 分类目录节点；数量分别表示直接档案与全部后代档案。 */
data class ArchiveCategoryNode(
    val category: ArchiveCategory,
    val directRecordCount: Int,
    val descendantRecordCount: Int,
    val childCount: Int,
)

/** 一条档案的分类归属；recordKind 与 category.library 必须一致。 */
data class ArchiveCategoryAssignment(
    val recordKind: KitchenRecordKind,
    val recordId: String,
    val categoryId: String,
)

/** 测试分类/测试档案的固定批次标识。 */
const val ARCHIVE_CATEGORY_TEST_BATCH = "UI-CATEGORY-TEST-V1"
