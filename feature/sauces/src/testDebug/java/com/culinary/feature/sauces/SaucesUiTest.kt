package com.culinary.feature.sauces

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceDetail
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceLineView
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.model.VersionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.culinary.core.domain.model.Unit as UnitModel

/** 酱汁页面 Compose UI：列表、空状态、详情组成与发布入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaucesUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `list shows sauce name and family`() {
        compose.setContent {
            CulinaryTheme {
                SaucesScreen(
                    state =
                        SaucesListUiState(
                            items = listOf(sauce(name = "基础酱", family = "乳化酱")),
                            isLoading = false,
                        ),
                    onOpenSauce = {},
                    onAddSauce = {},
                )
            }
        }

        compose.onNodeWithText("基础酱").assertIsDisplayed()
        compose.onNodeWithText("乳化酱").assertIsDisplayed()
        compose.onNodeWithText("新建酱汁").assertIsDisplayed()
    }

    @Test
    fun `empty list offers the create action`() {
        compose.setContent {
            CulinaryTheme {
                SaucesScreen(
                    state = SaucesListUiState(isLoading = false),
                    onOpenSauce = {},
                    onAddSauce = {},
                )
            }
        }

        compose.onNodeWithText("还没有酱汁").assertIsDisplayed()
        compose.onNodeWithText("新建第一个酱汁").assertIsDisplayed()
    }

    @Test
    fun `detail shows components and a publish action for drafts`() {
        val id = SauceId("sauce-1")
        val published = version(id, "v1", versionNo = 1, status = VersionStatus.PUBLISHED, current = true)
        val draft = version(id, "v2", versionNo = 2, status = VersionStatus.DRAFT, current = false)
        val detail =
            SauceDetail(
                sauce = sauce(name = "基础酱", family = "乳化酱"),
                currentVersion = published,
                versions = listOf(draft, published),
                lines =
                    listOf(
                        SauceLineView(
                            lineNo = 1,
                            label = "黄油",
                            quantity = Quantity(120.0, gram()),
                            isOptional = true,
                        ),
                    ),
            )

        compose.setContent {
            CulinaryTheme {
                SauceDetailContent(
                    detail = detail,
                    isPublishing = false,
                    onEdit = {},
                    onPublish = {},
                    onDismissError = {},
                )
            }
        }

        compose.onNodeWithText("黄油").assertIsDisplayed()
        compose.onNodeWithText("可选").assertIsDisplayed()
        compose.onNodeWithText("发布 v2").assertIsDisplayed()
    }

    @Test
    fun `editor marks imported recipe and sauce rows as read only`() {
        compose.setContent {
            CulinaryTheme {
                SauceEditorScreen(
                    state =
                        SauceEditorUiState(
                            name = "复合酱",
                            yieldAmount = "500",
                            yieldUnit = gram(),
                            isLoading = false,
                            lines =
                                listOf(
                                    SauceLineEditor(
                                        key = "line-1",
                                        target = ComponentTarget.Ingredient(IngredientId("flour")),
                                        name = "低筋面粉",
                                        amount = "100",
                                        unit = gram(),
                                    ),
                                    SauceLineEditor(
                                        key = "line-2",
                                        target =
                                            ComponentTarget.Recipe(
                                                RecipeId("recipe-base"),
                                                RecipeVersionId("recipe-v1"),
                                            ),
                                        name = "基础面糊（子菜谱 v1）",
                                        amount = "50",
                                        unit = gram(),
                                    ),
                                ),
                        ),
                    onNameChange = {},
                    onFamilyChange = {},
                    onYieldAmountChange = {},
                    onOpenUnitPicker = {},
                    onAddIngredient = {},
                    onLineAmountChange = { _, _ -> },
                    onToggleOptional = {},
                    onRemoveLine = {},
                    onSaveDraft = {},
                    onSaveAndPublish = {},
                    onDismissError = {},
                )
            }
        }

        compose
            .onNodeWithText("子菜谱 / 子酱汁组成行来自导入数据，本阶段只读保留；保存草稿会原样写回，不会丢失。")
            .assertIsDisplayed()
        compose.onNodeWithText("只读保留").assertExists()
    }

    private fun sauce(
        name: String,
        family: String?,
    ): Sauce =
        Sauce(
            id = SauceId("sauce-1"),
            name = name,
            normalizedName = name,
            status = EntityStatus.ACTIVE,
            family = family,
        )

    private fun version(
        sauceId: SauceId,
        id: String,
        versionNo: Int,
        status: VersionStatus,
        current: Boolean,
    ): SauceVersion =
        SauceVersion(
            id = SauceVersionId(id),
            sauceId = sauceId,
            versionNo = versionNo,
            status = status,
            yield = Quantity(500.0, gram()),
            components = emptyList(),
            isCurrent = current,
        )

    private fun gram(): UnitModel = UnitModel(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 0)
}
