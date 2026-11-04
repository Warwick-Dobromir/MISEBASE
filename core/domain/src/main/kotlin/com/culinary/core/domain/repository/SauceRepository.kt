package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import kotlinx.coroutines.flow.Flow

/**
 * 酱汁聚合的读写端口（与 [RecipeRepository] 对称）。
 *
 * 阶段 2 只定义契约与 fake；酱汁档案页面与持久化实现在 Stage 3 落地，
 * 本阶段不新增表、不改 Entity。
 */
interface SauceRepository {
    fun observeActive(): Flow<List<Sauce>>

    suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion>

    suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId>
}
