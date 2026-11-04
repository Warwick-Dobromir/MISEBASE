package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
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
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 库存查询：按商品聚合、详情关联菜谱、流水标签与表单选项。 */
class InventoryQueriesTest {
    private val inventory = FakeInventoryRepository()
    private val ingredients = FakeIngredientRepository()
    private val recipes = FakeRecipeRepository()

    @Test
    fun `overview aggregates batches by product and keeps the nearest expiry`() =
        runBlocking<kotlin.Unit> {
            seedProduct()
            inventory.items[BatchId("b1")] = item("b1", 3.0, reserved = 1.0, expiry = LocalDate.of(2026, 2, 1))
            inventory.items[BatchId("b2")] = item("b2", 5.0, expiry = LocalDate.of(2026, 1, 1))

            val rows = (ListInventoryOverviewUseCase(inventory, ingredients)() as Outcome.Success).value

            val row = rows.single()
            assertThat(row.onHand).isWithin(1e-9).of(8.0)
            assertThat(row.reserved).isWithin(1e-9).of(1.0)
            assertThat(row.available).isWithin(1e-9).of(7.0)
            assertThat(row.batchCount).isEqualTo(2)
            assertThat(row.nearestExpiry).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(row.ingredientName).isEqualTo("低筋面粉")
            assertThat(row.locationNames).containsExactly("冷藏")
            kotlin.Unit
        }

    @Test
    fun `item detail attaches ledger and recipes that use the ingredient`() =
        runBlocking<kotlin.Unit> {
            seedProduct()
            val batchId = BatchId("b1")
            inventory.items[batchId] = item("b1", 3.0)
            inventory.ledger += ledger("l1", batchId)
            recipes.recipe = recipe()

            val detail =
                (GetInventoryItemDetailUseCase(inventory, ingredients, recipes)(batchId) as Outcome.Success).value

            assertThat(detail.ingredientName).isEqualTo("低筋面粉")
            assertThat(detail.batch.locationName).isEqualTo("冷藏")
            assertThat(detail.product?.name).isEqualTo("低筋面粉")
            assertThat(detail.ledger).hasSize(1)
            assertThat(detail.relatedRecipeNames).containsExactly("测试面糊")
            kotlin.Unit
        }

    @Test
    fun `ledger rows resolve product ingredient and location names`() =
        runBlocking<kotlin.Unit> {
            seedProduct()
            val batchId = BatchId("b1")
            inventory.items[batchId] = item("b1", 3.0)
            inventory.ledger += ledger("l1", batchId)

            val rows = (ListInventoryLedgerUseCase(inventory, ingredients)() as Outcome.Success).value

            assertThat(rows.single().productName).isEqualTo("低筋面粉")
            assertThat(rows.single().ingredientName).isEqualTo("低筋面粉")
            assertThat(rows.single().locationName).isEqualTo("冷藏")
            kotlin.Unit
        }

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
        inventory.locations += StorageLocation(LocationId("loc-1"), "冷藏")
    }

    private fun item(
        id: String,
        onHand: Double,
        reserved: Double = 0.0,
        expiry: LocalDate? = null,
    ): InventoryItem =
        InventoryItem(
            batchId = BatchId(id),
            productId = PRODUCT,
            locationId = LocationId("loc-1"),
            stockUnit = gram,
            onHand = onHand,
            reserved = reserved,
            expiresOn = expiry,
        )

    private fun ledger(
        id: String,
        batchId: BatchId,
    ): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId(id),
            batchId = batchId,
            productId = PRODUCT,
            locationId = LocationId("loc-1"),
            type = LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit = gram,
            onHandDelta = 3.0,
            reservedDelta = 0.0,
            onHandAfter = 3.0,
            reservedAfter = 0.0,
            idempotencyKey = id,
            occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
        )

    private fun recipe(): Recipe {
        val recipe = Recipe(RecipeId("recipe-1"), "测试面糊", "测试面糊", RecipeCategory.DISH, EntityStatus.ACTIVE)
        val version =
            RecipeVersion(
                id = RecipeVersionId("recipe-v1"),
                recipeId = recipe.id,
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(500.0, gram),
                components =
                    listOf(
                        RecipeComponent(
                            lineNo = 1,
                            target = ComponentTarget.Ingredient(FLOUR),
                            quantity = Quantity(100.0, gram),
                        ),
                    ),
                isCurrent = true,
            )
        recipes.versions[recipe.id] = listOf(version)
        return recipe
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

        override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> = Outcome.success(true)

        override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())

        override suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(products.toList())

        override suspend fun findProduct(id: ProductId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.id == id })

        override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
            Outcome.success(products.firstOrNull { it.ingredientId == ingredientId })

        override suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(locations.toList())

        override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> =
            Outcome.success(locations.firstOrNull { it.id == id })

        override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> =
            Outcome.success(BatchId("created"))

        override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
            Outcome.success(ledger.filter { it.batchId == batchId })
    }

    private class FakeIngredientRepository : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> = flowOf(listOf(flour))

        override suspend fun get(id: IngredientId): Outcome<Ingredient> = Outcome.success(flour)

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

        override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(kotlin.Unit)
    }

    private class FakeRecipeRepository : RecipeRepository {
        var recipe: Recipe? = null
        val versions = mutableMapOf<RecipeId, List<RecipeVersion>>()

        override fun observeActive(): Flow<List<Recipe>> = flowOf(listOfNotNull(recipe))

        override suspend fun get(id: RecipeId): Outcome<Recipe> = Outcome.success(requireNotNull(recipe))

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? =
            versions.values.flatten().firstOrNull { it.id == id }

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? =
            versions[recipeId]?.firstOrNull { it.isCurrent }

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> = versions[recipeId].orEmpty()

        override suspend fun nextVersionNo(recipeId: RecipeId): Int = 2

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> = Outcome.success(recipe.id)

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId = version.id

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> =
            Outcome.success(version.id)
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
