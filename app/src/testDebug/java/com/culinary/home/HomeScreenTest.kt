package com.culinary.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.culinary.core.data.home.HomeInventorySummary
import com.culinary.core.data.home.HomeKnowledgeSummary
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 首页 Compose：真实数字、空/失败状态、四个快捷入口与品牌回调；Hero 不再有设置。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `content state shows inventory and knowledge numbers from state`() {
        show(
            state =
                HomeUiState(
                    todayLabel = "11月5日 星期四",
                    inventory = HomeSection.Ready(HomeInventorySummary(7, 12, 2)),
                    knowledge = HomeSection.Ready(HomeKnowledgeSummary(3, 5, 8)),
                ),
        )

        compose.onNodeWithText("库存概览").assertIsDisplayed()
        compose.onNodeWithText("库存项目").assertIsDisplayed()
        compose.onNodeWithText("批次总数").assertIsDisplayed()
        compose.onNodeWithText("需关注").assertIsDisplayed()
        compose.onNodeWithText("7").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("知识库概览").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.onNodeWithText("8").assertIsDisplayed()
        compose.onNodeWithText("11月5日 星期四").assertIsDisplayed()
    }

    @Test
    fun `empty state is explicit instead of fabricated data`() {
        show(
            state =
                HomeUiState(
                    todayLabel = "11月5日 星期四",
                    inventory = HomeSection.Ready(HomeInventorySummary(0, 0, 0)),
                    knowledge = HomeSection.Ready(HomeKnowledgeSummary(0, 0, 0)),
                ),
        )

        compose.onNodeWithText("还没有库存批次").assertIsDisplayed()
        compose.onNodeWithText("档案库还是空的").assertIsDisplayed()
        compose.onNodeWithText("去入库").assertIsDisplayed()
    }

    @Test
    fun `inventory failure degrades locally and keeps knowledge visible`() {
        var retried = false
        show(
            state =
                HomeUiState(
                    todayLabel = "11月5日 星期四",
                    inventory = HomeSection.Failed("本机数据库读取失败（CUL-DB-001）"),
                    knowledge = HomeSection.Ready(HomeKnowledgeSummary(3, 5, 8)),
                ),
            onRetryInventory = { retried = true },
        )

        compose.onNodeWithText("库存数据暂时无法读取").assertIsDisplayed()
        compose.onNodeWithText("知识库概览").assertIsDisplayed()
        compose.onNodeWithText("5").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertThat(retried).isTrue()
    }

    @Test
    fun `quick actions invoke real destinations`() {
        var scan = false
        var ingredient = false
        var recipe = false
        var sauce = false
        show(
            state = readyState(),
            onOpenScan = { scan = true },
            onNewIngredient = { ingredient = true },
            onNewRecipe = { recipe = true },
            onNewSauce = { sauce = true },
        )

        compose.onNodeWithText("扫码").performClick()
        compose.onNodeWithText("新建食材").performClick()
        compose.onNodeWithText("新建菜谱").performClick()
        compose.onNodeWithText("新建酱汁").performClick()

        assertThat(scan).isTrue()
        assertThat(ingredient).isTrue()
        assertThat(recipe).isTrue()
        assertThat(sauce).isTrue()
    }

    @Test
    fun `brand invokes the home callback and settings is gone from the hero`() {
        var home = false
        show(
            state = readyState(),
            onOpenHome = { home = true },
        )

        compose.onNodeWithText("MISEBASE").performClick()
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()

        assertThat(home).isTrue()
    }

    @Test
    fun `loading state renders skeletons without error banners`() {
        show(state = HomeUiState(todayLabel = "11月5日 星期四"))

        compose.onNodeWithText("库存概览").assertIsDisplayed()
        compose.onAllNodesWithText("重试").assertCountEquals(0)
        compose.onAllNodesWithText("库存数据暂时无法读取").assertCountEquals(0)
    }

    private fun readyState(): HomeUiState =
        HomeUiState(
            todayLabel = "11月5日 星期四",
            inventory = HomeSection.Ready(HomeInventorySummary(1, 3, 0)),
            knowledge = HomeSection.Ready(HomeKnowledgeSummary(0, 1, 0)),
        )

    @Suppress("LongParameterList")
    private fun show(
        state: HomeUiState,
        onOpenInventory: () -> Unit = {},
        onOpenReceipt: () -> Unit = {},
        onOpenSauces: () -> Unit = {},
        onOpenIngredients: () -> Unit = {},
        onOpenRecipes: () -> Unit = {},
        onOpenScan: () -> Unit = {},
        onNewIngredient: () -> Unit = {},
        onNewRecipe: () -> Unit = {},
        onNewSauce: () -> Unit = {},
        onOpenHome: () -> Unit = {},
        onRetryInventory: () -> Unit = {},
        onRetryKnowledge: () -> Unit = {},
    ) {
        compose.setContent {
            CulinaryTheme {
                HomeScreen(
                    state = state,
                    onOpenInventory = onOpenInventory,
                    onOpenReceipt = onOpenReceipt,
                    onOpenSauces = onOpenSauces,
                    onOpenIngredients = onOpenIngredients,
                    onOpenRecipes = onOpenRecipes,
                    onOpenScan = onOpenScan,
                    onNewIngredient = onNewIngredient,
                    onNewRecipe = onNewRecipe,
                    onNewSauce = onNewSauce,
                    onOpenHome = onOpenHome,
                    onRetryInventory = onRetryInventory,
                    onRetryKnowledge = onRetryKnowledge,
                )
            }
        }
    }
}
