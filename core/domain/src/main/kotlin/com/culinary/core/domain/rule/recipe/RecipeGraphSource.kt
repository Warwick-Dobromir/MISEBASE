package com.culinary.core.domain.rule.recipe

import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId

/**
 * 配方图的数据来源。
 *
 * 领域层不访问数据库；校验器只通过这个端口读取版本。
 */
fun interface RecipeGraphSource {
    fun findVersion(id: RecipeVersionId): RecipeVersion?
}
