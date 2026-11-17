package com.culinary.kitchen.archive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.designsystem.theme.CulinaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ArchiveLibraryRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun childSortingControlIsHiddenAtRootAndVisibleOneLevelDown() {
        show()

        compose.onAllNodesWithContentDescription("排列下级分类").assertCountEquals(0)
        compose.onNodeWithText("基础酱汁").performClick()
        compose.onNodeWithContentDescription("排列下级分类").assertIsDisplayed()
    }

    @Test
    fun rootShowsOnePageLevelCreateChildActionAndNoBrandTitle() {
        show()

        compose.onAllNodesWithText("＋ 新建子库").assertCountEquals(1)
        compose.onAllNodesWithText("SAUCE COLLECTION").assertCountEquals(0)
    }

    private fun show() {
        val categories = listOf(
            category("base", null, "基础酱汁", childCount = 1),
            category("cold", "base", "冷酱"),
        )
        compose.setContent {
            CulinaryTheme {
                ArchiveLibraryScreen(
                    section = KitchenArchiveSection.SAUCES,
                    state = ArchiveLibraryUiState(categories = categories, isLoading = false),
                    onAdd = {},
                    onOpenEntry = {},
                )
            }
        }
    }

    private fun category(
        id: String,
        parentId: String?,
        name: String,
        childCount: Int = 0,
    ) = KitchenArchiveCategory(
        id = id,
        library = KitchenArchiveKind.SAUCE,
        parentId = parentId,
        name = name,
        englishName = null,
        sortOrder = 0,
        testBatch = null,
        directCount = 0,
        descendantCount = 0,
        childCount = childCount,
    )
}
