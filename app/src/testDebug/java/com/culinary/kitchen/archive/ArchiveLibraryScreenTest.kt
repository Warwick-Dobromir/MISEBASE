package com.culinary.kitchen.archive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 统一资料库页：分类树、分区、搜索、空状态和回调。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ArchiveLibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val categories =
        listOf(
            category("base", null, "酱汁基础", 0, descendant = 2, childCount = 1),
            category("basic", "base", "基础酱汁", 2, descendant = 2),
            category("empty", null, "空分类", 0),
            category("test", null, "测试 · 基础酱汁", 0, testBatch = "UI-CATEGORY-TEST-V1"),
        )

    private val entries =
        listOf(
            entry("s1", "基础高汤酱", "basic", "base / basic"),
            entry("s2", "香菇酱", "basic", "base / basic"),
            entry("s3", "烟熏酱", "basic", "base / basic"),
        )

    @Test
    fun `renders category tree and formal category counts`() {
        show()

        compose.onNodeWithText("酱汁库").assertIsDisplayed()
        compose.onNodeWithText("SAUCE COLLECTION").assertIsDisplayed()
        compose.onNodeWithText("下级分类").assertIsDisplayed()
        compose.onNodeWithText("酱汁基础").assertIsDisplayed()
        compose.onNodeWithText("测试分类 · 仅供验收，可清理").assertIsDisplayed()
        compose.onNodeWithText("测试 · 基础酱汁").assertIsDisplayed()
    }

    @Test
    fun `drills into category and opens entry`() {
        var opened: KitchenArchiveEntry? = null
        show(onOpenEntry = { opened = it })

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onNodeWithText("基础酱汁").performClick()
        compose.onNodeWithText("基础高汤酱").performClick()

        assertThat(opened?.id).isEqualTo("s1")
    }

    @Test
    fun `empty category shows empty state and add callback`() {
        var added = false
        show(onAdd = { added = true })

        compose.onNodeWithText("空分类").performClick()
        compose.onNodeWithText("此分类暂无内容").assertIsDisplayed()
        compose.onNodeWithText("新建酱汁").performClick()
        assertThat(added).isTrue()
    }

    @Test
    fun `library search shows result with category path and is isolated to section`() {
        show()

        compose.onNodeWithContentDescription("全局搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("香菇")
        compose.onNodeWithText("base / basic").assertIsDisplayed()
        compose.onNodeWithText("基础高汤酱").assertDoesNotExist()
    }

    private fun show(
        onAdd: () -> Unit = {},
        onOpenEntry: (KitchenArchiveEntry) -> Unit = {},
    ) {
        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = KitchenArchiveSection.SAUCES,
                    state = ArchiveLibraryUiState(entries = entries, categories = categories, isLoading = false),
                    onAdd = onAdd,
                    onOpenEntry = onOpenEntry,
                )
            }
        }
    }

    private fun category(
        id: String,
        parentId: String?,
        name: String,
        direct: Int,
        descendant: Int = 0,
        childCount: Int = 0,
        testBatch: String? = null,
    ): KitchenArchiveCategory =
        KitchenArchiveCategory(
            id = id,
            library = KitchenArchiveKind.SAUCE,
            parentId = parentId,
            name = name,
            englishName = null,
            sortOrder = 0,
            testBatch = testBatch,
            directCount = direct,
            descendantCount = descendant,
            childCount = childCount,
        )

    private fun entry(
        id: String,
        name: String,
        categoryId: String,
        path: String,
    ): KitchenArchiveEntry =
        KitchenArchiveEntry(
            id = id,
            kind = KitchenArchiveKind.SAUCE,
            name = name,
            subtitle = null,
            category = "基础酱汁",
            searchText = "$name 基础酱汁",
            categoryId = categoryId,
            categoryPath = path.split(" / "),
        )
}
