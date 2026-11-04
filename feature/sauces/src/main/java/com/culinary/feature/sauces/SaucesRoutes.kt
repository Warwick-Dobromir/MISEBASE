package com.culinary.feature.sauces

/**
 * 酱汁库路由契约。
 *
 * 路由字符串由 app 组合根注册；feature 不依赖 app，只暴露稳定的路由常量与构造器。
 */
object SaucesRoutes {
    const val LIST: String = "sauces"
    const val ARG_SAUCE_ID: String = "sauceId"
    const val DETAIL: String = "sauces/{$ARG_SAUCE_ID}"
    const val EDIT: String = "sauces/{$ARG_SAUCE_ID}/edit"
    const val NEW_ID: String = "new"

    fun detail(sauceId: String): String = "sauces/$sauceId"

    fun edit(sauceId: String?): String = "sauces/${sauceId ?: NEW_ID}/edit"
}
