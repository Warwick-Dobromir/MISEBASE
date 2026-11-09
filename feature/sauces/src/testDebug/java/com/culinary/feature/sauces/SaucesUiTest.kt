package com.culinary.feature.sauces

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Quantity
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
                    relations = emptyList(),
                    onOpenRelation = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("基础酱").assertIsDisplayed()
        compose.onNodeWithText("待发布草稿").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `editor shows the nine-section archive layout and top actions`() {
        compose.setContent {
            CulinaryTheme {
                EditorUnderTest(
                    SauceEditorUiState(
                        name = "白黄油酱",
                        englishName = "Beurre Blanc",
                        family = "CLASSIC FRENCH SAUCE",
                        yieldAmount = "250",
                        yieldUnit = gram(),
                        isLoading = false,
                    ),
                )
            }
        }

        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("MIN. CAPACITY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("选择最小制作容量").performScrollTo().assertIsDisplayed()
        listOf("原料配方", "制作工艺", "适用范围", "衍生关系", "研发备注")
            .forEach { label ->
                compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            }
    }

    @Composable
    private fun EditorUnderTest(state: SauceEditorUiState) {
        SauceEditorScreen(
            state = state,
            onSaved = {},
            onCancelled = {},
            onArchive = {},
            onOpenSauce = {},
            onDismissError = {},
            onFamilyChange = {},
            onEnglishNameChange = {},
            onNameChange = {},
            onDescriptionChange = {},
            onYieldAmountChange = {},
            onYieldUnitChange = {},
            onDifficultyChange = {},
            onHeroPicked = {},
            onHeroClear = {},
            onHeroCaptionChange = {},
            onLineNameChange = { _, _ -> },
            onLineNameEnChange = { _, _ -> },
            onLineAmountChange = { _, _ -> },
            onLineUnitChange = { _, _ -> },
            onToggleOptional = {},
            onRemoveLine = {},
            onMoveLine = { _, _ -> },
            onAddIngredient = {},
            onStepTitleChange = { _, _ -> },
            onStepBodyChange = { _, _ -> },
            onStepImage = { _, _ -> },
            onClearStepImage = {},
            onRemoveStep = {},
            onMoveStep = { _, _ -> },
            onAddStep = {},
            onApplicationChange = {},
            onAddRelation = {},
            onRemoveRelation = {},
            onMoveRelation = { _, _ -> },
            onNotesChange = {},
            onAddTextNote = {},
            onNoteImage = {},
            onRemoveNoteImage = {},
        )
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
