package com.culinary.core.data.home

import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.Outcome
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.domain.model.InventoryOverviewRow
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 首页汇总实现：只读既有档案与库存用例，不写库、不新增 DAO/表。
 *
 * 所有读取都在 IO 调度器上执行；首页不直接触碰 Room 或 UI 线程。
 * 知识库的三条 Flow 任一失败都由本类映射为 `CulinaryError`，交给首页局部降级。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：把未知异常映射为 CUL-UNKNOWN-001
class DefaultHomeSummaryReader
    @Inject
    constructor(
        private val inventoryOverview: ListInventoryOverviewUseCase,
        private val listIngredients: ListIngredientsUseCase,
        private val listSauces: ListSaucesUseCase,
        private val listRecipes: ListRecipesUseCase,
        private val dispatchers: DispatcherProvider,
    ) : HomeSummaryReader {
        override suspend fun inventorySummary(): Outcome<HomeInventorySummary> =
            withContext(dispatchers.io) {
                when (val result = inventoryOverview()) {
                    is Outcome.Failure -> result
                    is Outcome.Success -> {
                        val rows = result.value
                        Outcome.success(
                            HomeInventorySummary(
                                productCount = rows.size,
                                batchCount = rows.sumOf(InventoryOverviewRow::batchCount),
                                attentionCount = rows.count(InventoryOverviewRow::isLow),
                            ),
                        )
                    }
                }
            }

        override suspend fun knowledgeSummary(): Outcome<HomeKnowledgeSummary> =
            withContext(dispatchers.io) {
                try {
                    Outcome.success(
                        HomeKnowledgeSummary(
                            sauceCount = listSauces().first().size,
                            ingredientCount = listIngredients().first().size,
                            recipeCount = listRecipes().first().size,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome.failure(DbErrorMapper.map(e))
                }
            }
    }
