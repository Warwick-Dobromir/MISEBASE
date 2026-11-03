package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.GoodsReceiptEntity
import com.culinary.core.database.entity.GoodsReceiptItemEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.MaterialRequirementEntity
import com.culinary.core.database.entity.ProcurementRequirementEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.entity.ProductionPlanEntity
import com.culinary.core.database.entity.ProductionPlanSnapshotEntity
import com.culinary.core.database.entity.PurchaseOrderEntity
import com.culinary.core.database.entity.PurchaseOrderItemEntity
import com.culinary.core.database.entity.StorageLocationEntity
import com.culinary.core.database.entity.SupplierEntity
import com.culinary.core.database.model.ConversionStatusDb
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

/** 采购/收货：唯一性、数量约束与 CASCADE/RESTRICT 行为。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcurementConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var orders: PurchaseOrderDao
    private lateinit var orderItems: PurchaseOrderItemDao
    private lateinit var receipts: GoodsReceiptDao
    private lateinit var receiptItems: GoodsReceiptItemDao
    private lateinit var procurement: ProcurementRequirementDao

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
        orders = database.purchaseOrderDao()
        orderItems = database.purchaseOrderItemDao()
        receipts = database.goodsReceiptDao()
        receiptItems = database.goodsReceiptItemDao()
        procurement = database.procurementRequirementDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rejects a duplicate purchase order code`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { orders.insert(order(id = SECOND_ORDER_ID)) }
        }
    }

    @Test
    fun `rejects a purchase order item whose cancelled quantity exceeds ordered`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                orderItems.insert(orderItem(id = ITEM_ID, ordered = 5.0, cancelled = 6.0))
            }
        }
    }

    @Test
    fun `rejects a purchase order item with a non positive ordered quantity`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { orderItems.insert(orderItem(id = ITEM_ID, ordered = 0.0)) }
        }
    }

    @Test
    fun `rejects a duplicate receipt number`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
            receipts.insert(receipt(id = RECEIPT_ID))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { receipts.insert(receipt(id = SECOND_RECEIPT_ID)) }
        }
    }

    @Test
    fun `rejects a receipt item with a non positive quantity`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
            receipts.insert(receipt(id = RECEIPT_ID))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { receiptItems.insert(receiptItem(quantity = 0.0)) }
        }
    }

    @Test
    fun `rejects a procurement requirement with a non positive requested quantity`() {
        runBlocking { insertPlanWorld() }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                procurement.insert(procurementRequirement(requestedQty = 0.0))
            }
        }
    }

    @Test
    fun `cascades purchase order items when the order is deleted`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
        }

        runBlocking {
            orders.deleteById(ORDER_ID)
            assertThat(orderItems.count()).isEqualTo(0)
        }
    }

    @Test
    fun `restricts deleting an order that still has receipts`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
            receipts.insert(receipt(id = RECEIPT_ID))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { orders.deleteById(ORDER_ID) }
        }
    }

    @Test
    fun `cascades receipt items when the receipt is deleted`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
            receipts.insert(receipt(id = RECEIPT_ID))
            receiptItems.insert(receiptItem())
        }

        runBlocking {
            receipts.deleteById(RECEIPT_ID)
            assertThat(receiptItems.count()).isEqualTo(0)
        }
    }

    @Test
    fun `restricts deleting an order item that a receipt references`() {
        runBlocking {
            insertCatalogWorld()
            orders.insert(order())
            orderItems.insert(orderItem())
            receipts.insert(receipt(id = RECEIPT_ID))
            receiptItems.insert(receiptItem())
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { orderItems.deleteById(ITEM_ID) }
        }
    }

    private suspend fun insertCatalogWorld() {
        database.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID,
                name = "黄油",
                normalizedName = "黄油",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.supplierDao().insert(
            SupplierEntity(
                id = SUPPLIER_ID,
                name = "供应商",
                normalizedName = "供应商",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
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
                supplierId = SUPPLIER_ID,
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
    }

    private suspend fun insertPlanWorld() {
        insertCatalogWorld()
        database.productionPlanDao().insert(
            ProductionPlanEntity(
                id = PLAN_ID,
                title = "周末备餐",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        database.planSnapshotDao().insert(
            ProductionPlanSnapshotEntity(
                id = SNAPSHOT_ID,
                planId = PLAN_ID,
                revisionNo = 1,
                engineVersion = "1.0.0",
                payloadJson = """{"items":[]}""",
                payloadHash = "0123456789abcdef",
                requirementCount = 1,
                createdAt = NOW,
            ),
        )
        database.materialRequirementDao().insert(
            MaterialRequirementEntity(
                id = REQUIREMENT_ID,
                snapshotId = SNAPSHOT_ID,
                ingredientId = INGREDIENT_ID,
                requiredQty = 10.0,
                unitId = gramId,
                dimension = UnitDimensionDb.MASS,
                aggregationKey = "k1",
                conversionStatus = ConversionStatusDb.OK,
                createdAt = NOW,
            ),
        )
    }

    private fun order(id: String = ORDER_ID) =
        PurchaseOrderEntity(
            id = id,
            code = "PO-0001",
            supplierId = SUPPLIER_ID,
            sourcePlanId = null,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private fun orderItem(
        id: String = ITEM_ID,
        ordered: Double = 5.0,
        cancelled: Double = 0.0,
    ) = PurchaseOrderItemEntity(
        id = id,
        purchaseOrderId = ORDER_ID,
        productId = PRODUCT_ID,
        orderedQty = ordered,
        unitId = gramId,
        cancelledQty = cancelled,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun receipt(id: String) =
        GoodsReceiptEntity(
            id = id,
            purchaseOrderId = ORDER_ID,
            receiptNo = "GR-0001",
            receivedAt = NOW,
            locationId = LOCATION_ID,
            createdAt = NOW,
        )

    private fun receiptItem(quantity: Double = 5.0) =
        GoodsReceiptItemEntity(
            id = RECEIPT_ITEM_ID,
            goodsReceiptId = RECEIPT_ID,
            purchaseOrderItemId = ITEM_ID,
            productId = PRODUCT_ID,
            quantity = quantity,
            stockUnitId = gramId,
        )

    private fun procurementRequirement(requestedQty: Double) =
        ProcurementRequirementEntity(
            id = PROCUREMENT_ID,
            planId = PLAN_ID,
            snapshotId = SNAPSHOT_ID,
            requirementId = REQUIREMENT_ID,
            ingredientId = INGREDIENT_ID,
            productId = PRODUCT_ID,
            requestedQty = requestedQty,
            unitId = gramId,
            createdAt = NOW,
            updatedAt = NOW,
        )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val INGREDIENT_ID = "11111111-1111-4111-8111-111111111111"
        const val SUPPLIER_ID = "22222222-2222-4222-8222-222222222222"
        const val LOCATION_ID = "33333333-3333-4333-8333-333333333333"
        const val PRODUCT_ID = "44444444-4444-4444-8444-444444444444"
        const val PLAN_ID = "55555555-5555-4555-8555-555555555555"
        const val SNAPSHOT_ID = "56565656-5656-4565-8565-565656565656"
        const val REQUIREMENT_ID = "57575757-5757-4575-8575-575757575757"
        const val ORDER_ID = "66666666-6666-4666-8666-666666666666"
        const val SECOND_ORDER_ID = "67676767-6767-4767-8767-676767676767"
        const val ITEM_ID = "77777777-7777-4777-8777-777777777777"
        const val RECEIPT_ID = "88888888-8888-4888-8888-888888888888"
        const val SECOND_RECEIPT_ID = "89898989-8989-4989-8989-898989898989"
        const val RECEIPT_ITEM_ID = "99999999-9999-4999-8999-999999999999"
        const val PROCUREMENT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    }
}
