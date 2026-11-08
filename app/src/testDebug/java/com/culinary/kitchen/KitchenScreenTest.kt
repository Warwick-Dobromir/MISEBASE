package com.culinary.kitchen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.data.kitchen.KitchenCounts
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 厨房首页：分组、六类入口、动态数量与全局搜索面板。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class KitchenScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `renders masthead two groups six categories and dynamic counts`() {
        compose.setContent {
            KitchenUnderTest(
                state =
                    KitchenUiState(
                        counts =
                            KitchenCounts(
                                sauceCount = 12,
                                ingredientCount = 3,
                                recipeCount = 0,
                                cookingTechniqueCount = 2,
                                productionStandardCount = 0,
                                storageStandardCount = 0,
                            ),
                        isLoading = false,
                    ),
            )
        }

        compose.onNodeWithText("MISEBASE").assertIsDisplayed()
        compose.onNodeWithText("厨房").assertIsDisplayed()
        compose.onNodeWithText("CULINARY").assertIsDisplayed()

        compose.onNodeWithText("烹饪资料").assertIsDisplayed()
        compose.onNodeWithText("CULINARY REFERENCES").assertIsDisplayed()
        compose.onNodeWithText("工作标准").assertIsDisplayed()
        compose.onNodeWithText("WORKING STANDARDS").assertIsDisplayed()

        compose.onNodeWithText("酱汁").assertIsDisplayed()
        compose.onNodeWithText("SAUCES").assertIsDisplayed()
        compose.onNodeWithText("食材").assertIsDisplayed()
        compose.onNodeWithText("INGREDIENTS").assertIsDisplayed()
        compose.onNodeWithText("菜谱").assertIsDisplayed()
        compose.onNodeWithText("RECIPES").assertIsDisplayed()
        compose.onNodeWithText("烹饪技术").assertIsDisplayed()
        compose.onNodeWithText("COOKING TECHNIQUES").assertIsDisplayed()
        compose.onNodeWithText("制作标准").assertIsDisplayed()
        compose.onNodeWithText("PRODUCTION STANDARDS").assertIsDisplayed()
        compose.onNodeWithText("储存标准").assertIsDisplayed()
        compose.onNodeWithText("STORAGE STANDARDS").assertIsDisplayed()

        compose.onNodeWithText("12 项").assertIsDisplayed()
        compose.onNodeWithText("3 项").assertIsDisplayed()
        compose.onNodeWithText("2 项").assertIsDisplayed()
        compose.onAllNodesWithText("0 项").assertCountEquals(3)
    }

    @Test
    fun `search entry is the only top-right action and all categories invoke callbacks`() {
        val opened = mutableListOf<KitchenCategory>()

        compose.setContent {
            KitchenUnderTest(
                state = KitchenUiState(counts = KitchenCounts(), isLoading = false),
                onOpenCategory = { opened += it },
            )
        }

        compose.onNodeWithContentDescription("厨房全局搜索").assertIsDisplayed()
        compose.onNodeWithContentDescription("厨房模块说明").assertDoesNotExist()

        compose.onNodeWithText("酱汁").performClick()
        compose.onNodeWithText("食材").performClick()
        compose.onNodeWithText("菜谱").performClick()
        compose.onNodeWithText("烹饪技术").performClick()
        compose.onNodeWithText("制作标准").performClick()
        compose.onNodeWithText("储存标准").performClick()

        assertThat(opened)
            .containsExactlyElementsIn(KitchenCategory.entries)
            .inOrder()
    }

    @Test
    fun `global search shows typed results and opens the selected entry`() {
        var opened: KitchenArchiveEntry? = null
        val entries =
            listOf(
                searchEntry("ingredient-1", KitchenArchiveKind.INGREDIENT, "黄油", "乳制品"),
                searchEntry("sauce-1", KitchenArchiveKind.SAUCE, "黄油酱", "基础酱汁"),
            )

        compose.setContent {
            KitchenUnderTest(
                state = KitchenUiState(isLoading = false),
                archiveEntries = entries,
                onOpenSearchEntry = { opened = it },
            )
        }

        compose.onNodeWithContentDescription("厨房全局搜索").performClick()
        compose.onNodeWithText("厨房搜索").assertIsDisplayed()
        compose.onNodeWithText("输入关键词搜索酱汁、食材、菜谱、烹饪技术和工作标准。").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextInput("黄油")
        compose.onNodeWithText("乳制品").assertIsDisplayed()
        compose.onNodeWithText("黄油酱").assertIsDisplayed()
        compose.onNodeWithText("乳制品").performClick()

        assertThat(opened?.id).isEqualTo("ingredient-1")
    }

    @Test
    fun `global search covers all six archive kinds`() {
        val entries =
            KitchenArchiveKind.entries.map { kind ->
                searchEntry(
                    id = kind.name.lowercase(),
                    kind = kind,
                    name = "共同${kind.name}",
                    category = "共同分类",
                )
            }
        var opened: KitchenArchiveEntry? = null

        compose.setContent {
            KitchenUnderTest(
                state = KitchenUiState(isLoading = false),
                archiveEntries = entries,
                onOpenSearchEntry = { opened = it },
            )
        }

        compose.onNodeWithContentDescription("厨房全局搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("共同")
        compose.onNodeWithText("6 条结果").assertIsDisplayed()
        KitchenArchiveKind.entries.forEach { kind ->
            compose.onNodeWithTag("search-result-$kind").assertIsDisplayed()
        }
        compose.onNodeWithTag("search-result-${KitchenArchiveKind.TECHNIQUE}").performClick()

        assertThat(opened?.kind).isEqualTo(KitchenArchiveKind.TECHNIQUE)
    }

    @Test
    fun `global search shows empty result and closes by close action`() {
        compose.setContent {
            KitchenUnderTest(
                state = KitchenUiState(isLoading = false),
                archiveEntries = listOf(searchEntry("ingredient-1", KitchenArchiveKind.INGREDIENT, "黄油", "乳制品")),
            )
        }

        compose.onNodeWithContentDescription("厨房全局搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("不存在")
        compose.onNodeWithText("没有匹配的厨房档案。").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭搜索").performClick()
        compose.onNodeWithText("烹饪资料").assertIsDisplayed()
    }

    @Test
    fun `loading state uses unavailable marker instead of fabricated zero`() {
        compose.setContent {
            KitchenUnderTest(state = KitchenUiState(isLoading = true))
        }

        compose.onAllNodesWithText("—").assertCountEquals(6)
        compose.onAllNodesWithText("0 项").assertCountEquals(0)
    }

    @Test
    fun `count source keeps categories isolated`() {
        val counts =
            KitchenCounts(
                sauceCount = 12,
                ingredientCount = 3,
                recipeCount = 7,
                cookingTechniqueCount = 4,
                productionStandardCount = 5,
                storageStandardCount = 6,
            )

        assertThat(counts.countFor(KitchenCategory.SAUCES)).isEqualTo(12)
        assertThat(counts.countFor(KitchenCategory.INGREDIENTS)).isEqualTo(3)
        assertThat(counts.countFor(KitchenCategory.RECIPES)).isEqualTo(7)
        assertThat(counts.countFor(KitchenCategory.TECHNIQUES)).isEqualTo(4)
        assertThat(counts.countFor(KitchenCategory.PRODUCTION_STANDARDS)).isEqualTo(5)
        assertThat(counts.countFor(KitchenCategory.STORAGE_STANDARDS)).isEqualTo(6)
    }
}

@Composable
private fun KitchenUnderTest(
    state: KitchenUiState,
    archiveEntries: List<KitchenArchiveEntry> = emptyList(),
    onOpenCategory: (KitchenCategory) -> Unit = {},
    onOpenSearchEntry: (KitchenArchiveEntry) -> Unit = {},
) {
    CulinaryTheme {
        KitchenScreen(
            state = state,
            archiveEntries = archiveEntries,
            onOpenCategory = onOpenCategory,
            onOpenSearchEntry = onOpenSearchEntry,
        )
    }
}

private fun searchEntry(
    id: String,
    kind: KitchenArchiveKind,
    name: String,
    category: String,
): KitchenArchiveEntry =
    KitchenArchiveEntry(
        id = id,
        kind = kind,
        name = name,
        subtitle = null,
        category = category,
        searchText = "$name $category",
    )
