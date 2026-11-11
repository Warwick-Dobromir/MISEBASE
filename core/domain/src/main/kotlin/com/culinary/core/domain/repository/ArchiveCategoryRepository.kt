package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.KitchenRecordKind
import kotlinx.coroutines.flow.Flow

/** 六库分类树与档案归属端口。 */
interface ArchiveCategoryRepository {
    fun observeActive(): Flow<List<ArchiveCategory>>

    fun observeAssignments(): Flow<List<ArchiveCategoryAssignment>>

    suspend fun get(id: String): ArchiveCategory?

    suspend fun getForRecord(
        kind: KitchenRecordKind,
        recordId: String,
    ): ArchiveCategory?

    suspend fun assign(
        kind: KitchenRecordKind,
        recordId: String,
        categoryId: String,
    ): Outcome<Unit>

    suspend fun clearTestBatch(testBatch: String): Outcome<Int>

    suspend fun libraryOf(kind: KitchenRecordKind): ArchiveLibrary

    // ---- 库存分类管理（v9）：只操作 library = INVENTORY 的节点 ----

    suspend fun listByLibrary(library: ArchiveLibrary): Outcome<List<ArchiveCategory>> = Outcome.success(emptyList())

    suspend fun createCategory(
        library: ArchiveLibrary,
        parentId: String?,
        name: String,
        englishName: String? = null,
    ): Outcome<ArchiveCategory> = unsupportedCategoryWrite()

    suspend fun renameCategory(
        id: String,
        name: String,
        englishName: String? = null,
    ): Outcome<ArchiveCategory> = unsupportedCategoryWrite()

    suspend fun moveCategory(
        id: String,
        newParentId: String?,
    ): Outcome<ArchiveCategory> = unsupportedCategoryWrite()

    /** 按给定顺序重排同级分类的 `sort_order`。 */
    suspend fun reorderCategories(orderedIds: List<String>): Outcome<Unit> = unsupportedCategoryWrite()

    /** 只删除没有子分类、且没有档案归属的分类。 */
    suspend fun deleteCategory(id: String): Outcome<Unit> = unsupportedCategoryWrite()

    private fun unsupportedCategoryWrite(): Outcome<Nothing> =
        Outcome.failure(
            com.culinary.core.common.CulinaryError
                .Unexpected("category write is not implemented"),
        )
}
