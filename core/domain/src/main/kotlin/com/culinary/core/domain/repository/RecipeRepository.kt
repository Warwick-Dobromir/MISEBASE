package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import kotlinx.coroutines.flow.Flow

/** 菜谱聚合的读写端口；版本读取是图校验与展开的唯一数据入口。 */
interface RecipeRepository {
    fun observeActive(): Flow<List<Recipe>>

    suspend fun getVersion(id: RecipeVersionId): RecipeVersion?

    suspend fun saveDraft(version: RecipeVersion): RecipeVersionId

    /**
     * 保存已固定版本的发布版本（MB-D-013）。
     *
     * 组件目标必须已是明确版本 ID；实现需保证"同一时刻只有一个当前版本"。
     */
    suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId>
}

