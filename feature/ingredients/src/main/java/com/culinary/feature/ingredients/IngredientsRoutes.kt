package com.culinary.feature.ingredients

/** 食材库路由契约；由 app 组合根注册。 */
object IngredientsRoutes {
    const val LIST: String = "ingredients"
    const val ARG_INGREDIENT_ID: String = "ingredientId"
    const val DETAIL: String = "ingredients/{$ARG_INGREDIENT_ID}"
    const val EDIT: String = "ingredients/{$ARG_INGREDIENT_ID}/edit"
    const val NEW_ID: String = "new"

    fun detail(id: String): String = "ingredients/$id"

    fun edit(id: String?): String = "ingredients/${id ?: NEW_ID}/edit"
}
