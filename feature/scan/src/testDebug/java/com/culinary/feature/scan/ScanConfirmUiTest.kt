package com.culinary.feature.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.barcode.ScanCapture
import com.culinary.core.domain.barcode.ScanSource
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.UnitId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 导入确认页 Compose UI：识别结果、匹配列表、幂等提示、导入结果与缺参兜底。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanConfirmUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val capture = ScanCapture(rawValue = "6901234567892", format = "EAN13", source = ScanSource.CAMERA)

    private fun ingredient(): Ingredient =
        Ingredient(
            id = IngredientId("ingredient-1"),
            name = "低筋面粉",
            normalizedName = "低筋面粉",
            defaultUnitId = UnitId("g"),
            status = EntityStatus.ACTIVE,
        )

    @Test
    fun `shows the identification result and import target`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state =
                        ScanConfirmUiState(
                            capture = capture,
                            ingredients = listOf(ingredient()),
                            selectedIngredientId = "ingredient-1",
                            loading = false,
                        ),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("导入确认").assertIsDisplayed()
        compose.onNodeWithText("6901234567892").assertIsDisplayed()
        compose.onNodeWithText("EAN13").assertIsDisplayed()
        compose.onNodeWithText("相机识别").assertIsDisplayed()
        compose.onNodeWithText("低筋面粉").assertIsDisplayed()
        compose.onNodeWithText("确认导入").assertIsEnabled()
    }

    @Test
    fun `duplicate scan warns and offers the import again action`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state =
                        ScanConfirmUiState(
                            capture = capture,
                            ingredients = listOf(ingredient()),
                            previous =
                                com.culinary.core.domain.scan.ScanPreviousImport(
                                    entry = previousEntry(),
                                    item = null,
                                ),
                            isDuplicate = true,
                            loading = false,
                        ),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("该商品此前已导入").assertIsDisplayed()
        compose.onNodeWithText("再次入库").assertExists().assertIsEnabled()
        compose.onNodeWithText("确认导入").assertDoesNotExist()
    }

    @Test
    fun `failure shows the error and a retry action`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state =
                        ScanConfirmUiState(
                            capture = capture,
                            ingredients = listOf(ingredient()),
                            selectedIngredientId = "ingredient-1",
                            lastError = "导入失败，请重试。（CUL-UNKNOWN-001）",
                            currentOperationId = "op-1",
                            loading = false,
                        ),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("导入失败，请重试。（CUL-UNKNOWN-001）").assertIsDisplayed()
        compose.onAllNodesWithText("重试").assertCountEquals(2)
    }

    @Test
    fun `shows a new ingredient field when creating`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state =
                        ScanConfirmUiState(
                            capture = capture,
                            createNew = true,
                            newIngredientName = "海盐",
                            loading = false,
                        ),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("新食材名称").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("确认导入").assertIsEnabled()
    }

    @Test
    fun `shows the import result with replay note`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state =
                        ScanConfirmUiState(
                            capture = capture,
                            loading = false,
                            result =
                                ScanImportOutcome(
                                    ingredientName = "低筋面粉",
                                    amount = 2.0,
                                    unitCode = "g",
                                    batchCode = "B-001",
                                    replayed = true,
                                ),
                        ),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("已入库").assertExists()
        compose.onNodeWithText("已入库 低筋面粉 2 g").assertExists()
        compose.onNodeWithText("同一次确认已写入过，本次未重复入库。").assertExists()
        compose.onNodeWithText("批次 B-001").assertExists()
    }

    @Test
    fun `shows an empty state when the capture is missing`() {
        compose.setContent {
            CulinaryTheme {
                ScanConfirmScreen(
                    state = ScanConfirmUiState(capture = null, loading = false),
                    onSelectIngredient = {},
                    onCreateNewChange = {},
                    onNewIngredientNameChange = {},
                    onQuantityChange = {},
                    onConfirm = {},
                    onRetry = {},
                    onBackToScan = {},
                )
            }
        }

        compose.onNodeWithText("无法读取扫码结果").assertIsDisplayed()
        compose.onNodeWithText("返回扫码").assertIsDisplayed()
    }

    private fun previousEntry(): com.culinary.core.domain.model.InventoryLedgerEntry =
        com.culinary.core.domain.model.InventoryLedgerEntry(
            id =
                com.culinary.core.domain.model
                    .LedgerEntryId("ledger-1"),
            batchId =
                com.culinary.core.domain.model
                    .BatchId("batch-1"),
            productId =
                com.culinary.core.domain.model
                    .ProductId("product-1"),
            locationId =
                com.culinary.core.domain.model
                    .LocationId("loc-1"),
            type = com.culinary.core.domain.model.LedgerEntryType.PURCHASE_RECEIPT,
            stockUnit =
                com.culinary.core.domain.model.Unit(
                    UnitId("g"),
                    "g",
                    "克",
                    "g",
                    com.culinary.core.domain.model.UnitDimension.MASS,
                    1.0,
                    true,
                    false,
                    1,
                ),
            onHandDelta = 1.0,
            reservedDelta = 0.0,
            onHandAfter = 1.0,
            reservedAfter = 0.0,
            idempotencyKey = "scan-import:6901234567892",
            occurredAt = java.time.Instant.ofEpochMilli(1_760_000_000_000L),
        )
}
