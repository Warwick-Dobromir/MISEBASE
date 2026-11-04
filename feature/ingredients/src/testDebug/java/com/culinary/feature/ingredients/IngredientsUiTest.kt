package com.culinary.feature.ingredients

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.IngredientDetail
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.culinary.core.domain.model.Unit as UnitModel

/** 食材页面 Compose UI：分类与单位、编辑器字段、换算展示。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IngredientsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `list shows category and default unit in each row`() {
        compose.setContent {
            CulinaryTheme {
                IngredientsScreen(
                    state =
                        IngredientsListUiState(
                            rows =
                                listOf(
                                    IngredientRow(
                                        id = "ingredient-1",
                                        name = "低筋面粉",
                                        category = "粉类",
                                        unitLabel = "g",
                                        isActive = true,
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onOpenIngredient = {},
                    onAddIngredient = {},
                )
            }
        }

        compose.onNodeWithText("低筋面粉").assertIsDisplayed()
        compose.onNodeWithText("粉类").assertIsDisplayed()
        compose.onNodeWithText("g").assertIsDisplayed()
    }

    @Test
    fun `editor exposes name category unit and description fields`() {
        compose.setContent {
            CulinaryTheme {
                IngredientEditorForm(
                    state =
                        IngredientEditorUiState(
                            name = "低筋面粉",
                            category = "粉类",
                            description = "蛋白质 8.5%",
                            defaultUnit = gram(),
                            units = listOf(gram()),
                            isLoading = false,
                        ),
                    onNameChange = {},
                    onCategoryChange = {},
                    onDescriptionChange = {},
                    onOpenUnitPicker = {},
                    onSave = {},
                )
            }
        }

        compose.onNodeWithText("名称").assertIsDisplayed()
        compose.onNodeWithText("分类（可选）").assertIsDisplayed()
        compose.onNodeWithText("默认单位").assertIsDisplayed()
        compose.onNodeWithText("克（g）").assertIsDisplayed()
        compose.onNodeWithText("保存").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `detail shows conversion basis and archive action`() {
        val conversion =
            IngredientConversionView(
                fromUnitId = UnitId("g"),
                toUnitId = UnitId("kg"),
                fromUnit = gram(),
                toUnit = UnitModel(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 0),
                factor = 0.001,
                note = "测试换算",
            )
        val detail =
            IngredientDetail(
                ingredient =
                    Ingredient(
                        id = IngredientId("ingredient-1"),
                        name = "低筋面粉",
                        normalizedName = "低筋面粉",
                        defaultUnitId = UnitId("g"),
                        status = EntityStatus.ACTIVE,
                        category = "粉类",
                    ),
                defaultUnit = gram(),
                conversions = listOf(conversion),
            )

        compose.setContent {
            CulinaryTheme {
                IngredientDetailContent(
                    detailState = IngredientDetailUiState(detail = detail, isLoading = false),
                    onEdit = {},
                    onArchive = {},
                )
            }
        }

        compose.onNodeWithText("测试换算").assertIsDisplayed()
        compose.onNodeWithText("归档").assertIsDisplayed()
    }

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)
}
