package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.repository.IngredientRepository
import kotlinx.coroutines.flow.Flow

/**
 * 观察在用食材列表。
 *
 * 这是阶段 2 的最小用例，用于证明"UI → Use Case → Repository 端口"的调用形状；
 * 真正的筛选、搜索与分页在食材纵向切片（阶段 7）实现。
 */
class ListIngredientsUseCase(
    private val repository: IngredientRepository,
) {
    operator fun invoke(): Flow<List<Ingredient>> = repository.observeActive()
}
