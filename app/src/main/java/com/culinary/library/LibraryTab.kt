package com.culinary.library

import androidx.annotation.StringRes
import com.culinary.R

/**
 * 知识库聚合页的三个 Tab。
 *
 * 三个 feature 模块保持独立；这里只做外层容器，不复制其列表/详情逻辑。
 */
enum class LibraryTab(
    val route: String,
    @StringRes val labelRes: Int,
) {
    SAUCES("sauces", R.string.destination_sauces),
    INGREDIENTS("ingredients", R.string.destination_ingredients),
    RECIPES("recipes", R.string.destination_recipes),
    ;

    companion object {
        fun fromRoute(value: String?): LibraryTab? = entries.firstOrNull { it.route == value }
    }
}
