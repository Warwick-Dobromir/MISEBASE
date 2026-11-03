package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import kotlinx.coroutines.flow.Flow

/**
 * 食材聚合的读写端口（A03A §6.2）。
 *
 * 只返回领域模型，不暴露 Entity、DAO 或 SQL；接口按聚合划分，不按页面划分。
 */
interface IngredientRepository {
    fun observeActive(): Flow<List<Ingredient>>

    suspend fun get(id: IngredientId): Outcome<Ingredient>

    suspend fun save(ingredient: Ingredient): Outcome<IngredientId>

    suspend fun archive(id: IngredientId): Outcome<Unit>
}
