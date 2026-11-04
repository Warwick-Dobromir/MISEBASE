package com.culinary.feature.products

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.scan.ScanIngredientGateway
import com.culinary.core.domain.scan.ScanIngredientRequest
import com.culinary.core.domain.scan.ScanIngredientTarget
import com.culinary.core.domain.usecase.ingredient.SaveIngredientInput
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 扫码识别的"创建/映射食材"接口实现（MB-D-004 §2.3）。
 *
 * 归属明确：食材档案与商品映射由 `feature:products` 提供，`feature:scan` 只调用本接口。
 * - 已有食材：读取食材与其启用商品，供确认页展示匹配结果；
 * - 新建食材：走既有 `SaveIngredientUseCase`（含名称校验），不绕过领域规则；
 * - 单位：优先食材默认单位，其次系统第一个可用单位；
 * - 不写库存表、不开事务（事务/补偿由确认页编排）。
 */
@Singleton
class ProductScanIngredientGateway
    @Inject
    constructor(
        private val ingredientRepository: IngredientRepository,
        private val inventoryRepository: InventoryRepository,
        private val unitRepository: UnitRepository,
        private val saveIngredient: SaveIngredientUseCase,
    ) : ScanIngredientGateway {
        override suspend fun resolve(request: ScanIngredientRequest): Outcome<ScanIngredientTarget> {
            val existingId = request.existingIngredientId
            val newName = request.newIngredientName?.trim().orEmpty()
            return when {
                existingId != null -> targetFor(existingId, created = false)
                newName.isNotEmpty() -> createIngredient(newName)
                else ->
                    Outcome.failure(
                        CulinaryError.Validation(
                            field = "ingredient",
                            rule = "must select an existing ingredient or provide a new name",
                        ),
                    )
            }
        }

        private suspend fun createIngredient(name: String): Outcome<ScanIngredientTarget> =
            when (val saved = saveIngredient(SaveIngredientInput(name = name))) {
                is Outcome.Failure -> saved
                is Outcome.Success -> targetFor(saved.value, created = true)
            }

        private suspend fun targetFor(
            ingredientId: IngredientId,
            created: Boolean,
        ): Outcome<ScanIngredientTarget> {
            val ingredient =
                when (val loaded = ingredientRepository.get(ingredientId)) {
                    is Outcome.Failure -> return loaded
                    is Outcome.Success -> loaded.value
                }
            val product =
                when (val existing = inventoryRepository.findProductForIngredient(ingredientId)) {
                    is Outcome.Failure -> return existing
                    is Outcome.Success -> existing.value
                }
            val unit =
                resolveUnit(ingredient)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "unit", id = "ingredient.default"))
            return Outcome.success(
                ScanIngredientTarget(
                    ingredient = ingredient,
                    product = product,
                    unit = unit,
                    created = created,
                ),
            )
        }

        private suspend fun resolveUnit(ingredient: Ingredient): Unit? {
            ingredient.defaultUnitId?.let { unitId ->
                unitRepository.find(unitId)?.let { return it }
            }
            return unitRepository.all().firstOrNull()
        }
    }
