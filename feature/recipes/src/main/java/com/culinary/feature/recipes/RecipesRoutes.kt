package com.culinary.feature.recipes

/** 菜谱库路由契约；由 app 组合根注册。 */
object RecipesRoutes {
    const val LIST: String = "recipes"
    const val ARG_RECIPE_ID: String = "recipeId"
    const val DETAIL: String = "recipes/{$ARG_RECIPE_ID}"
    const val EDIT: String = "recipes/{$ARG_RECIPE_ID}/edit"
    const val NEW_ID: String = "new"

    fun detail(id: String): String = "recipes/$id"

    fun edit(id: String?): String = "recipes/${id ?: NEW_ID}/edit"
}
