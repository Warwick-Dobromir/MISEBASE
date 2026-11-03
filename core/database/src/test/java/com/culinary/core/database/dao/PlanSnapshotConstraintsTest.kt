package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryReservationEntity
import com.culinary.core.database.entity.MaterialRequirementEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.entity.ProductionPlanEntity
import com.culinary.core.database.entity.ProductionPlanItemEntity
import com.culinary.core.database.entity.ProductionPlanSnapshotEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.entity.StorageLocationEntity
import com.culinary.core.database.model.ConversionStatusDb
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.UnitDimensionDb
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 计划/快照/需求/预留：快照只追加、需求键唯一、ACTIVE 预留唯一。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanSnapshotConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var plans: ProductionPlanDao
    private lateinit var planItems: ProductionPlanItemDao
    private lateinit var snapshots: PlanSnapshotDao
    private lateinit var requirements: MaterialRequirementDao
    private lateinit var reservations: InventoryReservationDao

    private val gramId = "00000000-0000-4000-8000-000000000002"

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    CulinaryDatabase::class.java,
                ).addCallback(CulinaryDatabaseCallback())
                .allowMainThreadQueries()
                .build()
        plans = database.productionPlanDao()
        planItems = database.productionPlanItemDao()
        snapshots = database.planSnapshotDao()
        requirements = database.materialRequirementDao()
        reservations = database.inventoryReservationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rejects a duplicate snapshot revision for one plan`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                snapshots.insert(snapshot(id = SNAPSHOT_2, revision = 1))
            }
        }
    }

    @Test
    fun `rejects updating a snapshot`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
        }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE production_plan_snapshot SET payload_json = 'tampered' WHERE id = '$SNAPSHOT_1'",
            )
        }
    }

    @Test
    fun `rejects a plan item with a non positive quantity`() {
        runBlocking { insertPlanWorld() }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                planItems.insert(planItem(id = SECOND_ITEM_ID, quantity = 0.0))
            }
        }
    }

    @Test
    fun `rejects duplicate requirement keys within a snapshot`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            requirements.insert(requirement(id = REQUIREMENT_1, key = "k1"))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                requirements.insert(requirement(id = SECOND_REQUIREMENT_ID, key = "k1"))
            }
        }
    }

    @Test
    fun `allows only one active reservation per requirement and batch`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            requirements.insert(requirement(id = REQUIREMENT_1, key = "k1"))
            reservations.insert(reservation(id = RESERVATION_1))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { reservations.insert(reservation(id = SECOND_RESERVATION_ID)) }
        }
    }

    @Test
    fun `allows a new active reservation after the previous one is released`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            requirements.insert(requirement(id = REQUIREMENT_1, key = "k1"))
            reservations.insert(reservation(id = RESERVATION_1))
            reservations.release(RESERVATION_1, at = NOW)
        }

        runBlocking {
            reservations.insert(reservation(id = SECOND_RESERVATION_ID))
            assertThat(reservations.count()).isEqualTo(2)
        }
    }

    @Test
    fun `rejects a reservation with a non positive quantity`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            requirements.insert(requirement(id = REQUIREMENT_1, key = "k1"))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { reservations.insert(reservation(id = RESERVATION_1, quantity = 0.0)) }
        }
    }

    @Test
    fun `cascades items when a draft plan is deleted`() {
        runBlocking { insertPlanWorld() }

        runBlocking {
            plans.deleteById(PLAN_ID)
            assertThat(plans.count()).isEqualTo(0)
            assertThat(planItems.count()).isEqualTo(0)
        }
    }

    @Test
    fun `rejects deleting a draft plan that still has a snapshot`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
        }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            runBlocking { plans.deleteById(PLAN_ID) }
        }
    }

    @Test
    fun `rejects deleting a confirmed plan`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            database.openHelper.writableDatabase.execSQL(
                "UPDATE production_plan SET status = 'CONFIRMED', " +
                    "current_snapshot_id = '$SNAPSHOT_1' WHERE id = '$PLAN_ID'",
            )
        }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            runBlocking { plans.deleteById(PLAN_ID) }
        }
    }

    @Test
    fun `rejects deleting a snapshot directly`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
        }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM production_plan_snapshot WHERE id = '$SNAPSHOT_1'",
            )
        }
    }

    @Test
    fun `restricts deleting a plan that still has reservations`() {
        runBlocking {
            insertPlanWorld()
            snapshots.insert(snapshot(id = SNAPSHOT_1, revision = 1))
            requirements.insert(requirement(id = REQUIREMENT_1, key = "k1"))
            reservations.insert(reservation(id = RESERVATION_1))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { plans.deleteById(PLAN_ID) }
        }
    }

    private suspend fun insertPlanWorld() {
        insertKnowledgeWorld()
        insertExecutionWorld()
        plans.insert(
            ProductionPlanEntity(
                id = PLAN_ID,
                title = "周末备餐",
                timezone = "Asia/Hong_Kong",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        planItems.insert(planItem())
    }

    private suspend fun insertKnowledgeWorld() {
        database.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID,
                name = "黄油",
                normalizedName = "黄油",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.recipeDao().insert(
            RecipeEntity(
                id = RECIPE_ID,
                name = "基础高汤",
                normalizedName = "基础高汤",
                category = RecipeCategoryDb.STOCK,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.recipeVersionDao().insert(
            RecipeVersionEntity(
                id = RECIPE_VERSION_ID,
                recipeId = RECIPE_ID,
                versionNo = 1,
                yieldQty = 1.0,
                yieldUnitId = gramId,
                createdAt = NOW,
            ),
        )
    }

    private suspend fun insertExecutionWorld() {
        database.storageLocationDao().insert(
            StorageLocationEntity(
                id = LOCATION_ID,
                name = "冷藏区",
                normalizedName = "冷藏区",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.productDao().insert(
            ProductEntity(
                id = PRODUCT_ID,
                ingredientId = INGREDIENT_ID,
                name = "黄油 200g",
                normalizedName = "黄油 200g",
                contentQty = 200.0,
                contentUnitId = gramId,
                stockUnitId = gramId,
                stockToContentFactor = 1.0,
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
                quantityOnHand = 10.0,
                stockUnitId = gramId,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
    }

    private fun planItem(
        id: String = PLAN_ITEM_ID,
        quantity: Double = 2.0,
    ) = ProductionPlanItemEntity(
        id = id,
        planId = PLAN_ID,
        recipeId = RECIPE_ID,
        recipeVersionId = RECIPE_VERSION_ID,
        quantity = quantity,
        unitId = gramId,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun snapshot(
        id: String,
        revision: Int,
    ) = ProductionPlanSnapshotEntity(
        id = id,
        planId = PLAN_ID,
        revisionNo = revision,
        engineVersion = "1.0.0",
        payloadJson = """{"items":[]}""",
        payloadHash = "0123456789abcdef",
        requirementCount = 1,
        createdAt = NOW,
    )

    private fun requirement(
        id: String,
        key: String,
    ) = MaterialRequirementEntity(
        id = id,
        snapshotId = SNAPSHOT_1,
        ingredientId = INGREDIENT_ID,
        requiredQty = 10.0,
        unitId = gramId,
        dimension = UnitDimensionDb.MASS,
        aggregationKey = key,
        availableQtySnapshot = 4.0,
        shortageQty = 6.0,
        conversionStatus = ConversionStatusDb.OK,
        createdAt = NOW,
    )

    private fun reservation(
        id: String,
        quantity: Double = 5.0,
    ) = InventoryReservationEntity(
        id = id,
        planId = PLAN_ID,
        planItemId = PLAN_ITEM_ID,
        requirementId = REQUIREMENT_1,
        batchId = BATCH_ID,
        productId = PRODUCT_ID,
        quantity = quantity,
        stockUnitId = gramId,
        createdAt = NOW,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val INGREDIENT_ID = "11111111-1111-4111-8111-111111111111"
        const val RECIPE_ID = "22222222-2222-4222-8222-222222222222"
        const val RECIPE_VERSION_ID = "23232323-2323-4232-8232-232323232323"
        const val PLAN_ID = "33333333-3333-4333-8333-333333333333"
        const val PLAN_ITEM_ID = "34343434-3434-4343-8343-343434343434"
        const val SECOND_ITEM_ID = "35353535-3535-4353-8353-353535353535"
        const val SNAPSHOT_1 = "44444444-4444-4444-8444-444444444444"
        const val SNAPSHOT_2 = "45454545-4545-4545-8545-454545454545"
        const val REQUIREMENT_1 = "55555555-5555-4555-8555-555555555555"
        const val SECOND_REQUIREMENT_ID = "56565656-5656-4565-8565-565656565656"
        const val RESERVATION_1 = "77777777-7777-4777-8777-777777777777"
        const val SECOND_RESERVATION_ID = "78787878-7878-4787-8787-787878787878"
        const val LOCATION_ID = "88888888-8888-4888-8888-888888888888"
        const val PRODUCT_ID = "99999999-9999-4999-8999-999999999999"
        const val BATCH_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }
}
