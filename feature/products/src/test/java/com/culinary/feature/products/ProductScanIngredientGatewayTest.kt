package com.culinary.feature.products

import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.scan.ScanIngredientRequest
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * 扫码→食材接口（`feature:products` 实现）：已有食材复用、新建食材走领域用例、
 * 单位回退、缺少目标时报校验错误。
 */
class ProductScanIngredientGatewayTest {
    private val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
    private val piece = Unit(UnitId("pc"), "pc", "个", "个", UnitDimension.COUNT, 1.0, true, false, 0)

    private fun newGateway(
        ingredientRepository: FakeIngredientRepository = FakeIngredientRepository(),
        units: List<Unit> = listOf(gram),
    ): ProductScanIngredientGateway =
        ProductScanIngredientGateway(
            ingredientRepository = ingredientRepository,
            inventoryRepository = FakeInventoryRepository(),
            unitRepository = FakeUnitRepository(units),
            saveIngredient =
                SaveIngredientUseCase(
                    repository = ingredientRepository,
                    idGenerator = IdGenerator { "generated-id" },
                    transactionRunner = ImmediateTransactionRunner,
                ),
        )

    @Test
    fun `reuses an existing ingredient and its default unit`() =
        runTest {
            val repository = FakeIngredientRepository(listOf(ingredient(name = "低筋面粉", defaultUnitId = UnitId("g"))))
            val gateway = newGateway(repository)

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "6901234567892"),
                        existingIngredientId = IngredientId("ingredient-1"),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val target = (result as Outcome.Success).value
            assertThat(target.ingredient.name).isEqualTo("低筋面粉")
            assertThat(target.unit.code).isEqualTo("g")
            assertThat(target.created).isFalse()
            assertThat(target.product).isNull()
        }

    @Test
    fun `creates a new ingredient through the save use case`() =
        runTest {
            val repository = FakeIngredientRepository()
            val gateway = newGateway(repository)

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "6901234567892"),
                        newIngredientName = "  海盐  ",
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val target = (result as Outcome.Success).value
            assertThat(target.ingredient.name).isEqualTo("海盐")
            assertThat(target.ingredient.id).isEqualTo(IngredientId("generated-id"))
            assertThat(target.created).isTrue()
            assertThat(repository.saved.single().name).isEqualTo("海盐")
        }

    @Test
    fun `falls back to the first available unit when the ingredient has no default`() =
        runTest {
            val repository = FakeIngredientRepository(listOf(ingredient(name = "鸡蛋", defaultUnitId = null)))
            val gateway = newGateway(repository, units = listOf(piece))

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "kitchen-qr"),
                        existingIngredientId = IngredientId("ingredient-1"),
                    ),
                )

            val target = (result as Outcome.Success).value
            assertThat(target.unit.code).isEqualTo("pc")
        }

    @Test
    fun `created ingredients get a sensible default unit instead of the first table row`() =
        runTest {
            val milligram = Unit(UnitId("mg"), "mg", "毫克", "mg", UnitDimension.MASS, 0.001, false, false, 0)
            val repository = FakeIngredientRepository()
            val gateway = newGateway(repository, units = listOf(milligram, gram, piece))

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "6901234567892"),
                        newIngredientName = "海盐",
                    ),
                )

            val target = (result as Outcome.Success).value
            assertThat(target.ingredient.defaultUnitId).isEqualTo(UnitId("g"))
            assertThat(target.unit.code).isEqualTo("g")
        }

    @Test
    fun `existing ingredient without default unit falls back to the mass base unit`() =
        runTest {
            val milligram = Unit(UnitId("mg"), "mg", "毫克", "mg", UnitDimension.MASS, 0.001, false, false, 0)
            val repository = FakeIngredientRepository(listOf(ingredient(name = "海盐", defaultUnitId = null)))
            val gateway = newGateway(repository, units = listOf(milligram, gram))

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "code"),
                        existingIngredientId = IngredientId("ingredient-1"),
                    ),
                )

            assertThat((result as Outcome.Success).value.unit.code).isEqualTo("g")
        }

    @Test
    fun `fails when neither an ingredient nor a name is provided`() =
        runTest {
            val gateway = newGateway()

            val result = gateway.resolve(ScanIngredientRequest(capture = ScanCapture(rawValue = "code")))

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        }

    @Test
    fun `fails when the ingredient no longer exists`() =
        runTest {
            val gateway = newGateway()

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "code"),
                        existingIngredientId = IngredientId("missing"),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        }

    @Test
    fun `reports the mapped product for an existing ingredient`() =
        runTest {
            val repository = FakeIngredientRepository(listOf(ingredient(name = "低筋面粉", defaultUnitId = UnitId("g"))))
            val gateway =
                ProductScanIngredientGateway(
                    ingredientRepository = repository,
                    inventoryRepository = FakeInventoryRepository(product = product(gram)),
                    unitRepository = FakeUnitRepository(listOf(gram)),
                    saveIngredient =
                        SaveIngredientUseCase(
                            repository = repository,
                            idGenerator = IdGenerator { "generated-id" },
                            transactionRunner = ImmediateTransactionRunner,
                        ),
                )

            val result =
                gateway.resolve(
                    ScanIngredientRequest(
                        capture = ScanCapture(rawValue = "code"),
                        existingIngredientId = IngredientId("ingredient-1"),
                    ),
                )

            val target = (result as Outcome.Success).value
            assertThat(target.product).isNotNull()
            assertThat(target.product?.name).isEqualTo("低筋面粉 1kg")
        }

    private fun ingredient(
        name: String,
        defaultUnitId: UnitId?,
    ): Ingredient =
        Ingredient(
            id = IngredientId("ingredient-1"),
            name = name,
            normalizedName = name,
            defaultUnitId = defaultUnitId,
            status = EntityStatus.ACTIVE,
        )

    private fun product(stockUnit: Unit): Product =
        Product(
            id = ProductId("product-1"),
            ingredientId = IngredientId("ingredient-1"),
            name = "低筋面粉 1kg",
            stockUnit = stockUnit,
            contentQty = 1.0,
            contentUnit = stockUnit,
            stockToContentFactor = 1.0,
        )

    private object ImmediateTransactionRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private class FakeIngredientRepository(
        initial: List<Ingredient> = emptyList(),
    ) : IngredientRepository {
        private val state = MutableStateFlow(initial)
        val saved = mutableListOf<Ingredient>()

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
            saved += ingredient
            state.value = state.value + ingredient
            return Outcome.success(ingredient.id)
        }

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(kotlin.Unit)
    }

    private class FakeUnitRepository(
        private val units: List<Unit>,
    ) : UnitRepository {
        override suspend fun all(): List<Unit> = units

        override suspend fun find(id: UnitId): Unit? = units.firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> = emptyList()
    }

    private class FakeInventoryRepository(
        private val product: Product? = null,
    ) : InventoryRepository {
        override suspend fun listItems(): Outcome<List<com.culinary.core.domain.model.InventoryItem>> =
            Outcome.success(emptyList())

        override suspend fun findItem(
            batchId: com.culinary.core.domain.model.BatchId,
        ): Outcome<com.culinary.core.domain.model.InventoryItem?> = Outcome.success(null)

        override suspend fun findByIdempotencyKey(
            key: String,
        ): Outcome<com.culinary.core.domain.model.InventoryLedgerEntry?> = Outcome.success(null)

        override suspend fun appendIfAbsent(
            entry: com.culinary.core.domain.model.InventoryLedgerEntry,
        ): Outcome<Boolean> = Outcome.success(true)

        override suspend fun ledger(): Outcome<List<com.culinary.core.domain.model.InventoryLedgerEntry>> =
            Outcome.success(emptyList())

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            Outcome.success(product)
    }
}
