package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/** 需求汇总/缺口/采购清单/计算记录接线（MB-D-014/015/016/017）。 */
class RequirementPlanUseCasesTest {
    private val recipes = FakeRecipeRepository()
    private val sauces = FakeSauceRepository()
    private val ingredients = FakeIngredientRepository()
    private val inventory = FakeInventoryRepository()
    private val units = FakeUnitRepository()
    private val calculations = FakeCalculationRecordRepository()
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }
    private val clock = Clock { NOW }

    private val expand = ExpandRecipeUseCase(recipes, ingredients, sauces)
    private val buildPlan =
        BuildRequirementPlanUseCase(
            expandRecipe = expand,
            recipeRepository = recipes,
            ingredientRepository = ingredients,
            inventoryRepository = inventory,
            unitRepository = units,
            idGenerator = ids,
            clock = clock,
        )

    @Test
    fun `plan aggregates recipes and sauces and separates optional gaps`() =
        runBlocking<kotlin.Unit> {
            seedGraph()
            val plan = (buildPlan(ROOT_VERSION, 4.0) as Outcome.Success).value

            assertThat(plan.recipeName).isEqualTo("测试主菜")
            assertThat(plan.baseServings).isEqualTo(2)
            val flour = plan.requiredLines.first { it.ingredientId == FLOUR }
            assertThat(flour.required.amount).isWithin(1e-9).of(1000.0)
            assertThat(flour.available.amount).isWithin(1e-9).of(200.0)
            assertThat(flour.shortfall.amount).isWithin(1e-9).of(800.0)
            val sugar = plan.requiredLines.first { it.ingredientId == SUGAR }
            assertThat(sugar.shortfall.amount).isWithin(1e-9).of(150.0)
            assertThat(plan.optionalLines).hasSize(1)
            assertThat(plan.optionalLines.single().ingredientId).isEqualTo(SALT)
            assertThat(plan.optionalLines.single().isOptional).isTrue()
            assertThat(plan.purchaseList.lines.map { it.ingredientId }).containsExactly(FlourId, SugarId).inOrder()
            assertThat(plan.record.lines).hasSize(3)
            assertThat(plan.record.engineVersion).isEqualTo("stage4-1")
            kotlin.Unit
        }

    @Test
    fun `plan scales the target servings and reports an unconvertible stock unit as zero`() =
        runBlocking<kotlin.Unit> {
            seedGraph(stockUnit = piece)
            val plan = (buildPlan(ROOT_VERSION, 4.0) as Outcome.Success).value

            val flour = plan.requiredLines.first { it.ingredientId == FLOUR }
            assertThat(flour.available.amount).isWithin(1e-9).of(0.0)
            assertThat(flour.shortfall.amount).isWithin(1e-9).of(1000.0)
            kotlin.Unit
        }

    @Test
    fun `generating a purchase list writes one immutable confirmed record`() =
        runBlocking<kotlin.Unit> {
            seedGraph()
            val generate = GeneratePurchaseListUseCase(buildPlan, calculations, ImmediateRunner, clock)

            val first = (generate(ROOT_VERSION, 4.0) as Outcome.Success).value
            val second = (generate(ROOT_VERSION, 4.0) as Outcome.Success).value

            assertThat(first.record.status).isEqualTo(CalculationRecordStatus.CONFIRMED)
            assertThat(first.record.confirmedAt).isEqualTo(NOW)
            assertThat(first.plan.purchaseList.lines).hasSize(2)
            assertThat(calculations.records).hasSize(1)
            assertThat(second.record.id).isEqualTo(first.record.id)
            assertThat(second.record.payloadHash).isEqualTo(first.record.payloadHash)
            kotlin.Unit
        }

    @Test
    fun `confirming a deduction with a required gap writes nothing`() =
        runBlocking<kotlin.Unit> {
            seedGraph()
            val confirm = deductionUseCase()

            val result = confirm(ROOT_VERSION, 4.0)

            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(calculations.records).isEmpty()
            assertThat(inventory.ledger).isEmpty()
            kotlin.Unit
        }

    @Test
    fun `confirming a covered deduction writes the record and ledger atomically`() =
        runBlocking<kotlin.Unit> {
            seedGraph(flourStock = 1600.0, sugarStock = 500.0)
            val confirm = deductionUseCase()

            val result = (confirm(ROOT_VERSION, 4.0) as Outcome.Success).value

            assertThat(result.record.status).isEqualTo(CalculationRecordStatus.CONFIRMED)
            assertThat(result.appliedEntryCount).isEqualTo(2)
            assertThat(calculations.records).hasSize(1)
            assertThat(inventory.ledger).hasSize(2)
            assertThat(
                inventory.items
                    .getValue(BatchId("batch-flour"))
                    .onHand,
            ).isWithin(1e-9).of(600.0)
            assertThat(
                inventory.items
                    .getValue(BatchId("batch-sugar"))
                    .onHand,
            ).isWithin(1e-9).of(300.0)
            kotlin.Unit
        }

    @Test
    fun `repeated deduction with the same record payload does not double write`() =
        runBlocking<kotlin.Unit> {
            seedGraph(flourStock = 2600.0, sugarStock = 1000.0)
            val confirm = deductionUseCase()
            confirm(ROOT_VERSION, 4.0)

            confirm(ROOT_VERSION, 4.0)

            assertThat(calculations.records).hasSize(1)
            assertThat(inventory.ledger).hasSize(2)
            assertThat(
                inventory.items
                    .getValue(BatchId("batch-flour"))
                    .onHand,
            ).isWithin(1e-9).of(1600.0)
            kotlin.Unit
        }

    @Test
    fun `calculation records can be listed and replayed by id`() =
        runBlocking<kotlin.Unit> {
            seedGraph()
            GeneratePurchaseListUseCase(buildPlan, calculations, ImmediateRunner, clock)(ROOT_VERSION, 4.0)
            val record = (ListCalculationRecordsUseCase(calculations)(ROOT_VERSION) as Outcome.Success).value.single()
            val replayed =
                (GetCalculationRecordUseCase(calculations)(record.id) as Outcome.Success).value

            assertThat(replayed?.payloadHash).isEqualTo(record.payloadHash)
            assertThat(replayed?.lines).hasSize(3)
            kotlin.Unit
        }

    private fun deductionUseCase(): ConfirmRequirementDeductionUseCase =
        ConfirmRequirementDeductionUseCase(
            buildPlan = buildPlan,
            inventoryRepository = inventory,
            unitRepository = units,
            calculationRepository = calculations,
            transactionRunner = ImmediateRunner,
            idGenerator = ids,
            clock = clock,
        )

    private fun seedGraph(
        stockUnit: Unit = gram,
        flourStock: Double = 200.0,
        sugarStock: Double = 50.0,
    ) {
        recipes.recipes +=
            Recipe(
                id = ROOT_RECIPE,
                name = "测试主菜",
                normalizedName = "测试主菜",
                category = RecipeCategory.DISH,
                status = EntityStatus.ACTIVE,
            )
        recipes.versions[ROOT_VERSION] =
            RecipeVersion(
                id = ROOT_VERSION,
                recipeId = ROOT_RECIPE,
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(1000.0, gram),
                servings = 2,
                components =
                    listOf(
                        RecipeComponent(1, ComponentTarget.Ingredient(FLOUR), Quantity(300.0, gram)),
                        RecipeComponent(2, ComponentTarget.Ingredient(SUGAR), Quantity(100.0, gram)),
                        RecipeComponent(
                            3,
                            ComponentTarget.Ingredient(SALT),
                            Quantity(10.0, gram),
                            isOptional = true,
                        ),
                        RecipeComponent(
                            4,
                            ComponentTarget.Sauce(SAUCE, SAUCE_VERSION),
                            Quantity(200.0, gram),
                        ),
                    ),
                isCurrent = true,
            )
        sauces.versions[SAUCE_VERSION] =
            SauceVersion(
                id = SAUCE_VERSION,
                sauceId = SAUCE,
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(100.0, gram),
                components =
                    listOf(
                        SauceComponent(1, ComponentTarget.Ingredient(FLOUR), Quantity(100.0, gram)),
                    ),
                isCurrent = true,
            )
        inventory.products += product(FLOUR, "面粉", stockUnit)
        inventory.products += product(SUGAR, "糖", stockUnit)
        inventory.items[BatchId("batch-flour")] =
            InventoryItem(
                batchId = BatchId("batch-flour"),
                productId = ProductId("product-flour"),
                locationId = LocationId("loc"),
                stockUnit = stockUnit,
                onHand = flourStock,
            )
        inventory.items[BatchId("batch-sugar")] =
            InventoryItem(
                batchId = BatchId("batch-sugar"),
                productId = ProductId("product-sugar"),
                locationId = LocationId("loc"),
                stockUnit = stockUnit,
                onHand = sugarStock,
            )
    }

    private fun product(
        ingredientId: IngredientId,
        name: String,
        unit: Unit,
    ): Product =
        Product(
            id = ProductId("product-${ingredientId.value}"),
            ingredientId = ingredientId,
            name = name,
            stockUnit = unit,
            contentQty = 1.0,
            contentUnit = unit,
            stockToContentFactor = 1.0,
        )

    private class FakeRecipeRepository : RecipeRepository {
        val recipes = mutableListOf<Recipe>()
        val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()

        override fun observeActive(): Flow<List<Recipe>> = flowOf(recipes)

        override suspend fun get(id: RecipeId): Outcome<Recipe> =
            recipes.firstOrNull { it.id == id }?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("recipe", id.value))

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? =
            versions.values.firstOrNull { it.recipeId == recipeId && it.isCurrent }

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> =
            versions.values.filter { it.recipeId == recipeId }

        override suspend fun nextVersionNo(recipeId: RecipeId): Int = 2

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> = Outcome.success(recipe.id)

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId = version.id

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> =
            Outcome.success(version.id)
    }

    private class FakeSauceRepository : SauceRepository {
        val versions = mutableMapOf<SauceVersionId, SauceVersion>()

        override fun observeActive(): Flow<List<com.culinary.core.domain.model.Sauce>> = flowOf(emptyList())

        override suspend fun get(id: SauceId): Outcome<com.culinary.core.domain.model.Sauce> =
            Outcome.failure(CulinaryError.NotFound("sauce", id.value))

        override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
            versions[id]?.let { Outcome.success(it) }
                ?: Outcome.failure(CulinaryError.NotFound("sauce_version", id.value))

        override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> = emptyList()

        override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? = null

        override suspend fun nextVersionNo(sauceId: SauceId): Int = 2

        override suspend fun save(sauce: com.culinary.core.domain.model.Sauce): Outcome<SauceId> =
            Outcome.success(sauce.id)

        override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> = Outcome.success(version.id)

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> = Outcome.success(version.id)
    }

    private class FakeIngredientRepository : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> =
            flowOf(
                listOf(
                    ingredient(FLOUR, "面粉"),
                    ingredient(SUGAR, "糖"),
                    ingredient(SALT, "盐"),
                ),
            )

        override suspend fun get(id: IngredientId): Outcome<Ingredient> = Outcome.success(ingredient(id, id.value))

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(kotlin.Unit)
    }

    private class FakeUnitRepository : UnitRepository {
        override suspend fun all(): List<Unit> = listOf(gram, piece)

        override suspend fun find(id: UnitId): Unit? = listOf(gram, piece).firstOrNull { it.id == id }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<Nothing> = emptyList()
    }

    private class FakeInventoryRepository : InventoryRepository {
        val items = mutableMapOf<BatchId, InventoryItem>()
        val products = mutableListOf<Product>()
        val ledger = mutableListOf<InventoryLedgerEntry>()

        override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

        override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

        override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
            Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
            if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
            val item = items.getValue(entry.batchId)
            val newOnHand = item.onHand + entry.onHandDelta
            if (newOnHand < 0.0) {
                return Outcome.failure(
                    CulinaryError.Domain(
                        DomainDetail.InsufficientStock(entry.productId.value, -entry.onHandDelta, item.available),
                        ErrorCode.INV_003,
                    ),
                )
            }
            items[entry.batchId] = item.copy(onHand = newOnHand)
            ledger += entry.copy(onHandAfter = newOnHand)
            return Outcome.success(true)
        }

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())

        override suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(products)

        override suspend fun findProduct(id: ProductId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.id == id })

        override suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(emptyList())

        override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> = Outcome.success(null)

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> =
            Outcome.failure(CulinaryError.Unexpected("not used"))

        override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
            Outcome.success(ledger.filter { it.batchId == batchId })
    }

    private class FakeCalculationRecordRepository : CalculationRecordRepository {
        val records = mutableListOf<CalculationRecord>()

        override suspend fun saveConfirmed(record: CalculationRecord): Outcome<CalculationRecord> {
            records.firstOrNull { it.payloadHash == record.payloadHash }?.let { return Outcome.success(it) }
            records += record
            return Outcome.success(record)
        }

        override suspend fun find(id: CalculationRecordId): Outcome<CalculationRecord?> =
            Outcome.success(records.firstOrNull { it.id == id })

        override suspend fun findByRecipeVersion(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>> =
            Outcome.success(records.filter { it.recipeVersionId == recipeVersionId })

        override suspend fun findByPayloadHash(payloadHash: String): Outcome<CalculationRecord?> =
            Outcome.success(records.firstOrNull { it.payloadHash == payloadHash })
    }

    private object ImmediateRunner : TransactionRunner {
        override suspend fun <T> write(block: suspend () -> T): Outcome<T> = Outcome.success(block())
    }

    private companion object {
        val NOW: Instant = Instant.ofEpochMilli(1_760_000_000_000L)
        val FLOUR = IngredientId("flour")
        val SUGAR = IngredientId("sugar")
        val SALT = IngredientId("salt")
        val FlourId = IngredientId("flour")
        val SugarId = IngredientId("sugar")
        val ROOT_RECIPE = RecipeId("recipe-root")
        val ROOT_VERSION = RecipeVersionId("recipe-root-v1")
        val SAUCE = SauceId("sauce-base")
        val SAUCE_VERSION = SauceVersionId("sauce-base-v1")
        val gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val piece = Unit(UnitId("piece"), "piece", "个", "个", UnitDimension.COUNT, 1.0, true, false, 0)

        fun ingredient(
            id: IngredientId,
            name: String,
        ): Ingredient =
            Ingredient(
                id = id,
                name = name,
                normalizedName = name,
                defaultUnitId = UnitId("g"),
                status = EntityStatus.ACTIVE,
            )
    }
}
