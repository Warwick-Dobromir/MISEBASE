package com.culinary.kitchen

import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.core.data.kitchen.KitchenCounts
import com.culinary.core.data.kitchen.KitchenCountsReader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 厨房数量/搜索数据状态：首次加载、源数据自动更新与读取失败。 */
@OptIn(ExperimentalCoroutinesApi::class)
class KitchenViewModelTest {
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
    fun `loads real counts and entries then updates when flows change`() =
        runTest(dispatcher) {
            val counts =
                MutableStateFlow(
                    KitchenCounts(sauceCount = 1, ingredientCount = 2, recipeCount = 3),
                )
            val entries = MutableStateFlow(listOf(sampleEntry("sauce-1")))
            val viewModel = KitchenViewModel(FakeCountsReader(counts), FakeArchiveReader(entries))

            assertThat(viewModel.uiState.value.isLoading).isTrue()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.counts)
                .isEqualTo(KitchenCounts(sauceCount = 1, ingredientCount = 2, recipeCount = 3))
            assertThat(viewModel.archiveEntries.value).hasSize(1)

            counts.value = KitchenCounts(sauceCount = 4, ingredientCount = 5, recipeCount = 6)
            entries.value = listOf(sampleEntry("sauce-1"), sampleEntry("sauce-2"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.counts)
                .isEqualTo(KitchenCounts(sauceCount = 4, ingredientCount = 5, recipeCount = 6))
            assertThat(viewModel.archiveEntries.value).hasSize(2)
        }

    @Test
    fun `reader failure is explicit instead of reporting zero`() =
        runTest(dispatcher) {
            val viewModel =
                KitchenViewModel(
                    FakeCountsReader(flow { error("db unavailable") }),
                    FakeArchiveReader(flow { error("db unavailable") }),
                )

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isLoading).isFalse()
            assertThat(viewModel.uiState.value.loadFailed).isTrue()
            assertThat(viewModel.archiveEntries.value).isEmpty()
        }
}

private fun sampleEntry(id: String): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id,
        kind = KitchenArchiveKind.SAUCE,
        name = "酱汁 $id",
        subtitle = null,
        category = "基础酱汁",
        searchText = "酱汁 $id 基础酱汁",
    )

private class FakeCountsReader(
    private val source: Flow<KitchenCounts>,
) : KitchenCountsReader {
    override fun observeCounts(): Flow<KitchenCounts> = source
}

private class FakeArchiveReader(
    private val source: Flow<List<KitchenArchiveEntry>>,
) : KitchenArchiveReader {
    override fun observeEntries(): Flow<List<KitchenArchiveEntry>> = source
}
