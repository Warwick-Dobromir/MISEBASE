package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.Outcome
import com.culinary.core.data.kitchen.KitchenReferenceRecord
import com.culinary.core.data.kitchen.KitchenReferenceStore
import com.culinary.core.data.kitchen.KitchenReferenceType
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KitchenReferenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new technique saves through the reference store`() =
        runTest(dispatcher) {
            val store = FakeReferenceStore()
            val viewModel =
                KitchenReferenceEditorViewModel(
                    SavedStateHandle(mapOf(KITCHEN_REFERENCE_KIND_ARG to "technique")),
                    store,
                )

            advanceUntilIdle()
            viewModel.onNameChange("低温慢煮")
            viewModel.onCategoryChange("热加工")
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.saved).isTrue()
            assertThat(store.savedType).isEqualTo(KitchenReferenceType.TECHNIQUE)
            assertThat(store.savedName).isEqualTo("低温慢煮")
        }

    @Test
    fun `blank name is rejected before store call`() =
        runTest(dispatcher) {
            val store = FakeReferenceStore()
            val viewModel =
                KitchenReferenceEditorViewModel(
                    SavedStateHandle(mapOf(KITCHEN_REFERENCE_KIND_ARG to "technique")),
                    store,
                )

            advanceUntilIdle()
            viewModel.save()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isEqualTo(KitchenReferenceError.NAME_REQUIRED)
            assertThat(store.saveCalls).isEqualTo(0)
        }

    @Test
    fun `detail loads and archives a reference`() =
        runTest(dispatcher) {
            val record =
                KitchenReferenceRecord(
                    id = "ref-1",
                    type = KitchenReferenceType.PRODUCTION_STANDARD,
                    name = "出品温度",
                    category = "热菜",
                    description = "出餐中心温度不低于 65°C",
                )
            val store = FakeReferenceStore(record)
            val viewModel =
                KitchenReferenceDetailViewModel(
                    SavedStateHandle(
                        mapOf(
                            KITCHEN_REFERENCE_KIND_ARG to "production-standard",
                            KITCHEN_REFERENCE_ID_ARG to "ref-1",
                        ),
                    ),
                    store,
                    emptyRelations(),
                )

            advanceUntilIdle()
            assertThat(
                viewModel.uiState.value.record
                    ?.name,
            ).isEqualTo("出品温度")

            viewModel.archive()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.archived).isTrue()
        }
}

private class FakeReferenceStore(
    private var record: KitchenReferenceRecord? = null,
) : KitchenReferenceStore {
    var savedType: KitchenReferenceType? = null
    var savedName: String? = null
    var saveCalls: Int = 0

    override fun observe(type: KitchenReferenceType): Flow<List<KitchenReferenceRecord>> =
        if (record == null) emptyFlow() else MutableStateFlow(listOf(record!!))

    override suspend fun get(id: String): Outcome<KitchenReferenceRecord> =
        record?.let { Outcome.success(it) }
            ?: Outcome.failure(
                com.culinary.core.common.CulinaryError
                    .NotFound("kitchen_reference", id),
            )

    override suspend fun save(
        type: KitchenReferenceType,
        id: String?,
        name: String,
        category: String?,
        description: String?,
        englishName: String?,
    ): Outcome<String> {
        saveCalls += 1
        savedType = type
        savedName = name
        record =
            KitchenReferenceRecord(
                id = id ?: "new-reference",
                type = type,
                name = name,
                category = category,
                description = description,
                englishName = englishName,
            )
        return Outcome.success(record!!.id)
    }

    override suspend fun archive(id: String): Outcome<Unit> = Outcome.success(Unit)
}

private fun emptyRelations(): ObserveKitchenRelationsUseCase =
    ObserveKitchenRelationsUseCase(
        object : KitchenRelationRepository {
            override fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>> = flowOf(emptyList())

            override suspend fun replaceRelations(
                sourceRecordId: String,
                sourceKind: com.culinary.core.domain.model.KitchenRecordKind,
                targetKind: com.culinary.core.domain.model.KitchenRecordKind,
                relations: List<com.culinary.core.domain.model.KitchenRelationDraft>,
            ): Outcome<Unit> = Outcome.success(Unit)
        },
    )
