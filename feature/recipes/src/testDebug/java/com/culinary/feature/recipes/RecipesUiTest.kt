package com.culinary.feature.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.ExpandedRecipe
import com.culinary.core.domain.model.ExpandedRecipeLine
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeComponentView
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.culinary.core.domain.model.Unit as UnitModel

/** 菜谱页面 Compose UI：列表分类、详情组成/展开、编辑器入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipesUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `list shows recipe category`() {
        compose.setContent {
            CulinaryTheme {
                RecipesScreen(
                    state =
                        RecipesListUiState(
                            rows =
                                listOf(
                                    RecipeRow(
                                        id = "recipe-1",
                                        name = "红烧肉",
                                        category = RecipeCategory.DISH,
                                        isActive = true,
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onOpenRecipe = {},
                    onAddRecipe = {},
                )
            }
        }

        compose.onNodeWithText("红烧肉").assertIsDisplayed()
        compose.onNodeWithText("菜品").assertIsDisplayed()
    }

    @Test
    fun `detail shows components optional marker and expansion`() {
        val recipe = recipe()
        val version = version(recipe)
        val detail =
            RecipeDetail(
                recipe = recipe,
                currentVersion = version,
                versions = listOf(version),
                components =
                    listOf(
                        RecipeComponentView(
                            lineNo = 1,
                            label = "低筋面粉",
                            kind = RecipeComponentKind.INGREDIENT,
                            quantity = Quantity(100.0, gram()),
                            isOptional = true,
                        ),
                    ),
                baseServings = 2,
            )
        val state =
            RecipeDetailUiState(
                detail = detail,
                isLoading = false,
                targetServings = "4",
                expanded =
                    ExpandedRecipe(
                        baseServings = 2,
                        targetServings = 4,
                        lines =
                            listOf(
                                ExpandedRecipeLine(
                                    label = "低筋面粉",
                                    quantity = Quantity(200.0, gram()),
                                    isOptional = false,
                                ),
                            ),
                    ),
            )

        compose.setContent {
            CulinaryTheme {
                RecipeDetailContent(
                    detail = detail,
                    state = state,
                    onEdit = {},
                    onPublish = {},
                    onServingsChange = {},
                    onExpand = {},
                    onOpenRelation = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("MIN. RANGE").assertIsDisplayed()
        compose.onAllNodesWithText("低筋面粉").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("可选").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("展开").onFirst().assertExists()
    }

    @Test
    fun `editor exposes servings and component actions`() {
        compose.setContent {
            CulinaryTheme {
                RecipeEditorForm(
                    state =
                        RecipeEditorUiState(
                            name = "红烧肉",
                            yieldAmount = "1000",
                            servings = "4",
                            yieldUnit = gram(),
                            units = listOf(gram()),
                            isLoading = false,
                        ),
                    onNameChange = {},
                    onOpenCategoryPicker = {},
                    onYieldAmountChange = {},
                    onOpenYieldUnit = {},
                    onServingsChange = {},
                    onAddIngredient = {},
                    onAddRecipe = {},
                    onAddSauce = {},
                    onLineAmountChange = { _, _ -> },
                    onToggleOptional = {},
                    onRemoveLine = {},
                )
            }
        }

        compose.onNodeWithText("最小制作范围", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("基准份数").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("适用范围").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("添加食材").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("添加子菜谱").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("添加酱汁").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关联档案").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("研发备注").performScrollTo().assertIsDisplayed()
    }

    private fun recipe(): Recipe =
        Recipe(
            id = RecipeId("recipe-1"),
            name = "红烧肉",
            normalizedName = "红烧肉",
            category = RecipeCategory.DISH,
            status = EntityStatus.ACTIVE,
        )

    private fun version(recipe: Recipe): RecipeVersion =
        RecipeVersion(
            id = RecipeVersionId("recipe-v1"),
            recipeId = recipe.id,
            versionNo = 1,
            status = VersionStatus.PUBLISHED,
            yield = Quantity(1000.0, gram()),
            servings = 2,
            isCurrent = true,
            components = emptyList(),
        )

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)
}
