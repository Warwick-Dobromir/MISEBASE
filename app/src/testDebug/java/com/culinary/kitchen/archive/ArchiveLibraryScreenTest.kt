package com.culinary.kitchen.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 统一资料库页：标题、总数、搜索、分类筛选、新增/详情回调。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ArchiveLibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val entries =
        listOf(
            entry("s1", KitchenArchiveKind.SAUCE, "基础高汤酱", "基础酱汁", "高汤熬制"),
            entry("s2", KitchenArchiveKind.SAUCE, "香菇酱", "基础酱汁", "香菇风味"),
            entry("s3", KitchenArchiveKind.SAUCE, "烟熏酱", "衍生酱汁", "烟熏处理"),
            entry("i1", KitchenArchiveKind.INGREDIENT, "黄油", "乳制品", "发酵黄油"),
        )

    @Test
    fun `renders sauce library header count categories and paging footer`() {
        show(KitchenArchiveSection.SAUCES)

        compose.onNodeWithText("酱汁库").assertIsDisplayed()
        compose.onNodeWithText("SAUCE COLLECTION").assertIsDisplayed()
        compose.onNodeWithText("共 3 条档案").assertIsDisplayed()
        compose.onNodeWithText("显示 3 / 3 条记录").assertIsDisplayed()
        compose.onNodeWithText("基础高汤酱").assertIsDisplayed()
        compose.onNodeWithText("烟熏酱").assertIsDisplayed()
        compose.onNodeWithText("分类").assertIsDisplayed()
        compose.onNodeWithText("＋ 新建酱汁").assertIsDisplayed()
        compose.onNodeWithText("全部").assertIsDisplayed()
    }

    @Test
    fun `category filter and local search only affect the current library`() {
        show(KitchenArchiveSection.SAUCES)

        compose.onNodeWithTag("archive-filter-基础酱汁").performClick()
        compose.onNodeWithText("显示 2 / 3 条记录").assertIsDisplayed()
        compose.onNodeWithText("烟熏酱").assertDoesNotExist()

        compose.onNodeWithTag("archive-filter-all").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("香菇")
        compose.onNodeWithText("香菇酱").assertIsDisplayed()
        compose.onNodeWithText("基础高汤酱").assertDoesNotExist()
        compose.onNodeWithText("显示 1 / 3 条记录").assertIsDisplayed()
    }

    @Test
    fun `add and entry row invoke callbacks`() {
        var added = false
        var opened: KitchenArchiveEntry? = null

        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = KitchenArchiveSection.SAUCES,
                    state =
                        ArchiveLibraryUiState(
                            entries = entries,
                            isLoading = false,
                        ),
                    onAdd = { added = true },
                    onOpenEntry = { opened = it },
                )
            }
        }

        compose.onNodeWithTag("archive-add").performClick()
        compose.onNodeWithTag("archive-entry-s1").performClick()

        assertThat(added).isTrue()
        assertThat(opened?.id).isEqualTo("s1")
    }

    @Test
    fun `recipe library keeps all business category filters even when empty`() {
        show(KitchenArchiveSection.RECIPES, entries = emptyList())

        compose.onNodeWithText("全部").assertIsDisplayed()
        compose.onNodeWithText("汤底 / 高汤").assertIsDisplayed()
        compose.onNodeWithText("预制件").assertIsDisplayed()
        compose.onNodeWithText("组件").assertIsDisplayed()
        compose.onNodeWithText("菜品").assertIsDisplayed()
        compose.onNodeWithText("其他").assertIsDisplayed()
    }

    @Test
    fun `empty library shows explicit empty state and zero footer`() {
        show(KitchenArchiveSection.TECHNIQUES, entries = emptyList())

        compose.onNodeWithText("烹饪技术").assertIsDisplayed()
        compose.onNodeWithText("COOKING TECHNIQUES").assertIsDisplayed()
        compose.onNodeWithText("共 0 条档案").assertIsDisplayed()
        compose.onNodeWithText("还没有档案").assertIsDisplayed()
        compose.onNodeWithText("显示 0 / 0 条记录").assertIsDisplayed()
    }

    private fun show(
        section: KitchenArchiveSection,
        entries: List<KitchenArchiveEntry> = this.entries,
    ) {
        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = section,
                    state = ArchiveLibraryUiState(entries = entries, isLoading = false),
                    onAdd = {},
                    onOpenEntry = {},
                )
            }
        }
    }

    private fun entry(
        id: String,
        kind: KitchenArchiveKind,
        name: String,
        category: String,
        description: String,
    ): KitchenArchiveEntry =
        KitchenArchiveEntry(
            id = id,
            kind = kind,
            name = name,
            subtitle = description,
            category = category,
            searchText = "$name $category $description",
        )
}
