package com.culinary.kitchen.archive

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
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
    fun `library root can create its first subcategory without creating a root category editor`() {
        var createdParent: String? = "not-called"
        var createdName = ""
        show(
            categories = emptyList(),
            onCreateSubcategory = { parentId, name ->
                createdParent = parentId
                createdName = name
            },
        )

        compose.onNodeWithText("＋ 新建子库").performClick()
        compose.onNodeWithText("所属资料库：酱汁库").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("基础酱汁")
        compose.onNodeWithText("创建").performClick()

        assertThat(createdParent).isNull()
        assertThat(createdName).isEqualTo("基础酱汁")
    }

    @Test
    fun `ingredient library root can create its first subcategory`() {
        var createdParent: String? = "not-called"
        show(
            section = KitchenArchiveSection.INGREDIENTS,
            categories = emptyList(),
            onCreateSubcategory = { parentId, _ -> createdParent = parentId },
        )

        compose.onNodeWithText("＋ 新建子库").performClick()
        compose.onNodeWithText("所属资料库：食材库").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("乳制品与蛋类")
        compose.onNodeWithText("创建").performClick()

        assertThat(createdParent).isNull()
    }

    @Test
    fun `recipe library root can create its first subcategory`() {
        var createdParent: String? = "not-called"
        show(
            section = KitchenArchiveSection.RECIPES,
            categories = emptyList(),
            onCreateSubcategory = { parentId, _ -> createdParent = parentId },
        )

        compose.onNodeWithText("＋ 新建子库").performClick()
        compose.onNodeWithText("所属资料库：菜谱库").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("主菜")
        compose.onNodeWithText("创建").performClick()

        assertThat(createdParent).isNull()
    }

    @Test
    fun `nested subcategory can create another level under the selected parent`() {
        var createdParent: String? = null
        var createdName = ""
        show(
            onCreateSubcategory = { parentId, name ->
                createdParent = parentId
                createdName = name
            },
        )

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onNodeWithText("＋ 新建子库").performClick()
        compose.onNodeWithText("所属分类：酱汁基础").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("冷酱")
        compose.onNodeWithText("创建").performClick()

        assertThat(createdParent).isEqualTo("base")
        assertThat(createdName).isEqualTo("冷酱")
    }

    @Test
    fun `long pressing a nested category opens rename dialog`() {
        var renamedId = ""
        var renamedName = ""
        show(
            onRenameSubcategory = { id, name ->
                renamedId = id
                renamedName = name
            },
        )

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onNodeWithText("基础酱汁").performTouchInput { longClick() }
        compose.onNodeWithText("重命名子库").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("改名后的分类")
        compose.onNodeWithText("保存").performClick()

        assertThat(renamedId).isEqualTo("basic")
        assertThat(renamedName).isEqualTo("改名后的分类")
    }

    @Test
    fun `long pressing the child action switches to delete and confirms deletion`() {
        var deletedId = ""
        val testCategories =
            listOf(
                category("base", null, "酱汁基础", 0, descendant = 1, childCount = 1),
                category("empty-child", "base", "空子分类", 0),
            )
        show(
            categories = testCategories,
            onDeleteSubcategory = { deletedId = it },
        )

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onAllNodesWithText("＋ 新建子库")[1].performTouchInput { longClick() }
        compose.onNodeWithContentDescription("删除子库").assertIsDisplayed()
        compose.onNodeWithContentDescription("删除子库").performClick()
        compose.onNodeWithText("确定删除「空子分类」？此操作不可撤销。").assertIsDisplayed()
        compose.onNodeWithText("删除").performClick()

        assertThat(deletedId).isEqualTo("empty-child")
    }

    @Test
    fun `long pressing and dragging the sort handle reorders siblings`() {
        var reordered = emptyList<String>()
        val testCategories =
            listOf(
                category("base", null, "酱汁基础", 0, descendant = 2, childCount = 2),
                category("first", "base", "第一分类", 0),
                category("second", "base", "第二分类", 0),
            )
        show(
            categories = testCategories,
            onReorderSubcategories = { reordered = it },
        )

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onNodeWithContentDescription("长按并上下拖动排序").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, 120f))
            up()
        }

        assertThat(reordered).isNotEmpty()
        assertThat(reordered).containsExactly("second", "first").inOrder()
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
        section: KitchenArchiveSection = KitchenArchiveSection.SAUCES,
        categories: List<KitchenArchiveCategory> = this.categories,
        onAdd: () -> Unit = {},
        onOpenEntry: (KitchenArchiveEntry) -> Unit = {},
        onCreateSubcategory: (String?, String) -> Unit = { _, _ -> },
        onRenameSubcategory: (String, String) -> Unit = { _, _ -> },
        onDeleteSubcategory: (String) -> Unit = {},
        onReorderSubcategories: (List<String>) -> Unit = {},
    ) {
        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = section,
                    state = ArchiveLibraryUiState(entries = entries, categories = categories, isLoading = false),
                    onAdd = onAdd,
                    onOpenEntry = onOpenEntry,
                    onCreateSubcategory = onCreateSubcategory,
                    onRenameSubcategory = onRenameSubcategory,
                    onDeleteSubcategory = onDeleteSubcategory,
                    onReorderSubcategories = onReorderSubcategories,
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
