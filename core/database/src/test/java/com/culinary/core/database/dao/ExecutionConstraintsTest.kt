package com.culinary.core.database.dao

import androidx.room.Room
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.CulinaryDatabaseCallback
import com.culinary.core.database.entity.BarcodeEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.InventoryBatchEntity
import com.culinary.core.database.entity.InventoryLedgerEntity
import com.culinary.core.database.entity.ProductEntity
import com.culinary.core.database.entity.StorageLocationEntity
import com.culinary.core.database.entity.SupplierEntity
import com.culinary.core.database.model.LedgerTypeDb
import com.culinary.core.database.model.StorageLocationKindDb
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

/** 商品/位置/库存：外键行为、数量约束与流水只追加。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExecutionConstraintsTest {
    private lateinit var database: CulinaryDatabase
    private lateinit var products: ProductDao
    private lateinit var barcodes: BarcodeDao
    private lateinit var suppliers: SupplierDao
    private lateinit var locations: StorageLocationDao
    private lateinit var batches: InventoryBatchDao
    private lateinit var ledgers: InventoryLedgerDao

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
        products = database.productDao()
        barcodes = database.barcodeDao()
        suppliers = database.supplierDao()
        locations = database.storageLocationDao()
        batches = database.inventoryBatchDao()
        ledgers = database.inventoryLedgerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `rejects a product with a non positive content quantity`() {
        runBlocking { insertWorld() }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { products.insert(product(id = SECOND_PRODUCT_ID, contentQty = 0.0)) }
        }
    }

    @Test
    fun `restricts deleting an ingredient that still has products`(): Unit =
        runBlocking {
            insertWorld()

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { database.ingredientDao().deleteById(INGREDIENT_ID) }
            }
        }

    @Test
    fun `clears the supplier reference when the supplier is deleted`(): Unit =
        runBlocking {
            insertWorld()

            suppliers.deleteById(SUPPLIER_ID)

            assertThat(products.findById(PRODUCT_ID)?.supplierId).isNull()
        }

    @Test
    fun `rejects a duplicate barcode code`() {
        runBlocking {
            insertWorld()
            barcodes.insert(barcode(id = BARCODE_ID, code = "6901234567890", primary = false))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                barcodes.insert(barcode(id = SECOND_BARCODE_ID, code = "6901234567890", primary = false))
            }
        }
    }

    @Test
    fun `allows only one primary barcode per product`() {
        runBlocking {
            insertWorld()
            barcodes.insert(barcode(id = BARCODE_ID, code = "111", primary = true))
        }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                barcodes.insert(barcode(id = SECOND_BARCODE_ID, code = "222", primary = true))
            }
        }
    }

    @Test
    fun `rejects a batch whose reserved quantity exceeds on hand`() {
        runBlocking { insertWorld() }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                batches.insert(batch(id = SECOND_BATCH_ID, onHand = 1.0, reserved = 2.0))
            }
        }
    }

    @Test
    fun `rejects a duplicate ledger idempotency key`() {
        runBlocking { insertWorld() }

        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                ledgers.insert(
                    ledger(id = SECOND_LEDGER_ID, key = "ADJUSTMENT:$PRODUCT_ID:$BATCH_ID:1"),
                )
            }
        }
    }

    @Test
    fun `rejects updating a ledger row`() {
        runBlocking { insertWorld() }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE inventory_ledger SET note = 'tampered' WHERE id = '$LEDGER_ID'",
            )
        }
    }

    @Test
    fun `rejects deleting a ledger row`() {
        runBlocking { insertWorld() }

        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM inventory_ledger WHERE id = '$LEDGER_ID'",
            )
        }
    }

    @Test
    fun `restricts deleting a location that has children`(): Unit =
        runBlocking {
            insertWorld()
            locations.insert(
                StorageLocationEntity(
                    id = CHILD_LOCATION_ID,
                    name = "冷藏抽屉",
                    normalizedName = "冷藏抽屉",
                    parentId = LOCATION_ID,
                    kind = StorageLocationKindDb.FRIDGE,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            )

            assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                runBlocking { locations.deleteById(LOCATION_ID) }
            }
        }

    private suspend fun insertWorld() {
        database.ingredientDao().insert(
            IngredientEntity(
                id = INGREDIENT_ID,
                name = "黄油",
                normalizedName = "黄油",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        suppliers.insert(
            SupplierEntity(
                id = SUPPLIER_ID,
                name = "供应商",
                normalizedName = "供应商",
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        locations.insert(
            StorageLocationEntity(
                id = LOCATION_ID,
                name = "冷藏区",
                normalizedName = "冷藏区",
                kind = StorageLocationKindDb.FRIDGE,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        products.insert(product())
        batches.insert(batch())
        ledgers.insert(ledger())
    }

    private fun product(
        id: String = PRODUCT_ID,
        contentQty: Double = 200.0,
    ) = ProductEntity(
        id = id,
        ingredientId = INGREDIENT_ID,
        supplierId = SUPPLIER_ID,
        name = "黄油 200g",
        normalizedName = "黄油 200g",
        contentQty = contentQty,
        contentUnitId = gramId,
        stockUnitId = gramId,
        stockToContentFactor = 1.0,
        defaultLocationId = LOCATION_ID,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun barcode(
        id: String,
        code: String,
        primary: Boolean,
    ) = BarcodeEntity(
        id = id,
        productId = PRODUCT_ID,
        code = code,
        isPrimary = primary,
        createdAt = NOW,
    )

    private fun batch(
        id: String = BATCH_ID,
        onHand: Double = 10.0,
        reserved: Double = 0.0,
    ) = InventoryBatchEntity(
        id = id,
        productId = PRODUCT_ID,
        locationId = LOCATION_ID,
        receivedAt = NOW,
        quantityOnHand = onHand,
        quantityReserved = reserved,
        stockUnitId = gramId,
        createdAt = NOW,
        updatedAt = NOW,
    )

    private fun ledger(
        id: String = LEDGER_ID,
        key: String = "ADJUSTMENT:$PRODUCT_ID:$BATCH_ID:1",
    ) = InventoryLedgerEntity(
        id = id,
        batchId = BATCH_ID,
        productId = PRODUCT_ID,
        locationId = LOCATION_ID,
        type = LedgerTypeDb.ADJUSTMENT_IN,
        onHandDelta = 10.0,
        reservedDelta = 0.0,
        stockUnitId = gramId,
        onHandAfter = 10.0,
        reservedAfter = 0.0,
        idempotencyKey = key,
        occurredAt = NOW,
        createdAt = NOW,
    )

    private companion object {
        const val NOW = 1_760_000_000_000L
        const val INGREDIENT_ID = "11111111-1111-4111-8111-111111111111"
        const val SUPPLIER_ID = "22222222-2222-4222-8222-222222222222"
        const val LOCATION_ID = "33333333-3333-4333-8333-333333333333"
        const val CHILD_LOCATION_ID = "34343434-3434-4343-8343-343434343434"
        const val PRODUCT_ID = "44444444-4444-4444-8444-444444444444"
        const val SECOND_PRODUCT_ID = "45454545-4545-4545-8545-454545454545"
        const val BARCODE_ID = "55555555-5555-4555-8555-555555555555"
        const val SECOND_BARCODE_ID = "56565656-5656-4565-8565-565656565656"
        const val BATCH_ID = "66666666-6666-4666-8666-666666666666"
        const val SECOND_BATCH_ID = "67676767-6767-4767-8767-676767676767"
        const val LEDGER_ID = "77777777-7777-4777-8777-777777777777"
        const val SECOND_LEDGER_ID = "78787878-7878-4787-8787-787878787878"
    }
}
