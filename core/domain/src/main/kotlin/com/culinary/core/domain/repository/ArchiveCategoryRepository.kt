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
}
