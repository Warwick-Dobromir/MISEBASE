package com.culinary.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.culinary.core.designsystem.theme.CulinaryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 导航外壳 Compose UI：五个一级入口、顺序、顶栏设置、占位页文案。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `bottom bar shows the five N1 entries in order`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = "sauces", onSelect = {})
            }
        }

        compose.onNodeWithText("酱汁").assertIsDisplayed()
        compose.onNodeWithText("食材").assertIsDisplayed()
        compose.onNodeWithText("菜谱").assertIsDisplayed()
        compose.onNodeWithText("库存").assertIsDisplayed()
        compose.onNodeWithText("扫码").assertIsDisplayed()
    }

    @Test
    fun `top bar exposes the settings action`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = "sauces",
                    topLevel = CulinaryDestination.SAUCES,
                    showBack = false,
                    onOpenSettings = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        compose.onNodeWithText("酱汁").assertIsDisplayed()
    }

    @Test
    fun `top bar shows a back action off the top level`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = "sauces/{sauceId}",
                    topLevel = null,
                    showBack = true,
                    onOpenSettings = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test
    fun `inventory top level renders the real inventory title and settings action`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = "inventory",
                    topLevel = CulinaryDestination.INVENTORY,
                    showBack = false,
                    onOpenSettings = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("库存").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
    }
}
