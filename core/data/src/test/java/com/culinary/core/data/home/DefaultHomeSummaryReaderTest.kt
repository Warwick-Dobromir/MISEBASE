package com.culinary.core.data.home

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.data.fake.InMemoryInventoryRepository
import com.culinary.core.data.fake.InMemoryRecipeRepository
import com.culinary.core.data.fake.InMemorySauceRepository
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** 首页汇总只读端口：真实计数、空数据与失败隔离。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHomeSummaryReaderTest {
    private val gram =
        Unit(
            id = UnitId("g"),
            code = "g",
            name = "克",
            symbol = "g",
            dimension = UnitDimension.MASS,
            baseFactor = 1.0,
            isBase = true,
            isApproximate = false,
            displayPrecision = 0,
        )
    private val ingredient =
        Ingredient(
            id = IngredientId("ingredient-1"),
            name = "盐",
            normalizedName = "盐",
            defaultUnitId = UnitId("g"),
            status = EntityStatus.ACTIVE,
        )
    private val product =
        Product(
            id = ProductId("product-1"),
            ingredientId = ingredient.id,
            name = "盐",
            stockUnit = gram,
            contentQty = 1.0,
            contentUnit = gram,
            stockToContentFactor = 1.0,
        )
    private val location = StorageLocation(id = LocationId("location-1"), name = "默认储藏")
    private val secondProduct =
        product.copy(
            id = ProductId("product-2"),
            name = "糖",
        )
    private val dispatchers =
        object : DispatcherProvider {
            private val dispatcher = UnconfinedTestDispatcher()
            override val main = dispatcher
            override val default = dispatcher
            override val io = dispatcher
        }

    @Test
    fun `inventory summary counts products batches and zero availability`() =
        runTest {
            val inventory =
                inventoryRepository(
                    listOf(
                        item("batch-1", product.id, onHand = 2.0),
                        item("batch-2", product.id, onHand = 0.0),
                        item("batch-3", ProductId("product-2"), onHand = 0.0),
                    ),
                )
            val reader = reader(inventory = inventory)

            val result = reader.inventorySummary()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val summary = (result as Outcome.Success).value
            assertThat(summary.productCount).isEqualTo(2)
            assertThat(summary.batchCount).isEqualTo(3)
            assertThat(summary.attentionCount).isEqualTo(1)
        }

    @Test
    fun `inventory summary reports empty when there is no active batch`() =
        runTest {
            val reader = reader(inventory = inventoryRepository(emptyList()))

            val summary = (reader.inventorySummary() as Outcome.Success).value

            assertThat(summary.productCount).isEqualTo(0)
            assertThat(summary.batchCount).isEqualTo(0)
            assertThat(summary.attentionCount).isEqualTo(0)
            assertThat(summary.isEmpty).isTrue()
        }

    @Test
    fun `knowledge summary only counts active archives`() =
        runTest {
            val sauces = InMemorySauceRepository().apply { addSauce(sauce("sauce-1", "照烧汁")) }
            val recipes = InMemoryRecipeRepository().apply { addRecipe(recipe("recipe-1", "宫保鸡丁")) }
            val archived =
                ingredient.copy(
                    id = IngredientId("archived"),
                    name = "旧食材",
                    normalizedName = "旧食材",
                    status = EntityStatus.ARCHIVED,
                )
            val ingredients = FakeIngredientRepository(listOf(ingredient, archived))
            val reader = reader(ingredients = ingredients, sauces = sauces, recipes = recipes)

            val summary = (reader.knowledgeSummary() as Outcome.Success).value

            assertThat(summary.sauceCount).isEqualTo(1)
            assertThat(summary.ingredientCount).isEqualTo(1)
            assertThat(summary.recipeCount).isEqualTo(1)
            assertThat(summary.totalCount).isEqualTo(3)
        }

    @Test
    fun `inventory failure is returned without touching knowledge`() =
        runTest {
            val failing =
                object : InventoryRepository {
                    override suspend fun listItems(): Outcome<List<InventoryItem>> =
                        Outcome.failure(CulinaryError.Data(message = "db down", code = ErrorCode.DB_001))

                    override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(null)

                    override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
                        Outcome.success(null)

                    override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> =
                        Outcome.success(false)

                    override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(emptyList())
                }
            val reader = reader(inventory = failing)

            val result = reader.inventorySummary()

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.DB_001)
        }

    @Test
    fun `knowledge failure is mapped to a culinary error`() =
        runTest {
            val failingIngredients =
                object : IngredientRepository {
                    override fun observeActive(): Flow<List<Ingredient>> =
                        flow { throw IllegalStateException("flow failed") }

                    override suspend fun get(id: IngredientId): Outcome<Ingredient> =
                        Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

                    override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> =
                        Outcome.success(ingredient.id)

                    override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(Unit)
                }
            val reader = reader(ingredients = failingIngredients)

            val result = reader.knowledgeSummary()

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.UNKNOWN_001)
        }

    private fun reader(
        inventory: InventoryRepository = inventoryRepository(emptyList()),
        ingredients: IngredientRepository = FakeIngredientRepository(listOf(ingredient)),
        sauces: InMemorySauceRepository = InMemorySauceRepository(),
        recipes: InMemoryRecipeRepository = InMemoryRecipeRepository(),
    ): DefaultHomeSummaryReader =
        DefaultHomeSummaryReader(
            inventoryOverview = ListInventoryOverviewUseCase(inventory, ingredients),
            listIngredients = ListIngredientsUseCase(ingredients),
            listSauces = ListSaucesUseCase(sauces),
            listRecipes = ListRecipesUseCase(recipes),
            dispatchers = dispatchers,
        )

    private fun inventoryRepository(items: List<InventoryItem>): InMemoryInventoryRepository =
        InMemoryInventoryRepository(items).apply {
            products += product
            products += secondProduct
            locations += location
        }

    private fun item(
        id: String,
        productId: ProductId,
        onHand: Double,
    ): InventoryItem =
        InventoryItem(
            batchId = BatchId(id),
            productId = productId,
            locationId = location.id,
            stockUnit = gram,
            onHand = onHand,
        )

    private fun sauce(
        id: String,
        name: String,
    ): Sauce = Sauce(id = SauceId(id), name = name, normalizedName = name, status = EntityStatus.ACTIVE)

    private fun recipe(
        id: String,
        name: String,
    ): Recipe =
        Recipe(
            id = RecipeId(id),
            name = name,
            normalizedName = name,
            category = RecipeCategory.DISH,
            status = EntityStatus.ACTIVE,
        )
}

/** 只读 fake：观察列表时按 ACTIVE 过滤，与正式 Repository 语义一致。 */
private class FakeIngredientRepository(
    private val items: List<Ingredient>,
) : IngredientRepository {
    override fun observeActive(): Flow<List<Ingredient>> = flowOf(items.filter { it.status == EntityStatus.ACTIVE })

    override suspend fun get(id: IngredientId): Outcome<Ingredient> =
        items.firstOrNull { it.id == id }?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound("ingredient", id.value))

    override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> = Outcome.success(ingredient.id)

    override suspend fun archive(id: IngredientId): Outcome<kotlin.Unit> = Outcome.success(Unit)
}
