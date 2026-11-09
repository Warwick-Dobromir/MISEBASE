package com.culinary.cooking

/** 统一制作模式的路由常量：菜谱与酱汁共用同一页面。 */
object CookingRoute {
    const val KIND_SAUCE: String = "sauce"
    const val KIND_RECIPE: String = "recipe"

    const val KIND_ARG: String = "kind"
    const val ID_ARG: String = "id"
    const val STEP_ARG: String = "step"

    const val ROUTE: String = "cooking/{$KIND_ARG}/{$ID_ARG}/{$STEP_ARG}"

    fun route(
        source: CookingSource,
        archiveId: String,
        startStep: Int,
    ): String {
        val kind = if (source == CookingSource.SAUCE) KIND_SAUCE else KIND_RECIPE
        return "cooking/$kind/$archiveId/${startStep.coerceAtLeast(1)}"
    }
}
