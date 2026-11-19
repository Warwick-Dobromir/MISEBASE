package com.culinary.kitchen.archive

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** 验证从档案目录进入详情前保存精确滚动位置。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ArchiveListScrollRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `restores saved category scroll position and saves it before opening an entry`() {
        var openedId: String? = null
        var savedPosition: Pair<Int, Int>? = null
        var savedCategory: String? = null
        val entries =
            (1..30).map { index ->
                archiveEntry(
                    id = "s$index",
                    name = "档案 " + index.toString().padStart(2, '0'),
                )
            }
        val categories =
            listOf(
                archiveCategory(
                    id = "base",
                    parentId = null,
                    name = "酱汁基础",
                    descendantCount = 30,
                    childCount = 1,
                ),
                archiveCategory(
                    id = "basic",
                    parentId = "base",
                    name = "基础酱汁",
                    descendantCount = 30,
                    childCount = 0,
                ),
            )

        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = KitchenArchiveSection.SAUCES,
                    state = ArchiveLibraryUiState(
                        entries = entries,
                        categories = categories,
                        isLoading = false,
                    ),
                    onAdd = {},
                    onOpenEntry = { openedId = it.id },
                    getScrollPosition = { categoryId, searchQuery ->
                        if (categoryId == "basic" && searchQuery == null) 2 to 17 else 0 to 0
                    },
                    onSaveScrollPosition = { categoryId, searchQuery, index, offset ->
                        if (searchQuery == null) {
                            savedCategory = categoryId
                            savedPosition = index to offset
                        }
                    },
                )
            }
        }

        compose.onNodeWithText("酱汁基础").performClick()
        compose.onNodeWithText("基础酱汁").performClick()
        compose.onNodeWithText("档案 01").performClick()

        assertThat(openedId).isEqualTo("s1")
        assertThat(savedCategory).isEqualTo("basic")
        assertThat(savedPosition).isEqualTo(2 to 17)
    }

    private fun archiveCategory(
        id: String,
        parentId: String?,
        name: String,
        descendantCount: Int,
        childCount: Int,
    ) = KitchenArchiveCategory(
        id = id,
        library = KitchenArchiveKind.SAUCE,
        parentId = parentId,
        name = name,
        englishName = null,
        sortOrder = 0,
        testBatch = null,
        directCount = if (childCount == 0) descendantCount else 0,
        descendantCount = descendantCount,
        childCount = childCount,
    )

    private fun archiveEntry(
        id: String,
        name: String,
    ) = KitchenArchiveEntry(
        id = id,
        kind = KitchenArchiveKind.SAUCE,
        name = name,
        subtitle = null,
        category = "基础酱汁",
        searchText = name,
        categoryId = "basic",
        categoryPath = listOf("酱汁基础", "基础酱汁"),
    )
}
