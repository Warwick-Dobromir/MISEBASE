package com.culinary.home

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary
import com.culinary.core.data.home.HomeSummaryReader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** 首页状态编排：加载、成功、空、局部失败、重试与异常降级。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock { Instant.parse("2026-11-05T02:30:00Z") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading and publishes real summaries`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.success(HomeInventorySummary(1, 3, 0)),
                    knowledge = Outcome.success(HomeKnowledgeSummary(0, 1, 0)),
                )
            val viewModel = HomeViewModel(reader, clock)

            assertThat(viewModel.uiState.value.inventory).isEqualTo(HomeSection.Loading)
            assertThat(viewModel.uiState.value.knowledge).isEqualTo(HomeSection.Loading)

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.inventory).isEqualTo(
                HomeSection.Ready(HomeInventorySummary(1, 3, 0)),
            )
            assertThat(viewModel.uiState.value.knowledge).isEqualTo(
                HomeSection.Ready(HomeKnowledgeSummary(0, 1, 0)),
            )
            assertThat(viewModel.uiState.value.todayLabel).isNotEmpty()
            assertThat(reader.inventoryCalls).isEqualTo(1)
            assertThat(reader.knowledgeCalls).isEqualTo(1)
        }

    @Test
    fun `one section failure does not replace the other section`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.failure(CulinaryError.Data("db down", code = ErrorCode.DB_001)),
                    knowledge = Outcome.success(HomeKnowledgeSummary(2, 4, 6)),
                )
            val viewModel = HomeViewModel(reader, clock)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.inventory).isInstanceOf(HomeSection.Failed::class.java)
            assertThat((state.inventory as HomeSection.Failed).message)
                .isEqualTo("本机数据库读取失败，请重试。")
            assertThat((state.inventory as HomeSection.Failed).message).doesNotContain("CUL-")
            assertThat((state.inventory as HomeSection.Failed).diagnosticCode).isEqualTo("CUL-DB-001")
            assertThat(state.knowledge).isEqualTo(HomeSection.Ready(HomeKnowledgeSummary(2, 4, 6)))
        }

    @Test
    fun `retry replaces a failed section with loaded data`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.failure(CulinaryError.Data("db down")),
                    knowledge = Outcome.success(HomeKnowledgeSummary(0, 0, 0)),
                )
            val viewModel = HomeViewModel(reader, clock)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.inventory).isInstanceOf(HomeSection.Failed::class.java)

            reader.inventory = Outcome.success(HomeInventorySummary(2, 5, 1))
            viewModel.retryInventory()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.inventory).isEqualTo(
                HomeSection.Ready(HomeInventorySummary(2, 5, 1)),
            )
            assertThat(reader.inventoryCalls).isEqualTo(2)
        }

    @Test
    fun `unexpected reader exception is shown as a failed section`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.success(HomeInventorySummary(0, 0, 0)),
                    knowledge = Outcome.success(HomeKnowledgeSummary(0, 0, 0)),
                    throwOnInventory = true,
                )
            val viewModel = HomeViewModel(reader, clock)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.inventory).isInstanceOf(HomeSection.Failed::class.java)
            assertThat((viewModel.uiState.value.inventory as HomeSection.Failed).message)
                .contains("未预期")
        }

    @Test
    fun `refresh keeps visible data and raises a lightweight refreshing flag`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.success(HomeInventorySummary(1, 3, 0)),
                    knowledge = Outcome.success(HomeKnowledgeSummary(2, 4, 6)),
                )
            val viewModel = HomeViewModel(reader, clock)
            advanceUntilIdle()

            reader.delayMillis = 100
            reader.inventory = Outcome.success(HomeInventorySummary(2, 5, 1))
            viewModel.refresh()

            // 刷新期间保留旧值，不发骨架屏。
            assertThat(viewModel.uiState.value.isRefreshing).isTrue()
            assertThat(viewModel.uiState.value.inventory).isEqualTo(
                HomeSection.Ready(HomeInventorySummary(1, 3, 0)),
            )

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isRefreshing).isFalse()
            assertThat(viewModel.uiState.value.inventory).isEqualTo(
                HomeSection.Ready(HomeInventorySummary(2, 5, 1)),
            )
        }

    @Test
    fun `refresh during an active load is not silently dropped`() =
        runTest(dispatcher) {
            val reader =
                FakeReader(
                    inventory = Outcome.success(HomeInventorySummary(0, 0, 0)),
                    knowledge = Outcome.success(HomeKnowledgeSummary(0, 0, 0)),
                    delayMillis = 100,
                )
            val viewModel = HomeViewModel(reader, clock)
            advanceTimeBy(10)

            reader.inventory = Outcome.success(HomeInventorySummary(4, 6, 2))
            reader.knowledge = Outcome.success(HomeKnowledgeSummary(1, 1, 1))
            viewModel.refresh()
            advanceUntilIdle()

            assertThat(reader.inventoryCalls).isEqualTo(2)
            assertThat(reader.knowledgeCalls).isEqualTo(2)
            assertThat(viewModel.uiState.value.inventory).isEqualTo(
                HomeSection.Ready(HomeInventorySummary(4, 6, 2)),
            )
            assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        }
}

private class FakeReader(
    var inventory: Outcome<HomeInventorySummary>,
    var knowledge: Outcome<HomeKnowledgeSummary>,
    private val throwOnInventory: Boolean = false,
    var delayMillis: Long = 0,
) : HomeSummaryReader {
    var inventoryCalls: Int = 0
        private set
    var knowledgeCalls: Int = 0
        private set

    override suspend fun inventorySummary(): Outcome<HomeInventorySummary> {
        inventoryCalls += 1
        if (delayMillis > 0) delay(delayMillis)
        if (throwOnInventory) error("boom")
        return inventory
    }

    override suspend fun knowledgeSummary(): Outcome<HomeKnowledgeSummary> {
        knowledgeCalls += 1
        return knowledge
    }
}
