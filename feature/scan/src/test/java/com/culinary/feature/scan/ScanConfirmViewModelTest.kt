package com.culinary.feature.scan

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.scan.ScanImportKey
import com.culinary.core.domain.scan.ScanIngredientGateway
import com.culinary.core.domain.scan.ScanIngredientRequest
import com.culinary.core.domain.scan.ScanIngredientTarget
import com.culinary.core.domain.scan.ScanStockGateway
import com.culinary.core.domain.scan.ScanStockReceipt
import com.culinary.core.domain.scan.ScanStockRequest
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.scan.LookupScanImportUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * 导入确认页 ViewModel（MB-S5-002 事件级幂等）。
 *
 * 覆盖任务书要求的 7 个场景：首次导入、重复提示、取消、确认再次入库、快速连点、
 * 失败后重试复用同一 operation id、两次独立确认生成不同 operation id。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanConfirmViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    private val flour =
        Ingredient(
            id = IngredientId("ingredient-1"),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId("g"),
            status = EntityStatus.ACTIVE,
        )
    private val ingredientRepository = FakeIngredientRepository(listOf(flour))
    private val inventoryRepository = FakeInventoryRepository()
    private val ingredientGateway = FakeIngredientGateway()
    private val stockGateway = FakeStockGateway(inventoryRepository)
    private var operationCounter = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        code: String? = "6901234567892",
        format: String? = "EAN13",
        source: String? = "CAMERA",
    ): ScanConfirmViewModel =
        ScanConfirmViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        ScanRoutes.ARG_CODE to code,
                        ScanRoutes.ARG_FORMAT to format,
                        ScanRoutes.ARG_SOURCE to source,
                    ),
                ),
            lookup = LookupScanImportUseCase(inventoryRepository),
            listIngredients = ListIngredientsUseCase(ingredientRepository),
            ingredientGateway = ingredientGateway,
            stockGateway = stockGateway,
            archiveIngredient =
                ArchiveIngredientUseCase(ingredientRepository, ImmediateTransactionRunner),
            idGenerator = IdGenerator { "op-${++operationCounter}" },
        )

    // 场景 1：首次导入
    @Test
    fun `first import succeeds when there is no history`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isDuplicate).isFalse()

            viewModel.onSelectIngredient("ingredient-1")
            viewModel.confirm()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.result).isNotNull()
            assertThat(state.result?.replayed).isFalse()
            assertThat(state.isDuplicate).isTrue()
            assertThat(state.currentOperationId).isNull()
            assertThat(inventoryRepository.ledger).hasSize(1)
            assertThat(stockGateway.receivedOperationIds).containsExactly("op-1")
        }

    // 场景 2：重复提示
    @Test
    fun `duplicate scan sets the warning state without blocking`() =
        runTest(dispatcher.scheduler) {
            seedPreviousImport()

            val viewModel = newViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isDuplicate).isTrue()
            assertThat(state.previous).isNotNull()
            assertThat(state.canConfirm).isTrue()
            assertThat(state.isSubmitting).isFalse()
        }

    // 场景 3：取消
    @Test
    fun `cancel does not call the stock gateway and writes nothing`() =
        runTest(dispatcher.scheduler) {
            seedPreviousImport()
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onCancelImport()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.currentOperationId).isNull()
            assertThat(stockGateway.receivedOperationIds).isEmpty()
            assertThat(inventoryRepository.ledger).hasSize(1)
        }

    // 场景 4：确认再次入库
    @Test
    fun `confirming again creates a new operation id and one more ledger row`() =
        runTest(dispatcher.scheduler) {
            seedPreviousImport()
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onSelectIngredient("ingredient-1")
            viewModel.confirm()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.result).isNotNull()
            assertThat(state.result?.replayed).isFalse()
            assertThat(inventoryRepository.ledger).hasSize(2)
            assertThat(stockGateway.receivedOperationIds).containsExactly("op-1")
            assertThat(inventoryRepository.ledger.last().idempotencyKey)
                .isEqualTo(ScanImportKey.of(ScanCapture(rawValue = "6901234567892"), "op-1"))
        }

    // 场景 5：快速连续点击
    @Test
    fun `rapid double click submits only once`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            viewModel.confirm()
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(stockGateway.receivedOperationIds).hasSize(1)
            assertThat(inventoryRepository.ledger).hasSize(1)
        }

    // 场景 6：失败后重试复用同一个 operation id
    @Test
    fun `retry after a failure reuses the same operation id`() =
        runTest(dispatcher.scheduler) {
            stockGateway.failuresBeforeSuccess = 1
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            advanceUntilIdle()
            val failed = viewModel.uiState.value
            assertThat(failed.result).isNull()
            assertThat(failed.lastError).isNotNull()
            assertThat(failed.canRetry).isTrue()
            assertThat(failed.currentOperationId).isEqualTo("op-1")
            assertThat(inventoryRepository.ledger).isEmpty()

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(stockGateway.receivedOperationIds).containsExactly("op-1", "op-1").inOrder()
            assertThat(inventoryRepository.ledger).hasSize(1)
            assertThat(viewModel.uiState.value.result).isNotNull()
            assertThat(viewModel.uiState.value.currentOperationId).isNull()
        }

    // 场景 7：两次独立确认生成不同的 operation id
    @Test
    fun `two independent confirmations produce two different operation ids`() =
        runTest(dispatcher.scheduler) {
            val first = newViewModel()
            advanceUntilIdle()
            first.onSelectIngredient("ingredient-1")
            first.confirm()
            advanceUntilIdle()
            assertThat(first.uiState.value.result).isNotNull()

            // 重新进入确认页（等价于返回扫码页后再扫同一个码）
            val second = newViewModel()
            advanceUntilIdle()
            assertThat(second.uiState.value.isDuplicate).isTrue()
            second.onSelectIngredient("ingredient-1")
            second.confirm()
            advanceUntilIdle()

            assertThat(stockGateway.receivedOperationIds).containsExactly("op-1", "op-2").inOrder()
            assertThat(inventoryRepository.ledger).hasSize(2)
            assertThat(
                second.uiState.value.result
                    ?.replayed,
            ).isFalse()
        }

    @Test
    fun `reads the capture from navigation arguments`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()

            advanceUntilIdle()

            val capture = viewModel.uiState.value.capture
            assertThat(capture?.rawValue).isEqualTo("6901234567892")
            assertThat(capture?.format).isEqualTo("EAN13")
            assertThat(capture?.source).isEqualTo(ScanSource.CAMERA)
            assertThat(viewModel.uiState.value.ingredients).containsExactly(flour)
        }

    @Test
    fun `shows an error when the navigation arguments are missing`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel(code = null)

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.capture).isNull()
            assertThat(viewModel.uiState.value.error).isNotNull()
            assertThat(viewModel.uiState.value.canConfirm).isFalse()
        }

    @Test
    fun `creates a new ingredient through the products gateway`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onCreateNewChange(true)
            viewModel.onNewIngredientNameChange("海盐")
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientGateway.requests.single().newIngredientName).isEqualTo("海盐")
            assertThat(
                viewModel.uiState.value.result
                    ?.ingredientName,
            ).isEqualTo("海盐")
        }

    @Test
    fun `rejects invalid quantity before calling any gateway`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.onQuantityChange("0")
            viewModel.confirm()
            viewModel.onQuantityChange("abc")
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNotNull()
            assertThat(ingredientGateway.requests).isEmpty()
            assertThat(stockGateway.receivedOperationIds).isEmpty()
        }

    @Test
    fun `rejects confirmation without a target`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNotNull()
            assertThat(ingredientGateway.requests).isEmpty()
        }

    @Test
    fun `compensates a newly created ingredient when the receipt fails`() =
        runTest(dispatcher.scheduler) {
            stockGateway.failuresBeforeSuccess = 1
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onCreateNewChange(true)
            viewModel.onNewIngredientNameChange("海盐")
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientRepository.archived).containsExactly(IngredientId("ingredient-1"))
            assertThat(viewModel.uiState.value.result).isNull()
            assertThat(viewModel.uiState.value.lastError).contains("已撤回")
        }

    @Test
    fun `does not compensate an existing ingredient when the receipt fails`() =
        runTest(dispatcher.scheduler) {
            stockGateway.failuresBeforeSuccess = 1
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientRepository.archived).isEmpty()
            assertThat(viewModel.uiState.value.lastError).isNotNull()
        }

    private fun seedPreviousImport() {
        val capture = ScanCapture(rawValue = "6901234567892")
        val key = ScanImportKey.of(capture, "op-seed")
        val batchId = BatchId("batch-seed")
        val entry =
            InventoryLedgerEntry(
                id = LedgerEntryId("ledger-seed"),
                batchId = batchId,
                productId = ProductId("product-1"),
                locationId = LocationId("loc-1"),
                type = LedgerEntryType.PURCHASE_RECEIPT,
                stockUnit = gram,
                onHandDelta = 1.0,
                reservedDelta = 0.0,
                onHandAfter = 1.0,
                reservedAfter = 0.0,
                idempotencyKey = key,
                occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
            )
        inventoryRepository.entries[key] = entry
        inventoryRepository.ledger += entry
        inventoryRepository.items[batchId] =
            InventoryItem(
                batchId = batchId,
                productId = ProductId("product-1"),
                locationId = LocationId("loc-1"),
                stockUnit = gram,
                onHand = 1.0,
            )
    }

    private fun target(name: String = "低筋面粉"): ScanIngredientTarget =
        ScanIngredientTarget(
            ingredient = flour.copy(name = name),
            product = null,
            unit = gram,
            created = true,
        )

    private inner class FakeIngredientGateway : ScanIngredientGateway {
        val requests = mutableListOf<ScanIngredientRequest>()

        override suspend fun resolve(request: ScanIngredientRequest): Outcome<ScanIngredientTarget> {
            requests += request
            val created = request.existingIngredientId == null
            return Outcome.success(target(request.newIngredientName ?: "低筋面粉").copy(created = created))
        }
    }

    /**
     * 模拟真实入库端口的幂等语义：按 `scan-import:<条码>:<operation_id>` 查重，
     * 已存在则重放，否则新增一条批次 + 流水。这样"是否重复写入"的断言才有意义。
     */
    private inner class FakeStockGateway(
        private val inventory: FakeInventoryRepository,
    ) : ScanStockGateway {
        val receivedOperationIds = mutableListOf<String>()
        var failuresBeforeSuccess: Int = 0
        private var nextBatch: Int = 1
        private var nextEntry: Int = 1

        override suspend fun receive(request: ScanStockRequest): Outcome<ScanStockReceipt> {
            receivedOperationIds += request.operationId
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                return Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .Data(message = "db busy"),
                )
            }
            val key = ScanImportKey.of(request.capture, request.operationId)
            inventory.entries[key]?.let { existing ->
                val item =
                    inventory.items[existing.batchId]
                        ?: return Outcome.failure(
                            com.culinary.core.common.CulinaryError
                                .NotFound("inventory_batch", existing.batchId.value),
                        )
                return Outcome.success(ScanStockReceipt(existing, item, idempotentReplay = true))
            }
            val batchId = BatchId("batch-${nextBatch++}")
            val entry =
                InventoryLedgerEntry(
                    id = LedgerEntryId("ledger-${nextEntry++}"),
                    batchId = batchId,
                    productId = ProductId("product-1"),
                    locationId = LocationId("loc-1"),
                    type = LedgerEntryType.PURCHASE_RECEIPT,
                    stockUnit = gram,
                    onHandDelta = request.amount,
                    reservedDelta = 0.0,
                    onHandAfter = request.amount,
                    reservedAfter = 0.0,
                    idempotencyKey = key,
                    occurredAt = Instant.ofEpochMilli(1_760_000_100_000L),
                )
            val item =
                InventoryItem(
                    batchId = batchId,
                    productId = ProductId("product-1"),
                    locationId = LocationId("loc-1"),
                    stockUnit = gram,
                    onHand = request.amount,
                    batchCode = "B-${nextBatch - 1}",
                )
            inventory.entries[key] = entry
            inventory.ledger += entry
            inventory.items[batchId] = item
            return Outcome.success(ScanStockReceipt(entry, item, idempotentReplay = false))
        }
    }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private class FakeIngredientRepository(
        initial: List<Ingredient>,
    ) : IngredientRepository {
        private val state = MutableStateFlow(initial)
        val archived = mutableListOf<IngredientId>()

        override fun observeActive(): Flow<List<Ingredient>> = state

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            state.value
                .firstOrNull { it.id == id }
                ?.let { Outcome.success(it) }
                ?: Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .NotFound("ingredient", id.value),
                )

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> {
            state.value = state.value + ingredient
            return Outcome.success(ingredient.id)
        }

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> {
            archived += id
            state.value = state.value.filterNot { it.id == id }
            return Outcome.success(kotlin.Unit)
        }
    }

    private class FakeInventoryRepository : InventoryRepository {
        val entries = mutableMapOf<String, InventoryLedgerEntry>()
        val items = mutableMapOf<BatchId, InventoryItem>()
        val ledger = mutableListOf<InventoryLedgerEntry>()

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(entries[key])

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (entries.containsKey(entry.idempotencyKey)) return Outcome.success(false)
            entries[entry.idempotencyKey] = entry
            ledger += entry
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())
    }
}
