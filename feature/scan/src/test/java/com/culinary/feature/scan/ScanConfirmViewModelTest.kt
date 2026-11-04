package com.culinary.feature.scan

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.Outcome
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
 * 导入确认页 ViewModel：匹配结果展示、幂等拦截、数量校验、接口编排与补偿。
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
    private val stockGateway = FakeStockGateway()

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
        )

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
    fun `reports a previous import for the same code and blocks confirmation`() =
        runTest(dispatcher.scheduler) {
            inventoryRepository.entries[ScanImportKey.of("6901234567892")] = ledgerEntry()
            val viewModel = newViewModel()

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.previous).isNotNull()
            assertThat(viewModel.uiState.value.canConfirm).isFalse()

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNotNull()
            assertThat(stockGateway.requests).isEmpty()
        }

    @Test
    fun `imports an existing ingredient through both gateways`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onSelectIngredient("ingredient-1")
            viewModel.onQuantityChange("2")
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientGateway.requests.single().existingIngredientId).isEqualTo(IngredientId("ingredient-1"))
            assertThat(stockGateway.requests.single().amount).isEqualTo(2.0)
            assertThat(
                viewModel.uiState.value.result
                    ?.ingredientName,
            ).isEqualTo("低筋面粉")
            assertThat(
                viewModel.uiState.value.result
                    ?.replayed,
            ).isFalse()
            assertThat(viewModel.uiState.value.previous).isNotNull()
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
            assertThat(stockGateway.requests).isEmpty()
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
            stockGateway.failure =
                Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .Conflict("inventory_ledger"),
                )
            val viewModel = newViewModel()
            advanceUntilIdle()

            viewModel.onCreateNewChange(true)
            viewModel.onNewIngredientNameChange("海盐")
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientRepository.archived).containsExactly(IngredientId("ingredient-1"))
            assertThat(viewModel.uiState.value.result).isNull()
            assertThat(viewModel.uiState.value.error).contains("已撤回")
        }

    @Test
    fun `does not compensate an existing ingredient when the receipt fails`() =
        runTest(dispatcher.scheduler) {
            stockGateway.failure =
                Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .Conflict("inventory_ledger"),
                )
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(ingredientRepository.archived).isEmpty()
            assertThat(viewModel.uiState.value.error).isNotNull()
        }

    @Test
    fun `surfaces an idempotent replay from the inventory gateway`() =
        runTest(dispatcher.scheduler) {
            stockGateway.replay = true
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value.result
                    ?.replayed,
            ).isTrue()
        }

    @Test
    fun `cannot confirm twice after a successful import`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            advanceUntilIdle()
            viewModel.onSelectIngredient("ingredient-1")

            viewModel.confirm()
            advanceUntilIdle()
            viewModel.confirm()
            advanceUntilIdle()

            assertThat(stockGateway.requests).hasSize(1)
        }

    private fun ledgerEntry(): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId("ledger-1"),
            batchId = BatchId("batch-1"),
            productId = ProductId("product-1"),
            locationId = LocationId("loc-1"),
            type = LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit = gram,
            onHandDelta = 1.0,
            reservedDelta = 0.0,
            onHandAfter = 1.0,
            reservedAfter = 0.0,
            idempotencyKey = ScanImportKey.of("6901234567892"),
            occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
        )

    private fun target(
        name: String = "低筋面粉",
        created: Boolean = true,
    ): ScanIngredientTarget =
        ScanIngredientTarget(
            ingredient = flour.copy(name = name),
            product = null,
            unit = gram,
            created = created,
        )

    private inner class FakeIngredientGateway : ScanIngredientGateway {
        val requests = mutableListOf<ScanIngredientRequest>()

        override suspend fun resolve(request: ScanIngredientRequest): Outcome<ScanIngredientTarget> {
            requests += request
            val target = target(request.newIngredientName ?: "低筋面粉", created = request.existingIngredientId == null)
            return Outcome.success(target)
        }
    }

    private inner class FakeStockGateway : ScanStockGateway {
        val requests = mutableListOf<ScanStockRequest>()
        var failure: Outcome<Nothing>? = null
        var replay: Boolean = false

        override suspend fun receive(request: ScanStockRequest): Outcome<ScanStockReceipt> {
            requests += request
            failure?.let { return it }
            return Outcome.success(
                ScanStockReceipt(
                    entry = ledgerEntry(),
                    item =
                        InventoryItem(
                            batchId = BatchId("batch-1"),
                            productId = ProductId("product-1"),
                            locationId = LocationId("loc-1"),
                            stockUnit = gram,
                            onHand = 1.0,
                            batchCode = "B-001",
                        ),
                    idempotentReplay = replay,
                ),
            )
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

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(emptyList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(null)

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(entries[key])

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> = Outcome.success(true)

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(entries.values.toList())
    }
}
