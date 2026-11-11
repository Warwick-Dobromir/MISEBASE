package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
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
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 库存入库/出库/调整：流水、余额、负库存拒绝、幂等键与事务边界。 */
class StockMovementUseCasesTest {
    private val inventory = FakeInventoryRepository()
    private val ingredients = FakeIngredientRepository()
    private val units = FakeUnitRepository()
    private val ids = IdGenerator { "id-${counter++}" }
    private var counter = 0

    @Test
    fun `receipt creates a batch and appends a purchase receipt ledger row`() =
        runBlocking {
            seedProduct()
            val useCase = receiptUseCase()

            val result =
                useCase(
                    StockReceiptInput(
                        quantity = Quantity(2.0, gram),
                        productId = PRODUCT,
                        batchCode = "B-001",
                        expiresOn = LocalDate.of(2027, 1, 1),
                        idempotencyKey = "receipt-1",
                    ),
                )

            val value = (result as Outcome.Success).value
            assertThat(value.entry.type).isEqualTo(LedgerEntryType.PURCHASE_RECEIPT)
            assertThat(value.entry.onHandDelta).isWithin(1e-9).of(2.0)
            assertThat(value.item.onHand).isWithin(1e-9).of(2.0)
            assertThat(value.item.batchCode).isEqualTo("B-001")
            assertThat(inventory.ledger).hasSize(1)
            assertThat(inventory.items).hasSize(1)
            assertThat(value.item.locationId).isEqualTo(LocationId("location-default"))
        }

    @Test
    fun `receipt is idempotent for the same key`() =
        runBlocking {
            seedProduct()
            val useCase = receiptUseCase()
            val input =
                StockReceiptInput(
                    quantity = Quantity(2.0, gram),
                    productId = PRODUCT,
                    idempotencyKey = "receipt-1",
                )

            useCase(input)
            val second = useCase(input)

            val value = (second as Outcome.Success).value
            assertThat(value.idempotentReplay).isTrue()
            assertThat(inventory.ledger).hasSize(1)
            assertThat(inventory.items).hasSize(1)
            assertThat(
                inventory.items.values
                    .single()
                    .onHand,
            ).isWithin(1e-9).of(2.0)
        }

    @Test
    fun `issue consumes available stock and records the reason type`() =
        runBlocking {
            val batchId = seedItem(onHand = 10.0)
            val useCase = issueUseCase()

            val result =
                useCase(
                    StockIssueInput(
                        batchId = batchId,
                        quantity = Quantity(3.0, gram),
                        reason = StockIssueReason.WASTE,
                        idempotencyKey = "issue-1",
                    ),
                )

            val value = (result as Outcome.Success).value
            assertThat(value.entry.type).isEqualTo(LedgerEntryType.WASTE)
            assertThat(value.entry.onHandDelta).isWithin(1e-9).of(-3.0)
            assertThat(value.item.onHand).isWithin(1e-9).of(7.0)
            assertThat(inventory.ledger).hasSize(1)
        }

    @Test
    fun `issue rejects an amount above available without writing`() =
        runBlocking {
            val batchId = seedItem(onHand = 2.0, reserved = 1.0)
            val useCase = issueUseCase()

            val result =
                useCase(
                    StockIssueInput(
                        batchId = batchId,
                        quantity = Quantity(1.5, gram),
                        idempotencyKey = "issue-1",
                    ),
                )

            val error = (result as Outcome.Failure).error
            assertThat(error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(inventory.ledger).isEmpty()
            assertThat(inventory.items.getValue(batchId).onHand).isWithin(1e-9).of(2.0)
        }

    @Test
    fun `issue is idempotent for the same key`() =
        runBlocking {
            val batchId = seedItem(onHand = 10.0)
            val useCase = issueUseCase()
            val input =
                StockIssueInput(
                    batchId = batchId,
                    quantity = Quantity(3.0, gram),
                    idempotencyKey = "issue-1",
                )

            useCase(input)
            val second = useCase(input)

            assertThat((second as Outcome.Success).value.idempotentReplay).isTrue()
            assertThat(inventory.ledger).hasSize(1)
            assertThat(inventory.items.getValue(batchId).onHand).isWithin(1e-9).of(7.0)
        }

    @Test
    fun `adjustment posts the difference as in or out`() =
        runBlocking {
            val batchId = seedItem(onHand = 10.0)
            val useCase = adjustmentUseCase()

            val up =
                useCase(
                    StockAdjustmentInput(
                        batchId = batchId,
                        countedQuantity = Quantity(12.0, gram),
                        idempotencyKey = "adjust-1",
                    ),
                )
            val down =
                useCase(
                    StockAdjustmentInput(
                        batchId = batchId,
                        countedQuantity = Quantity(9.0, gram),
                        idempotencyKey = "adjust-2",
                    ),
                )

            assertThat((up as Outcome.Success).value.entry.type).isEqualTo(LedgerEntryType.ADJUSTMENT_IN)
            assertThat((down as Outcome.Success).value.entry.type).isEqualTo(LedgerEntryType.ADJUSTMENT_OUT)
            assertThat((down as Outcome.Success).value.item.onHand).isWithin(1e-9).of(9.0)
        }

    @Test
    fun `adjustment rejects an unchanged count`() =
        runBlocking {
            val batchId = seedItem(onHand = 10.0)

            val result =
                adjustmentUseCase()(
                    StockAdjustmentInput(
                        batchId = batchId,
                        countedQuantity = Quantity(10.0, gram),
                    ),
                )

            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.VAL_001)
            assertThat(inventory.ledger).isEmpty()
        }

