package com.culinary.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.culinary.R
import com.culinary.ui.PlaceholderScreen

/**
 * 首页中尚未开发的二级入口。
 *
 * 本次只交付首页，因此这些入口明确显示“尚未开发”的占位说明，
 * 不伪造表单、详情或计时功能，也不把占位页当作已完成页面。
 */
enum class HomePlaceholderSection(
    val routeValue: String,
) {
    TIMER("timer"),
    PLANS("plans"),
    PLAN_ADD("plan-add"),
    PURCHASES("purchases"),
    PURCHASE_ADD("purchase-add"),
    ;

    companion object {
        fun fromRoute(value: String?): HomePlaceholderSection? = entries.firstOrNull { it.routeValue == value }
    }
}

const val HOME_PLACEHOLDER_SECTION_ARG: String = "section"
const val HOME_PLACEHOLDER_ROUTE: String = "home/placeholder/{$HOME_PLACEHOLDER_SECTION_ARG}"

const val HOME_PURCHASE_ID_ARG: String = "purchaseId"
const val HOME_PURCHASE_DETAIL_ROUTE: String = "home/purchases/{$HOME_PURCHASE_ID_ARG}"

fun homePlaceholderRoute(section: HomePlaceholderSection): String = "home/placeholder/${section.routeValue}"

fun homePurchaseDetailRoute(purchaseId: String): String = "home/purchases/$purchaseId"

@Composable
fun HomePlaceholderRoute(
    section: HomePlaceholderSection,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (section) {
            HomePlaceholderSection.TIMER -> R.string.home_placeholder_timer_title
            HomePlaceholderSection.PLANS -> R.string.home_placeholder_plans_title
            HomePlaceholderSection.PLAN_ADD -> R.string.home_placeholder_plan_add_title
            HomePlaceholderSection.PURCHASES -> R.string.home_placeholder_purchases_title
            HomePlaceholderSection.PURCHASE_ADD -> R.string.home_placeholder_purchase_add_title
        }
    val bodyRes =
        when (section) {
            HomePlaceholderSection.TIMER -> R.string.home_placeholder_timer_body
            HomePlaceholderSection.PLANS -> R.string.home_placeholder_plans_body
            HomePlaceholderSection.PLAN_ADD -> R.string.home_placeholder_plan_add_body
            HomePlaceholderSection.PURCHASES -> R.string.home_placeholder_purchases_body
            HomePlaceholderSection.PURCHASE_ADD -> R.string.home_placeholder_purchase_add_body
        }

    PlaceholderScreen(
        title = stringResource(titleRes),
        description = stringResource(bodyRes),
        modifier = modifier,
    )
}

@Composable
fun HomePurchasePlaceholderRoute(
    purchaseId: String,
    modifier: Modifier = Modifier,
) {
    val purchase = HomeMockData.purchaseById(purchaseId)
    val purchaseName =
        purchase?.let { stringResource(it.nameRes) }
            ?: stringResource(R.string.home_placeholder_purchase_unknown)
    PlaceholderScreen(
        title = stringResource(R.string.home_placeholder_purchase_detail_title),
        description =
            stringResource(
                R.string.home_placeholder_purchase_detail_body,
                purchaseName,
            ),
        modifier = modifier,
    )
}
