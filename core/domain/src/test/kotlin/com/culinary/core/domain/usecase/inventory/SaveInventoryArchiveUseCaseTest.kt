package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.DefaultShelfLifeUnit
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SaveInventoryArchiveUseCaseTest {
    @Test
    fun `persists default shelf life value and unit`() = runBlocking {
        val repository = FakeInventoryArchiveRepository()
        val useCase = SaveInventoryArchiveUseCase(
            repository = repository,
            categoryRepository = EmptyArchiveCategoryRepository,
            idGenerator = IdGenerator { "archive-1" },
            clock = TestClock,
        )

        val result = useCase(
            InventoryArchive(
                id = "",
                name = "牛奶",
                stockUnit = Gram,
                defaultShelfLifeValue = 18,
                defaultShelfLifeUnit = DefaultShelfLifeUnit.MONTH,
            ),
        )

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val saved = repository.archives.getValue("archive-1")
        assertThat(saved.defaultShelfLifeValue).isEqualTo(18)
        assertThat(saved.defaultShelfLifeUnit).isEqualTo(DefaultShelfLifeUnit.MONTH)
    }

    private object EmptyArchiveCategoryRepository : ArchiveCategoryRepository {
        override fun observeActive(): Flow<List<ArchiveCategory>> = emptyFlow()

        override fun observeAssignments(): Flow<List<ArchiveCategoryAssignment>> = emptyFlow()

        override suspend fun get(id: String): ArchiveCategory? = null

        override suspend fun getForRecord(
            kind: KitchenRecordKind,
            recordId: String,
        ): ArchiveCategory? = null

        override suspend fun assign(
            kind: KitchenRecordKind,
            recordId: String,
            categoryId: String,
        ): Outcome<Unit> = Outcome.success(Unit)

        override suspend fun clearAssignment(
            kind: KitchenRecordKind,
            recordId: String,
        ): Outcome<Unit> = Outcome.success(Unit)

        override suspend fun clearTestBatch(testBatch: String): Outcome<Int> = Outcome.success(0)

        override suspend fun libraryOf(kind: KitchenRecordKind): ArchiveLibrary = ArchiveLibrary.INVENTORY
    }
}
