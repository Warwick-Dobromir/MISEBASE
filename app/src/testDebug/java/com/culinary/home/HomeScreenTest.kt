package com.culinary.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.culinary.core.designsystem.theme.CulinaryTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 首页视觉与交互：四条计划、动态计数/进度、三条采购记录及全部入口回调。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home shows masthead today plans and purchase records`() {
        compose.setContent { HomeUnderTest() }

        compose.onNodeWithText("MISEBASE").assertIsDisplayed()
        compose.onNodeWithText("Culinary Records and Inventory System").assertIsDisplayed()

        compose.onNodeWithText("今日计划").assertIsDisplayed()
        compose.onNodeWithText("Today’s Preparation").assertIsDisplayed()
        compose.onNodeWithText("00/04").assertIsDisplayed()
        compose.onNodeWithText("准备今日高汤").assertIsDisplayed()
        compose.onNodeWithText("准备今日酱汁").assertIsDisplayed()
        compose.onNodeWithText("检查食材库存").assertIsDisplayed()
        compose.onNodeWithText("整理今日菜谱").assertIsDisplayed()
        compose
            .onNodeWithTag("today-plan-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))

        compose.onNodeWithText("采购单").assertIsDisplayed()
        compose.onNodeWithText("Purchase Records").assertIsDisplayed()
        compose.onNodeWithText("今日蔬菜采购").assertIsDisplayed()
        compose.onNodeWithText("创建日期：2026.11.08").assertIsDisplayed()
        compose.onNodeWithText("创建时间：08:30").assertIsDisplayed()
        compose.onNodeWithText("乳制品补充").assertIsDisplayed()
        compose.onNodeWithText("肉类及高汤原料").assertIsDisplayed()
        compose.onNodeWithText("查看完整采购单").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping a plan toggles completion count and progress`() {
        compose.setContent { HomeUnderTest() }

        compose.onNodeWithText("准备今日高汤").assertIsOff().performClick()
        compose.onNodeWithText("01/04").assertExists()
        compose.onNodeWithText("准备今日高汤").assertIsOn()
        compose
            .onNodeWithTag("today-plan-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))

        compose.onNodeWithText("准备今日高汤").performClick()
        compose.onNodeWithText("00/04").assertExists()
        compose.onNodeWithText("准备今日高汤").assertIsOff()
        compose
            .onNodeWithTag("today-plan-progress")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
    }

    @Test
    fun `all plan and purchase actions invoke their callbacks`() {
        var timer = false
        var settings = false
        var allPlans = false
        var addPlan = false
        var newPurchase = false
        var allPurchases = false
        var openedPurchase: String? = null

        compose.setContent {
            HomeUnderTest(
                onOpenTimer = { timer = true },
                onOpenSettings = { settings = true },
                onOpenAllPlans = { allPlans = true },
                onAddPlan = { addPlan = true },
                onNewPurchase = { newPurchase = true },
                onOpenAllPurchases = { allPurchases = true },
                onOpenPurchase = { openedPurchase = it },
            )
        }

        compose.onNodeWithContentDescription("计时器").performClick()
        compose.onNodeWithContentDescription("设置与更多").performClick()
        compose.onNodeWithText("查看全部").performClick()
        compose.onNodeWithText("＋ 添加计划").performClick()
        compose.onNodeWithText("＋ 新建").performClick()
        compose.onNodeWithText("查看完整采购单").performScrollTo().performClick()
        compose.onNodeWithText("今日蔬菜采购").performScrollTo().performClick()

        assertThat(timer).isTrue()
        assertThat(settings).isTrue()
        assertThat(allPlans).isTrue()
        assertThat(addPlan).isTrue()
        assertThat(newPurchase).isTrue()
        assertThat(allPurchases).isTrue()
        assertThat(openedPurchase).isEqualTo("vegetables")
    }
}

/** 短屏适配：首页必须可滚动到采购单尾部，内容不能被底栏或屏幕裁掉。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h640dp")
class HomeScreenSmallHeightTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `purchase footer is reachable by vertical scrolling`() {
        compose.setContent { HomeUnderTest() }

        compose.onNodeWithText("肉类及高汤原料").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看完整采购单").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("以上为本地演示数据，未写入正式数据库。").performScrollTo().assertIsDisplayed()
    }
}

@Composable
private fun HomeUnderTest(
    onOpenTimer: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAllPlans: () -> Unit = {},
    onAddPlan: () -> Unit = {},
    onNewPurchase: () -> Unit = {},
    onOpenAllPurchases: () -> Unit = {},
    onOpenPurchase: (String) -> Unit = {},
) {
    var state by remember { mutableStateOf(HomeUiState()) }
    CulinaryTheme {
        HomeScreen(
            state = state,
            onTogglePlan = { planId ->
                state =
                    state.copy(
                        plans =
                            state.plans.map { plan ->
                                if (plan.id == planId) plan.copy(completed = !plan.completed) else plan
                            },
                    )
            },
            onOpenTimer = onOpenTimer,
            onOpenSettings = onOpenSettings,
            onOpenAllPlans = onOpenAllPlans,
            onAddPlan = onAddPlan,
            onNewPurchase = onNewPurchase,
            onOpenAllPurchases = onOpenAllPurchases,
            onOpenPurchase = onOpenPurchase,
        )
    }
}
