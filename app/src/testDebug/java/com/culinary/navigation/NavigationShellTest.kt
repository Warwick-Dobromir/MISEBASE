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

/** 五项等规格底栏：首页 / 厨房 / 扫码 / 库存 / 我的，扫码不再凸起。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `bottom bar shows exactly the five equal entries`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = HOME_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("首页").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("厨房").assertIsDisplayed()
        compose.onNodeWithText("扫码").assertIsDisplayed()
        compose.onNodeWithText("库存").assertIsDisplayed()
        compose.onNodeWithText("我的").assertIsDisplayed()
        compose.onAllNodesWithText("酱汁").assertCountEquals(0)
        compose.onAllNodesWithText("食材").assertCountEquals(0)
        compose.onAllNodesWithText("菜谱").assertCountEquals(0)

        val items = compose.onAllNodes(isSelectable()).fetchSemanticsNodes()
        assertThat(items).hasSize(5)
        val widths = items.map { it.boundsInRoot.width }
        assertThat(widths.maxOrNull()!!).isWithin(1f).of(widths.minOrNull()!!)
    }

    @Test
    fun `kitchen tab route keeps kitchen selected`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = LIBRARY_TAB_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("厨房").assertIsSelected()
        compose.onNodeWithText("首页").assertIsNotSelected()
        compose.onAllNodes(isSelectable()).assertCountEquals(5)
    }

    @Test
    fun `scan is a normal selected tab on the scan route`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = SCAN_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("扫码").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("厨房").assertIsNotSelected()
    }

    @Test
    fun `profile entry is selected when it is the active bottom entry`() {
        compose.setContent {
            CulinaryTheme {
                CulinaryBottomBar(currentRoute = SETTINGS_ROUTE, onSelect = {})
            }
        }

        compose.onNodeWithText("我的").assertIsSelected()
        compose.onNodeWithText("首页").assertIsNotSelected()
    }

    @Test
    fun `scan tab invokes the scan destination`() {
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
                    topLevel = CulinaryDestination.INVENTORY,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回首页").assertIsDisplayed()
    }

    @Test
    fun `top bar brand returns home from a business module`() {
        var openedHome = false
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
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
    fun `kitchen top level renders the brand action`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    topLevel = CulinaryDestination.LIBRARY,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回首页").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
    }

    @Test
    fun `archive top bar shows back and parent kitchen title without search`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    topLevel = CulinaryDestination.LIBRARY,
                    showBack = true,
                    title = "厨房",
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        compose.onNodeWithText("厨房").assertIsDisplayed()
        compose.onNodeWithContentDescription("厨房全局搜索").assertDoesNotExist()
    }

    @Test
    fun `scan top level renders the real scan title`() {
        compose.setContent {
            CulinaryTheme {
                AppTopBar(
                    topLevel = CulinaryDestination.SCAN,
                    showBack = false,
                    onOpenHome = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithContentDescription("返回首页").assertIsDisplayed()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
    }
}
