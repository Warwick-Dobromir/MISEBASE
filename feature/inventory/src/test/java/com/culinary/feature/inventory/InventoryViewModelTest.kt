package com.culinary.feature.inventory

import androidx.lifecycle.SavedStateHandle
import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.AdjustStockUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryLedgerUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockIssueUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 库存 ViewModel：加载、过滤、表单预选与用户动作。 */
@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val inventory = FakeInventoryRepository()
    private val ingredients = FakeIngredientRepository()
    private val units = FakeUnitRepository()
    private val clock = Clock { Instant.ofEpochMilli(1_760_000_000_000L) }
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        inventory.products += product()
        inventory.locations += StorageLocation(LocationId("loc-1"), "冷藏")
        inventory.items[BatchId("batch-1")] =
            InventoryItem(
                batchId = BatchId("batch-1"),
                productId = PRODUCT,
                locationId = LocationId("loc-1"),
                stockUnit = gram,
                onHand = 5.0,
                expiresOn = LocalDate.of(2027, 1, 1),
                batchCode = "B-001",
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `overview loads rows and applies search and low stock filters`() =
        runTest {
            val viewModel =
                InventoryOverviewViewModel(
                    ListInventoryOverviewUseCase(inventory, ingredients),
                )

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.rows).hasSize(1)
            viewModel.onQueryChange("面粉")
            assertThat(viewModel.uiState.value.visibleRows).hasSize(1)
            viewModel.onQueryChange("不存在")
            assertThat(viewModel.uiState.value.visibleRows).isEmpty()
            kotlin.Unit
        }

    @Test
    fun `ledger filters by type and query`() =
        runTest {
            inventory.ledger +=
                ledger(entryType = LedgerEntryType.PURCHASE_RECEIPT, id = "l1")
            inventory.ledger +=
                ledger(entryType = LedgerEntryType.WASTE, id = "l2")
            val viewModel =
                InventoryLedgerViewModel(
                    ListInventoryLedgerUseCase(inventory, ingredients),
                )

            advanceUntilIdle()

            assertThat(viewModel.uiState.value.rows).hasSize(2)
            viewModel.onTypeFilterChange(LedgerTypeFilter.RECEIPT)
            assertThat(viewModel.uiState.value.visibleRows).hasSize(1)
            assertThat(
                viewModel.uiState.value.visibleRows
                    .single()
                    .entry.type,
            ).isEqualTo(LedgerEntryType.PURCHASE_RECEIPT)
            kotlin.Unit
        }

    @Test
    fun `issue form preselects batch and writes stock through the domain use case`() =
        runTest {
            val viewModel = movementViewModel(InventoryRoutes.MODE_ISSUE, "batch-1")

            advanceUntilIdle()
            assertThat(viewModel.uiState.value.selectedBatchId).isEqualTo("batch-1")
            viewModel.onQuantityChange("2")
            viewModel.submit()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).isNull()
            assertThat(viewModel.uiState.value.saved).isTrue()
            assertThat(inventory.ledger).hasSize(1)
            assertThat(inventory.items.getValue(BatchId("batch-1")).onHand).isWithin(1e-9).of(3.0)
            kotlin.Unit
        }

    @Test
    fun `invalid quantity is rejected before writing`() =
        runTest {
            val viewModel = movementViewModel(InventoryRoutes.MODE_RECEIPT, null)

            advanceUntilIdle()
            viewModel.onQuantityChange("abc")
            viewModel.submit()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.error).contains("数量")
            assertThat(inventory.ledger).isEmpty()
            kotlin.Unit
        }

    private fun movementViewModel(
        mode: String,
        batchId: String?,
    ): StockMovementViewModel {
        val runner = ImmediateRunner
        return StockMovementViewModel(
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        InventoryRoutes.ARG_MODE to mode,
                        InventoryRoutes.ARG_BATCH_ID to batchId,
                    ),
                ),
            listOptions = ListInventoryFormOptionsUseCase(inventory, ingredients, units),
            recordReceipt =
                RecordStockReceiptUseCase(inventory, ingredients, units, runner, ids, clock),
            recordIssue = RecordStockIssueUseCase(inventory, runner, ids, clock),
            adjustStock = AdjustStockUseCase(inventory, runner, ids, clock),
        )
    }

    private fun product(): Product =
        Product(
            id = PRODUCT,
            ingredientId = FLOUR,
            name = "低筋面粉",
            stockUnit = gram,
            contentQty = 1.0,
            contentUnit = gram,
            stockToContentFactor = 1.0,
            status = EntityStatus.ACTIVE,
        )

    private fun ledger(
        entryType: LedgerEntryType,
        id: String,
    ): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id =
                com.culinary.core.domain.model
                    .LedgerEntryId(id),
            batchId = BatchId("batch-1"),
            productId = PRODUCT,
            locationId = LocationId("loc-1"),
            type = entryType,
            stockUnit = gram,
            onHandDelta = if (entryType == LedgerEntryType.PURCHASE_RECEIPT) 5.0 else -1.0,
            reservedDelta = 0.0,
            onHandAfter = 5.0,
            reservedAfter = 0.0,
            idempotencyKey = id,
            occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
            referenceType = "stock",
            referenceId = "ref-$id",
        )

    private class FakeInventoryRepository : InventoryRepository {
        val items = mutableMapOf<BatchId, InventoryItem>()
        val products = mutableListOf<Product>()
        val locations = mutableListOf<StorageLocation>()
        val ledger = mutableListOf<InventoryLedgerEntry>()

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
            val item = items.getValue(entry.batchId)
            items[entry.batchId] = item.copy(onHand = item.onHand + entry.onHandDelta)
            ledger += entry.copy(onHandAfter = item.onHand + entry.onHandDelta)
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())

        override suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(products)

        override suspend fun findProduct(id: ProductId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.id == id })

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.ingredientId == ingredientId })

        override suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(locations)

        override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> =
            Outcome.success(locations.firstOrNull { it.id == id })

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> {
            val id = BatchId("created-${items.size}")
            items[id] =
                InventoryItem(
                    batchId = id,
                    productId = batch.productId,
                    locationId = batch.locationId,
                    stockUnit = batch.stockUnit,
                    onHand = 0.0,
                )
            return Outcome.success(id)
        }

        override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
            Outcome.success(ledger.filter { it.batchId == batchId })
    }

    private class FakeIngredientRepository : IngredientRepository {
        private val flour =
            Ingredient(
                id = FLOUR,
                name = "低筋面粉",
                normalizedName = "低筋面粉",
                defaultUnitId = UnitId("g"),
                status = EntityStatus.ACTIVE,
            )

        override fun observeActive(): Flow<List<Ingredient>> = flowOf(listOf(flour))

        override suspend fun get(id: IngredientId): Outcome<Ingredient> = Outcome.success(flour)

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(kotlin.Unit)
    }

    private class FakeUnitRepository : UnitRepository {
        override suspend fun all(): List<Unit> = listOf(gram)

        override suspend fun find(id: UnitId): Unit? = if (id == gram.id) gram else null

        override suspend fun conversionsFor(ingredientId: IngredientId): List<Nothing> = emptyList()
    }

    private object ImmediateRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        val PRODUCT = ProductId("product-flour")
        val FLOUR = IngredientId("flour")
        val gram =
            Unit(
                id = UnitId("g"),
                code = "g",
                name = "克",
                symbol = "g",
                dimension = UnitDimension.MASS,
                baseFactor = 1.0,
                isBase = true,
                isApproximate = false,
                displayPrecision = 1,
            )
    }
}
