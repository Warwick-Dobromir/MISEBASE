package com.culinary.feature.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryBatchSummary
import com.culinary.core.domain.model.InventoryFormOptions
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.InventoryLedgerRow
import com.culinary.core.domain.model.LedgerEntryId
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.PurchaseList
import com.culinary.core.domain.model.PurchaseListLine
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.RequirementPlan
import com.culinary.core.domain.model.RequirementPreviewLine
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** 库存页面 Compose UI：总览、详情、入库表单、流水与需求计划。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `overview shows available quantity and low stock badge`() {
        compose.setContent {
            CulinaryTheme {
                InventoryOverviewScreen(
                    state =
                        InventoryOverviewUiState(
                            rows = listOf(overviewRow()),
                            isLoading = false,
                        ),
                    onOpenProduct = {},
                    onReceipt = {},
                    onOpenLedger = {},
                    onOpenPlan = {},
                    onQueryChange = {},
                    onToggleLowStock = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("低筋面粉").assertIsDisplayed()
        compose.onNodeWithText("缺货").assertIsDisplayed()
        compose.onNodeWithText("共 1 个商品").assertIsDisplayed()
        compose.onNodeWithText("需求计划").assertIsDisplayed()
    }

    @Test
    fun `detail shows batches ledger and related recipes`() {
        compose.setContent {
            CulinaryTheme {
                InventoryDetailScreen(
                    state =
                        InventoryDetailUiState(
                            detail =
                                com.culinary.core.domain.model.InventoryProductDetail(
                                    product = product(),
                                    ingredientName = "低筋面粉",
                                    batches = listOf(batch()),
                                    ledger =
                                        listOf(
                                            ledgerEntry(),
                                        ),
                                    relatedRecipeNames = listOf("测试面糊"),
                                ),
                            isLoading = false,
                        ),
                    onIssueBatch = {},
                    onAdjustBatch = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("B-001").assertIsDisplayed()
        compose.onNodeWithText("· 测试面糊").assertExists()
        compose.onNodeWithText("入库").assertIsDisplayed()
        compose.onNodeWithText("出库").assertIsDisplayed()
    }

    @Test
    fun `receipt form shows product source fields and confirmation`() {
        compose.setContent {
            CulinaryTheme {
                StockMovementScreen(
                    state =
                        StockMovementUiState(
                            options = formOptions(),
                            selectedProductId = ProductId("product-flour"),
                            unitCode = "g",
                            isLoading = false,
                        ),
                    onSourceChange = {},
                    onProductSelected = {},
                    onIngredientSelected = {},
                    onBatchSelected = {},
                    onQuantityChange = {},
                    onUnitChange = {},
                    onReasonChange = {},
                    onNoteChange = {},
                    onBatchCodeChange = {},
                    onExpiryDateChange = {},
                    onLocationSelected = {},
                    onSubmit = {},
                    onDismissError = {},
                )
            }
        }

        compose.onNodeWithText("入库").assertIsDisplayed()
        compose.onNodeWithText("低筋面粉").assertIsDisplayed()
        compose.onNodeWithText("确认入库").assertExists()
    }

    @Test
    fun `ledger shows type delta and source reference`() {
        compose.setContent {
            CulinaryTheme {
                InventoryLedgerScreen(
                    state =
                        InventoryLedgerUiState(
                            rows =
                                listOf(
                                    InventoryLedgerRow(
                                        entry = ledgerEntry(),
                                        productName = "低筋面粉",
                                        ingredientName = "低筋面粉",
                                        locationName = "冷藏",
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onQueryChange = {},
                    onTypeFilterChange = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("低筋面粉 · 低筋面粉 · 冷藏").assertIsDisplayed()
        compose.onNodeWithText("+3 g").assertIsDisplayed()
        compose.onNodeWithText("来源：stock_receipt / batch-1").assertIsDisplayed()
    }

    @Test
    fun `plan shows required gap optional detail and purchase list`() {
        compose.setContent {
            CulinaryTheme {
                RequirementPlanScreen(
                    state =
                        RequirementPlanUiState(
                            isLoading = false,
                            selectedRecipeId = "recipe-1",
                            selectedVersionId = RecipeVersionId("recipe-v1"),
                            servings = "4",
                            plan = plan(),
                            ingredientNames = mapOf("flour" to "面粉", "salt" to "盐"),
                        ),
                    onSelectRecipe = {},
                    onServingsChange = {},
                    onCalculate = {},
                    onGenerateList = {},
                    onConfirmDeduction = {},
                    onExportCsv = {},
                    onReplay = {},
                    onCloseReplay = {},
                    onDismissMessages = {},
                )
            }
        }

        compose.onNodeWithText("需求汇总与缺口").assertIsDisplayed()
        compose.onNodeWithText("可选缺口（不计入采购）").assertIsDisplayed()
        compose.onNodeWithText("导出 CSV").assertExists()
        compose.onNodeWithText("盐").assertExists()
    }

    private fun gram(): Unit = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)

    private fun product(): Product =
        Product(
            id = ProductId("product-flour"),
            ingredientId = IngredientId("flour"),
            name = "低筋面粉",
            stockUnit = gram(),
            contentQty = 1.0,
            contentUnit = gram(),
            stockToContentFactor = 1.0,
            status = EntityStatus.ACTIVE,
        )

    private fun batch(): InventoryBatchSummary =
        InventoryBatchSummary(
            item =
                InventoryItem(
                    batchId =
                        com.culinary.core.domain.model
                            .BatchId("batch-1"),
                    productId = ProductId("product-flour"),
                    locationId = LocationId("loc-1"),
                    stockUnit = gram(),
                    onHand = 5.0,
                    expiresOn = LocalDate.of(2027, 1, 1),
                    batchCode = "B-001",
                ),
            batchCode = "B-001",
            locationName = "冷藏",
        )

    private fun ledgerEntry(): InventoryLedgerEntry =
        InventoryLedgerEntry(
            id = LedgerEntryId("ledger-1"),
            batchId =
                com.culinary.core.domain.model
                    .BatchId("batch-1"),
            productId = ProductId("product-flour"),
            locationId = LocationId("loc-1"),
            type = LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit = gram(),
            onHandDelta = 3.0,
            reservedDelta = 0.0,
            onHandAfter = 3.0,
            reservedAfter = 0.0,
            idempotencyKey = "receipt-1",
            occurredAt = Instant.ofEpochMilli(1_760_000_000_000L),
            referenceType = "stock_receipt",
            referenceId = "batch-1",
        )

    private fun formOptions(): InventoryFormOptions =
        InventoryFormOptions(
            products = listOf(product()),
            locations = emptyList(),
            ingredients =
                listOf(
                    Ingredient(
                        id = IngredientId("flour"),
                        name = "低筋面粉",
                        normalizedName = "低筋面粉",
                        defaultUnitId = UnitId("g"),
                        status = EntityStatus.ACTIVE,
                    ),
                ),
            units = listOf(gram()),
        )

    private fun overviewRow(): com.culinary.core.domain.model.InventoryOverviewRow =
        com.culinary.core.domain.model.InventoryOverviewRow(
            productId = ProductId("product-flour"),
            productName = "低筋面粉",
            ingredientId = IngredientId("flour"),
            ingredientName = "低筋面粉",
            stockUnit = gram(),
            onHand = 0.0,
            reserved = 0.0,
            available = 0.0,
            batchCount = 0,
        )

    private fun plan(): RequirementPlan {
        val required =
            RequirementPreviewLine(
                ingredientId = IngredientId("flour"),
                ingredientName = "面粉",
                required = Quantity(600.0, gram()),
                available = Quantity(200.0, gram()),
                shortfall = Quantity(400.0, gram()),
                isOptional = false,
                productId = ProductId("product-flour"),
            )
        val optional =
            RequirementPreviewLine(
                ingredientId = IngredientId("salt"),
                ingredientName = "盐",
                required = Quantity(10.0, gram()),
                available = Quantity(0.0, gram()),
                shortfall = Quantity(10.0, gram()),
                isOptional = true,
            )
        val record =
            CalculationRecord(
                id = CalculationRecordId("calc-1"),
                recipeVersionId = RecipeVersionId("recipe-v1"),
                servings = Servings(4.0),
                lines =
                    listOf(
                        RequirementLine(IngredientId("flour"), Quantity(600.0, gram())),
                        RequirementLine(IngredientId("salt"), Quantity(10.0, gram()), isOptional = true),
                    ),
                engineVersion = "stage4-1",
                payloadHash = "a".repeat(64),
                status = CalculationRecordStatus.PREVIEW,
                createdAt = Instant.ofEpochMilli(1_760_000_000_000L),
            )
        return RequirementPlan(
            recipeVersionId = RecipeVersionId("recipe-v1"),
            recipeName = "测试主菜",
            baseServings = 2,
            targetServings = 4.0,
            requiredLines = listOf(required),
            optionalLines = listOf(optional),
            purchaseList =
                PurchaseList(
                    generatedAt = Instant.ofEpochMilli(1_760_000_000_000L),
                    lines =
                        listOf(
                            PurchaseListLine(
                                ingredientId = IngredientId("flour"),
                                required = Quantity(600.0, gram()),
                                available = Quantity(200.0, gram()),
                                toPurchase = Quantity(400.0, gram()),
                            ),
                        ),
                ),
            record = record,
            generatedAt = Instant.ofEpochMilli(1_760_000_000_000L),
        )
    }
}
