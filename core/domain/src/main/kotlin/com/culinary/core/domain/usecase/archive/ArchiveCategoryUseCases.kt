package com.culinary.core.domain.usecase.archive

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ListArchiveCategoriesUseCase(
    private val repository: ArchiveCategoryRepository? = null,
) {
    operator fun invoke(library: ArchiveLibrary): Flow<List<ArchiveCategory>> =
        repository?.observeActive()?.map { categories -> categories.filter { it.library == library } }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
}

class GetArchiveCategoryForRecordUseCase(
    private val repository: ArchiveCategoryRepository? = null,
) {
    suspend operator fun invoke(
        kind: KitchenRecordKind,
        recordId: String,
    ): ArchiveCategory? = repository?.getForRecord(kind, recordId)
}

class AssignArchiveCategoryUseCase(
    private val repository: ArchiveCategoryRepository? = null,
) {
    suspend operator fun invoke(
        kind: KitchenRecordKind,
        recordId: String,
        categoryId: String,
    ): Outcome<Unit> = repository?.assign(kind, recordId, categoryId) ?: Outcome.success(Unit)
}

class ClearArchiveCategoryTestBatchUseCase(
    private val repository: ArchiveCategoryRepository? = null,
) {
    suspend operator fun invoke(testBatch: String): Outcome<Int> =
        repository?.clearTestBatch(testBatch) ?: Outcome.success(0)
}
