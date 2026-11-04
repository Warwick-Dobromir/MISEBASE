package com.culinary.feature.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
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
import com.culinary.core.domain.scan.ScanImportKey
import com.culinary.core.domain.scan.ScanIngredientTarget
import com.culinary.core.domain.scan.ScanStockRequest
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * 扫码→入库接口（`feature:inventory` 实现）：同一条码幂等、批次与流水同一事务、
 * 商品按食材补建、非法数量被拒绝。
 */
class InventoryScanStockGatewayTest {
    private val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    private val ingredient =
        Ingredient(
            id = IngredientId("ingredient-1"),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId("g"),
            status = EntityStatus.ACTIVE,
        )

    private fun target(): ScanIngredientTarget =
        ScanIngredientTarget(ingredient = ingredient, product = null, unit = gram, created = false)

    private fun newGateway(
        inventoryRepository: FakeInventoryRepository = FakeInventoryRepository(),
        ingredientRepository: FakeIngredientRepository = FakeIngredientRepository(listOf(ingredient)),
        unitRepository: FakeUnitRepository = FakeUnitRepository(listOf(gram)),
    ): Pair<InventoryScanStockGateway, FakeInventoryRepository> {
        val receipt =
            RecordStockReceiptUseCase(
                inventoryRepository = inventoryRepository,
                ingredientRepository = ingredientRepository,
                unitRepository = unitRepository,
                transactionRunner = ImmediateTransactionRunner,
                idGenerator = IdGenerator { "generated-${inventoryRepository.nextId++}" },
                clock = Clock { Instant.ofEpochMilli(1_760_000_000_000L) },
            )
        return InventoryScanStockGateway(receipt) to inventoryRepository
    }

    private fun request(
        capture: ScanCapture = ScanCapture(rawValue = "6901234567892"),
        amount: Double = 2.0,
        operationId: String = "op-1",
    ): ScanStockRequest =
        ScanStockRequest(
            capture = capture,
            target = target(),
            amount = amount,
            operationId = operationId,
        )

    @Test
    fun `receives stock with an event based idempotency key`() =
        runTest {
            val (gateway, inventory) = newGateway()

            val result = gateway.receive(request(operationId = "op-1"))

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val receipt = (result as Outcome.Success).value
            assertThat(receipt.idempotentReplay).isFalse()
            assertThat(receipt.entry.idempotencyKey)
                .isEqualTo(ScanImportKey.of(ScanCapture(rawValue = "6901234567892"), "op-1"))
            assertThat(receipt.item.onHand).isEqualTo(2.0)
            assertThat(inventory.batches).hasSize(1)
            assertThat(inventory.entries).hasSize(1)
        }

    @Test
    fun `same operation retried does not add stock again`() =
        runTest {
            val (gateway, inventory) = newGateway()

            val first = gateway.receive(request(operationId = "op-1"))
            val second = gateway.receive(request(operationId = "op-1"))

            val firstReceipt = (first as Outcome.Success).value
            val secondReceipt = (second as Outcome.Success).value
            assertThat(firstReceipt.idempotentReplay).isFalse()
            assertThat(secondReceipt.idempotentReplay).isTrue()
            assertThat(secondReceipt.entry.id).isEqualTo(firstReceipt.entry.id)
            assertThat(inventory.batches).hasSize(1)
            assertThat(inventory.entries).hasSize(1)
            assertThat(
                inventory.batches.values
                    .single()
                    .onHand,
            ).isEqualTo(2.0)
        }

    @Test
    fun `the same code with a new operation id creates a second import`() =
        runTest {
            val (gateway, inventory) = newGateway()

            // MB-S5-002 回归：买第二件同款商品时必须能再次入库。
            val first = gateway.receive(request(operationId = "op-1"))
            val second = gateway.receive(request(operationId = "op-2"))

            val firstReceipt = (first as Outcome.Success).value
            val secondReceipt = (second as Outcome.Success).value
            assertThat(firstReceipt.idempotentReplay).isFalse()
            assertThat(secondReceipt.idempotentReplay).isFalse()
            assertThat(secondReceipt.entry.id).isNotEqualTo(firstReceipt.entry.id)
            assertThat(inventory.batches).hasSize(2)
            assertThat(inventory.entries).hasSize(2)
            assertThat(inventory.batches.values.map { it.onHand }).containsExactly(2.0, 2.0)
        }

