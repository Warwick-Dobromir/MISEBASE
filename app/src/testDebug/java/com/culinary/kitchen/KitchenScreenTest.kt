package com.culinary.kitchen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.culinary.core.data.kitchen.KitchenCounts
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 厨房首页：分组、六类入口、动态数量；跨库搜索入口已移除。 */
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
                        isLoading = false,
                        counts =
                            KitchenCounts(
                                sauceCount = 10,
                                ingredientCount = 18,
                                recipeCount = 10,
                                cookingTechniqueCount = 10,
                                productionStandardCount = 10,
                                storageStandardCount = 10,
                            ),
                    ),
            )
        }

        compose.onNodeWithText("MISEBASE").assertIsDisplayed()
        compose.onNodeWithText("厨房").assertIsDisplayed()
        compose.onNodeWithText("烹饪资料").assertIsDisplayed()
        compose.onNodeWithText("工作标准").assertIsDisplayed()
        compose.onNodeWithText("酱汁").assertIsDisplayed()
        compose.onAllNodesWithText("10 项").assertCountEquals(5)
        compose.onNodeWithText("18 项").assertIsDisplayed()
    }

    @Test
    fun `category row invokes callback`() {
        var opened: KitchenCategory? = null
        compose.setContent {
            KitchenUnderTest(
                state = KitchenUiState(isLoading = false),
                onOpenCategory = { opened = it },
            )
        }

        compose.onNodeWithText("酱汁").performClick()
        assertThat(opened).isEqualTo(KitchenCategory.SAUCES)
    }

    @Test
    fun `cross library search entry is gone`() {
        compose.setContent {
            KitchenUnderTest(state = KitchenUiState(isLoading = false))
        }

        compose
            .onNodeWithContentDescription("厨房全局搜索")
            .assertDoesNotExist()
    }

    @Composable
    private fun KitchenUnderTest(
        state: KitchenUiState,
        onOpenCategory: (KitchenCategory) -> Unit = {},
    ) {
        CulinaryTheme {
            KitchenScreen(
                state = state,
                onOpenCategory = onOpenCategory,
            )
        }
    }
}
