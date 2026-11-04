package com.culinary.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** N3 导航外壳 UI：五项底栏、中央扫码、设置入底栏、顶栏不再有设置图标。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `bottom bar shows exactly the five N3 entries`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = HOME_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("首页").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("知识库").assertIsDisplayed()
        compose.onNodeWithText("扫码").assertIsDisplayed()
        compose.onNodeWithText("库存").assertIsDisplayed()
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onAllNodesWithText("酱汁").assertCountEquals(0)
        compose.onAllNodesWithText("食材").assertCountEquals(0)
        compose.onAllNodesWithText("菜谱").assertCountEquals(0)
        compose.onAllNodes(isSelectable()).assertCountEquals(5)
    }

    @Test
    fun `library tab route keeps knowledge library selected`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = LIBRARY_TAB_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("知识库").assertIsSelected()
        compose.onNodeWithText("首页").assertIsNotSelected()
        compose.onAllNodes(isSelectable()).assertCountEquals(5)
    }

    @Test
    fun `settings is selected when it is the active bottom entry`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = SETTINGS_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("设置").assertIsSelected()
        compose.onNodeWithText("首页").assertIsNotSelected()
    }

    @Test
    fun `center scan button invokes the scan destination`() {
        var selected: CulinaryDestination? = null
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = HOME_ROUTE, onSelect = { selected = it })
            }
        }

        compose.onNodeWithText("扫码").performClick()

        assertThat(selected).isEqualTo(CulinaryDestination.SCAN)
    }

    @Test
    fun `top bar no longer exposes a settings action`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = INVENTORY_ROUTE,
                    topLevel = CulinaryDestination.INVENTORY,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("库存").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回首页").assertIsDisplayed()
    }

    @Test
    fun `top bar brand returns home from a business module`() {
        var openedHome = false
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = INVENTORY_ROUTE,
                    topLevel = CulinaryDestination.INVENTORY,
                    showBack = false,
                    onOpenHome = { openedHome = true },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回首页").performClick()

        assertThat(openedHome).isTrue()
    }

    @Test
    fun `top bar shows a back action off the top level`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = "sauces/{sauceId}",
                    topLevel = null,
                    showBack = true,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回首页").assertDoesNotExist()
    }

    @Test
    fun `library top level renders the library title`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = LIBRARY_ROUTE,
                    topLevel = CulinaryDestination.LIBRARY,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("知识库").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
    }

    @Test
    fun `scan top level renders the real scan title`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    currentRoute = SCAN_ROUTE,
                    topLevel = CulinaryDestination.SCAN,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("扫码").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
    }
}
