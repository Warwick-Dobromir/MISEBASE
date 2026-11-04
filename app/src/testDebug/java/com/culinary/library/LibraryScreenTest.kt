package com.culinary.library

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.culinary.core.designsystem.theme.CulinaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 知识库聚合容器：三个 Tab、选中态和内容切换。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h800dp")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `three tabs are visible with sauces selected by default`() {
        show()

        compose.onNodeWithText("酱汁").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("食材").assertIsDisplayed()
        compose.onNodeWithText("菜谱").assertIsDisplayed()
        compose.onNodeWithText("内容：sauces").assertIsDisplayed()
    }

    @Test
    fun `switching tabs replaces the content`() {
        show()

        compose.onNodeWithText("食材").performClick()
        compose.onNodeWithText("食材").assertIsSelected()
        compose.onNodeWithText("内容：ingredients").assertIsDisplayed()
        compose.onNodeWithText("内容：sauces").assertDoesNotExist()

        compose.onNodeWithText("菜谱").performClick()
        compose.onNodeWithText("菜谱").assertIsSelected()
        compose.onNodeWithText("内容：recipes").assertIsDisplayed()
    }

    private fun show() {
        compose.setContent {
            CulinaryTheme {
                var selected by remember { mutableStateOf(LibraryTab.SAUCES) }
                LibraryScreen(
                    selectedTab = selected,
                    onSelectTab = { selected = it },
                ) { tab ->
                    Text(text = "内容：${tab.route}")
                }
            }
        }
    }
}
