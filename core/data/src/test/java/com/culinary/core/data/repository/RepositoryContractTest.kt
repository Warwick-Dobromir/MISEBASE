package com.culinary.core.data.repository

import androidx.room.Room
import com.culinary.core.common.Clock
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.fake.InMemoryCalculationRecordRepository
import com.culinary.core.data.fake.InMemoryInventoryRepository
import com.culinary.core.data.fake.InMemoryRecipeRepository
import com.culinary.core.data.fake.InMemorySauceRepository
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.StorageLocationEntity
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.snapshot.CalculationRecordFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Repository 契约测试（阶段 2）。
 *
 * Room 侧用现有 Schema 的内存库验证基础实现；fake 侧验证同一组语义，
 * 二者不修改 Schema/Entity。计算记录无表，只测 fake 契约。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryContractTest {
    private lateinit var database: CulinaryDatabase
    private val clock = Clock { Instant.ofEpochMilli(NOW) }
    private var idCounter = 0
    private val ids = IdGenerator { "generated-${idCounter++}" }

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CulinaryDatabase::class.java)
                .addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `recipe repository persists a draft and reads the pinned components back`() =
        runBlocking {
            seedUnit()
            database.recipeDao().insert(recipeEntity(RECIPE_ID))
            database.ingredientDao().insert(ingredientEntity(INGREDIENT_ID))
            val repository = recipeRepository()

            repository.saveDraft(draftVersion())

            val loaded = repository.getVersion(RecipeVersionId(VERSION_ID))
            assertThat(loaded).isNotNull()
            assertThat(loaded!!.components).hasSize(1)
            val component = loaded.components.single()
            assertThat(component.quantity.amount).isWithin(1e-9).of(200.0)
            assertThat(component.quantity.unit.code).isEqualTo("tg")
            assertThat((component.target as ComponentTarget.Ingredient).id).isEqualTo(IngredientId(INGREDIENT_ID))
            assertThat(loaded.yield.amount).isWithin(1e-9).of(1000.0)
        }

    @Test
    fun `recipe repository keeps exactly one current version after publishing`() =
        runBlocking {
            seedUnit()
            database.recipeDao().insert(recipeEntity(RECIPE_ID))
            database.ingredientDao().insert(ingredientEntity(INGREDIENT_ID))
            val repository = recipeRepository()

            val first = repository.savePublished(publishedVersion(VERSION_ID, versionNo = 1))
            val second = repository.savePublished(publishedVersion(VERSION_ID_2, versionNo = 2))

            assertThat(first).isInstanceOf(Outcome.Success::class.java)
            assertThat(second).isInstanceOf(Outcome.Success::class.java)
            assertThat(database.recipeVersionDao().findCurrent(RECIPE_ID)?.id).isEqualTo(VERSION_ID_2)
            assertThat(database.recipeVersionDao().findById(VERSION_ID)?.isCurrent).isFalse()
            // 旧版本内容仍在，旧展开结果可回放。
            assertThat(database.recipeVersionDao().findById(VERSION_ID)?.yieldQty).isWithin(1e-9).of(1000.0)
        }

    @Test
    fun `inventory repository appends idempotently and rejects negative balances`() =
        runBlocking {
            seedInventory(onHand = 10.0)
            val repository = inventoryRepository()

            val first = repository.appendIfAbsent(ledgerEntry("key-1", -4.0, onHandAfter = 6.0))
            val duplicate = repository.appendIfAbsent(ledgerEntry("key-1", -4.0, onHandAfter = 6.0))
            val overdraft = repository.appendIfAbsent(ledgerEntry("key-2", -10.0, onHandAfter = 0.0))

            assertThat((first as Outcome.Success).value).isTrue()
            assertThat((duplicate as Outcome.Success).value).isFalse()
            assertThat((overdraft as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
            assertThat((repository.listItems() as Outcome.Success).value.single().onHand).isWithin(1e-9).of(6.0)
            assertThat((repository.ledger() as Outcome.Success).value).hasSize(1)
        }

    @Test
    fun `recipe fake mirrors draft and current version semantics`() =
        runBlocking {
            val fake = InMemoryRecipeRepository()
            fake.addRecipe(recipe())

            fake.saveDraft(draftVersion())
            fake.savePublished(publishedVersion(VERSION_ID, versionNo = 1))
            fake.savePublished(publishedVersion(VERSION_ID_2, versionNo = 2))

            assertThat(fake.getVersion(RecipeVersionId(VERSION_ID))?.yield?.amount).isWithin(1e-9).of(1000.0)
            assertThat(fake.currentVersionId(RECIPE_ID)).isEqualTo(RecipeVersionId(VERSION_ID_2))
        }

    @Test
    fun `inventory fake mirrors idempotent append and negative rejection`() =
        runBlocking {
            val fake = InMemoryInventoryRepository(listOf(inventoryItem(onHand = 10.0)))
            val first = fake.appendIfAbsent(ledgerEntry("key-1", -4.0, onHandAfter = 6.0))
            val duplicate = fake.appendIfAbsent(ledgerEntry("key-1", -4.0, onHandAfter = 6.0))
            val overdraft = fake.appendIfAbsent(ledgerEntry("key-2", -10.0, onHandAfter = 0.0))

            assertThat((first as Outcome.Success).value).isTrue()
            assertThat((duplicate as Outcome.Success).value).isFalse()
            assertThat((overdraft as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(fake.items.getValue(BatchId(BATCH_ID)).onHand).isWithin(1e-9).of(6.0)
        }

    @Test
    fun `calculation record fake enforces confirmed only insert once contract`() =
        runBlocking {
            val fake = InMemoryCalculationRecordRepository()
            val factory = CalculationRecordFactory()
            val preview =
                factory.preview(
                    CalculationRecordId(CALC_ID),
                    RecipeVersionId(VERSION_ID),
                    Servings(2.0),
                    listOf(RequirementLine(IngredientId(INGREDIENT_ID), Quantity(100.0, gramUnit()))),
                    createdAt = NOW_INSTANT,
                )
            val confirmed = (preview.confirm(NOW_INSTANT) as Outcome.Success).value

            val previewRejected = fake.saveConfirmed(preview)
            val saved = fake.saveConfirmed(confirmed)
            val overwrite = fake.saveConfirmed(confirmed.copy(payloadHash = "b".repeat(64)))

            assertThat((previewRejected as Outcome.Failure).error.code).isEqualTo(ErrorCode.INV_003)
            assertThat(saved).isInstanceOf(Outcome.Success::class.java)
            assertThat((overwrite as Outcome.Failure).error.code).isEqualTo(ErrorCode.CON_001)
            assertThat(fake.records).hasSize(1)
            assertThat(fake.records.single().status).isEqualTo(CalculationRecordStatus.CONFIRMED)
        }

    @Test
    fun `sauce fake stores a draft version and reports missing versions`() =
        runBlocking {
            val fake = InMemorySauceRepository()
            fake.addSauce(
                Sauce(SauceId(SAUCE_ID), "基础酱", "基础酱", EntityStatus.ACTIVE),
            )
            val version =
                SauceVersion(
                    id = SauceVersionId(SAUCE_VERSION_ID),
                    sauceId = SauceId(SAUCE_ID),
                    versionNo = 1,
                    status = VersionStatus.DRAFT,
                    yield = Quantity(500.0, gramUnit()),
                    components = emptyList(),
                )

            fake.saveDraft(version)

            assertThat((fake.getVersion(SauceVersionId(SAUCE_VERSION_ID)) as Outcome.Success).value.versionNo)
                .isEqualTo(1)
            val missing = fake.getVersion(SauceVersionId("missing"))
            assertThat((missing as Outcome.Failure).error.code).isEqualTo(ErrorCode.NF_001)
            assertThat(fake.observeActive()).isNotNull()
        }

    private fun recipeRepository(): RecipeRepository =
        RecipeRepositoryImpl(
            recipeDao = database.recipeDao(),
            versionDao = database.recipeVersionDao(),
            componentDao = database.recipeComponentDao(),
            unitDao = database.unitDao(),
            clock = clock,
            idGenerator = ids,
        )

    private fun inventoryRepository(): InventoryRepositoryImpl =
        InventoryRepositoryImpl(
            batchDao = database.inventoryBatchDao(),
            ledgerDao = database.inventoryLedgerDao(),
            unitDao = database.unitDao(),
            clock = clock,
        )

    private suspend fun seedUnit() {
        database.unitDao().insert(
            UnitEntity(
                id = UNIT_ID,
                code = "tg",
                name = "测试克",
                symbol = "tg",
                dimension = UnitDimensionDb.MASS,
                baseFactor = 1.0,
                isBase = false,
                isApproximate = false,
                displayPrecision = 1,
                isActive = true,
                sortOrder = 0,
                createdAt = NOW,
            ),
        )
    }

    private suspend fun seedInventory(onHand: Double) {
        seedUnit()
        database.ingredientDao().insert(ingredientEntity(INGREDIENT_ID))
        database.storageLocationDao().insert(
            StorageLocationEntity(
                id = LOCATION_ID,
                name = "冷藏",
                normalizedName = "冷藏",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.productDao().insert(
            ProductEntity(
                id = PRODUCT_ID,
                ingredientId = INGREDIENT_ID,
                name = "黄油",
                normalizedName = "黄油",
                contentQty = 1.0,
                contentUnitId = UNIT_ID,
                stockUnitId = UNIT_ID,
                stockToContentFactor = 1.0,
                defaultLocationId = LOCATION_ID,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.inventoryBatchDao().insert(
            InventoryBatchEntity(
                id = BATCH_ID,
                productId = PRODUCT_ID,
                locationId = LOCATION_ID,
                receivedAt = NOW,
                quantityOnHand = onHand,
                quantityReserved = 0.0,
                stockUnitId = UNIT_ID,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
    }

    private fun recipeEntity(id: String): RecipeEntity =
        RecipeEntity(
            id = id,
            name = "测试菜谱",
            normalizedName = "测试菜谱",
            category = RecipeCategoryDb.DISH,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun ingredientEntity(id: String): IngredientEntity =
        IngredientEntity(
            id = id,
            name = "黄油",
            normalizedName = "黄油",
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun recipe(): Recipe =
        Recipe(
            id = RecipeId(RECIPE_ID),
            name = "测试菜谱",
            normalizedName = "测试菜谱",
            category = RecipeCategory.DISH,
            status = EntityStatus.ACTIVE,
        )

    private fun draftVersion(): RecipeVersion =
        baseVersion(
            id = VERSION_ID,
            versionNo = 1,
            status = VersionStatus.DRAFT,
        )

    private fun publishedVersion(
        id: String,
        versionNo: Int,
    ): RecipeVersion =
        baseVersion(id = id, versionNo = versionNo, status = VersionStatus.PUBLISHED)

    private fun baseVersion(
        id: String,
        versionNo: Int,
        status: VersionStatus,
    ): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId(id),
            recipeId = RecipeId(RECIPE_ID),
            versionNo = versionNo,
            status = status,
            yield = Quantity(1000.0, gramUnit()),
            components =
                listOf(
                    RecipeComponent(
                        lineNo = 1,
                        target = ComponentTarget.Ingredient(IngredientId(INGREDIENT_ID)),
                        quantity = Quantity(200.0, gramUnit()),
                    ),
                ),
        )

    private fun inventoryItem(onHand: Double): InventoryItem =
        InventoryItem(
            batchId = BatchId(BATCH_ID),
            productId = ProductId(PRODUCT_ID),
            locationId = LocationId(LOCATION_ID),
            stockUnit = gramUnit(),
            onHand = onHand,
        )

    private fun ledgerEntry(
        key: String,
        delta: Double,
        onHandAfter: Double,
    ): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId("ledger-$key"),
            batchId = BatchId(BATCH_ID),
            productId = ProductId(PRODUCT_ID),
            locationId = LocationId(LOCATION_ID),
            type = LedgerEntryType.PRODUCTION_ISSUE,
            stockUnit = gramUnit(),
            onHandDelta = delta,
            reservedDelta = 0.0,
            onHandAfter = onHandAfter,
            reservedAfter = 0.0,
            idempotencyKey = key,
            occurredAt = NOW_INSTANT,
            referenceType = "calculation_record",
            referenceId = CALC_ID,
        )

    private fun gramUnit(): Unit = Unit(UnitId(UNIT_ID), "tg", "测试克", "tg", UnitDimension.MASS, 1.0, false, false, 1)

    private companion object {
        const val NOW = 1_760_000_000_000L
        val NOW_INSTANT: Instant = Instant.ofEpochMilli(NOW)
        const val UNIT_ID = "unit-test-g"
        const val INGREDIENT_ID = "ingredient-flour"
        const val RECIPE_ID = "recipe-1"
        const val VERSION_ID = "recipe-v1"
        const val VERSION_ID_2 = "recipe-v2"
        const val PRODUCT_ID = "product-1"
        const val LOCATION_ID = "location-1"
        const val BATCH_ID = "batch-1"
        const val CALC_ID = "calc-1"
        const val SAUCE_ID = "sauce-1"
        const val SAUCE_VERSION_ID = "sauce-v1"
    }
}
