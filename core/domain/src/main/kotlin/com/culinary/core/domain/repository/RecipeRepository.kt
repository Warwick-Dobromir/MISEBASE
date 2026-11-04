package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import kotlinx.coroutines.flow.Flow

/**
 * 菜谱聚合的读写端口；版本读取是图校验与展开的唯一数据入口。
 *
 * 方法与酱汁聚合对称；事务边界由 Use Case 控制。
 */
interface RecipeRepository {
    fun observeActive(): Flow<List<Recipe>>

    suspend fun get(id: RecipeId): Outcome<Recipe>

    suspend fun getVersion(id: RecipeVersionId): RecipeVersion?

    suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion?

    /** 按版本号倒序返回全部版本（含草稿与已发布）。 */
    suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion>

    suspend fun nextVersionNo(recipeId: RecipeId): Int

    suspend fun save(recipe: Recipe): Outcome<RecipeId>

    suspend fun saveDraft(version: RecipeVersion): RecipeVersionId

    /**
     * 保存已固定版本的发布版本（MB-D-013）。
     *
     * 组件目标必须已是明确版本 ID；实现需保证"同一时刻只有一个当前版本"。
     */
    suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId>
}