    @Test
    fun `a different code creates its own batch and ledger entry`() =
        runTest {
            val (gateway, inventory) = newGateway()

            gateway.receive(request(capture = ScanCapture(rawValue = "code-a"), operationId = "op-a"))
            gateway.receive(request(capture = ScanCapture(rawValue = "code-b"), operationId = "op-b"))

            assertThat(inventory.batches).hasSize(2)
            assertThat(inventory.entries).hasSize(2)
        }

    @Test
    fun `rejects a blank operation id without writing`() =
        runTest {
            val (gateway, inventory) = newGateway()

            val result = gateway.receive(request(operationId = "   "))

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(inventory.batches).isEmpty()
            assertThat(inventory.entries).isEmpty()
        }

    @Test
    fun `creates the missing product for the ingredient`() =
        runTest {
            val (gateway, inventory) = newGateway()

            val result = gateway.receive(request())

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(inventory.products).hasSize(1)
            assertThat(
                inventory.products.values
                    .single()
                    .ingredientId,
            ).isEqualTo(ingredient.id)
            assertThat(
                inventory.products.values
                    .single()
                    .stockUnit,
            ).isEqualTo(gram)
        }

    @Test
    fun `rejects non positive amounts without writing`() =
        runTest {
            val (gateway, inventory) = newGateway()

            val zero = gateway.receive(request(amount = 0.0, operationId = "op-zero"))
            val negative = gateway.receive(request(amount = -1.0, operationId = "op-negative"))

            assertThat(zero).isInstanceOf(Outcome.Failure::class.java)
            assertThat(negative).isInstanceOf(Outcome.Failure::class.java)
            assertThat(inventory.batches).isEmpty()
            assertThat(inventory.entries).isEmpty()
        }

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private class FakeIngredientRepository(
        private val ingredients: List<Ingredient> = emptyList(),
    ) : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> = flowOf(ingredients)

        override suspend fun get(id: IngredientId): Outcome<Ingredient> =
            ingredients
                .firstOrNull { it.id == id }
                ?.let { Outcome.success(it) }
                ?: Outcome.failure(
                    com.culinary.core.common.CulinaryError
                        .NotFound("ingredient", id.value),
                )

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(kotlin.Unit)
    }

    private class FakeUnitRepository(
        private val units: List<Unit>,
    ) : UnitRepository {
        override suspend fun all(): List<Unit> = units

        override suspend fun find(id: UnitId): Unit? = units.firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> = emptyList()
    }

    private class FakeInventoryRepository : InventoryRepository {
        val products = mutableMapOf<ProductId, Product>()
        val batches = mutableMapOf<BatchId, InventoryItem>()
        val entries = mutableMapOf<String, InventoryLedgerEntry>()
        var nextId: Int = 1

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(batches.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(batches[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(entries[key])

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (entries.containsKey(entry.idempotencyKey)) return Outcome.success(false)
            val batch =
                batches[entry.batchId]
                    ?: return Outcome.failure(
                        com.culinary.core.common.CulinaryError
                            .NotFound("inventory_batch", entry.batchId.value),
                    )
            val onHand = batch.onHand + entry.onHandDelta
            batches[entry.batchId] = batch.copy(onHand = onHand)
            entries[entry.idempotencyKey] = entry.copy(onHandAfter = onHand)
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(entries.values.toList())

        override suspend fun findProduct(id: ProductId): Outcome<Product?> = Outcome.success(products[id])

        override suspend fun saveProduct(product: Product): Outcome<ProductId> {
            products[product.id] = product
            return Outcome.success(product.id)
        }

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            Outcome.success(products.values.firstOrNull { it.ingredientId == ingredientId })

        override suspend fun ensureDefaultLocation(): Outcome<StorageLocation> =
            Outcome.success(StorageLocation(LocationId("loc-default"), "默认储藏"))

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> {
            val id = BatchId("batch-${nextId++}")
            batches[id] =
                InventoryItem(
                    batchId = id,
                    productId = batch.productId,
                    locationId = batch.locationId,
                    stockUnit = batch.stockUnit,
                    onHand = 0.0,
                )
            return Outcome.success(id)
        }
    }
}