    @Test
    fun `receipt runs inside the transaction runner`() =
        runBlocking {
            seedProduct()
            var calls = 0
            val runner =
                object : TransactionRunner {
                    override suspend fun <T> write(block: suspend () -> T): Outcome<T> {
                        calls += 1
                        return Outcome.failure(CulinaryError.Unexpected("stop"))
                    }
                }

            val result =
                RecordStockReceiptUseCase(
                    inventory,
                    ingredients,
                    units,
                    runner,
                    ids,
                    clock,
                )(
                    StockReceiptInput(
                        quantity = Quantity(1.0, gram),
                        productId = PRODUCT,
                        idempotencyKey = "receipt-1",
                    ),
                )

            assertThat(calls).isEqualTo(1)
            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(inventory.ledger).isEmpty()
        }

    private fun receiptUseCase(): RecordStockReceiptUseCase =
        RecordStockReceiptUseCase(inventory, ingredients, units, ImmediateRunner, ids, clock)

    private fun issueUseCase(): RecordStockIssueUseCase =
        RecordStockIssueUseCase(inventory, ImmediateRunner, ids, clock)

    private fun adjustmentUseCase(): AdjustStockUseCase = AdjustStockUseCase(inventory, ImmediateRunner, ids, clock)

    private fun seedProduct() {
        inventory.products +=
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
    }

    private fun seedItem(
        onHand: Double,
        reserved: Double = 0.0,
    ): BatchId {
        seedProduct()
        val id = BatchId("batch-${counter++}")
        inventory.items[id] =
            InventoryItem(
                batchId = id,
                productId = PRODUCT,
                locationId = LocationId("location-1"),
                stockUnit = gram,
                onHand = onHand,
                reserved = reserved,
            )
        return id
    }

    private class FakeInventoryRepository : InventoryRepository {
        val items = mutableMapOf<BatchId, InventoryItem>()
        val ledger = mutableListOf<InventoryLedgerEntry>()
        val products = mutableListOf<Product>()
        val locations = mutableListOf<StorageLocation>()

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
            val item = items.getValue(entry.batchId)
            val newOnHand = item.onHand + entry.onHandDelta
            val newReserved = item.reserved + entry.reservedDelta
            if (newOnHand < 0.0 || newReserved < 0.0 || newReserved > newOnHand) {
                return Outcome.failure(
                    CulinaryError.Domain(
                        com.culinary.core.common.DomainDetail.InsufficientStock(
                            entry.productId?.value.orEmpty(),
                            -entry.onHandDelta,
                            item.available,
                        ),
                        ErrorCode.INV_003,
                    ),
                )
            }
            items[entry.batchId] = item.copy(onHand = newOnHand, reserved = newReserved)
            ledger += entry.copy(onHandAfter = newOnHand, reservedAfter = newReserved)
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())

        override suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(products.toList())

        override suspend fun findProduct(id: ProductId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.id == id })

        override suspend fun saveProduct(product: Product): Outcome<ProductId> {
            products += product
            return Outcome.success(product.id)
        }

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.ingredientId == ingredientId })

        override suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(locations.toList())

        override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> =
            Outcome.success(locations.firstOrNull { it.id == id })

        override suspend fun saveLocation(location: StorageLocation): Outcome<LocationId> {
            locations += location
            return Outcome.success(location.id)
        }

        override suspend fun ensureDefaultLocation(): Outcome<StorageLocation> {
            val existing = locations.firstOrNull()
            if (existing != null) return Outcome.success(existing)
            val created = StorageLocation(LocationId("location-default"), "默认储藏")
            locations += created
            return Outcome.success(created)
        }

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> {
            val id = BatchId("batch-${items.size}")
            items[id] =
                InventoryItem(
                    batchId = id,
                    productId = batch.productId,
                    locationId = batch.locationId,
                    stockUnit = batch.stockUnit,
                    onHand = 0.0,
                    expiresOn = batch.expiresOn,
                    batchCode = batch.batchCode,
                    receivedAt = batch.receivedAt,
                )
            return Outcome.success(id)
        }

        override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
            Outcome.success(ledger.filter { it.batchId == batchId })
    }

    private class FakeIngredientRepository : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> = flowOf(listOf(flour))

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            if (id == FLOUR) Outcome.success(flour) else Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

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
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val clock = Clock { NOW }
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
        val flour =
            Ingredient(
                id = FLOUR,
                name = "低筋面粉",
                normalizedName = "低筋面粉",
                defaultUnitId = UnitId("g"),
                status = EntityStatus.ACTIVE,
            )
    }
}
