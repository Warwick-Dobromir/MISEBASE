package com.culinary.core.domain.rule.recipe

import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersionId

/**
 * 子菜谱"当前版本"的解析端口。
 *
 * 领域层不访问数据库；发布用例在事务外预取当前指针，再交给纯规则解析。
 */
fun interface CurrentVersionSource {
    fun currentVersionId(recipeId: RecipeId): RecipeVersionId?
}
