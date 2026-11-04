package com.culinary.core.domain.rule.recipe

import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId

/**
 * 配方图的数据来源。
 *
 * 领域层不访问数据库；校验器只通过这个端口读取版本。
 * 酱汁图展开（Stage 4）通过 [findSauceVersion] 读取子酱汁；默认返回 null，
 * 让未提供酱汁图的调用方明确得到"版本缺失"，而不是静默少算。
 */
fun interface RecipeGraphSource {
    fun findVersion(id: RecipeVersionId): RecipeVersion?

    fun findSauceVersion(id: SauceVersionId): SauceVersion? = null
}
