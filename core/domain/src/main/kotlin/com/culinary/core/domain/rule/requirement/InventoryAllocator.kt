package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.rule.unit.UnitConverter

/** 库存可用量（按食材）。 */
data class InventoryAvailability(
    val ingredientId: IngredientId,
    val quantity: Quantity,
)

/** 单行缺口；[shortfall] 为 0 表示满足。 */
data class RequirementGap(
    val ingredientId: IngredientId,
    val required: Quantity,
    val available: Quantity,
    val shortfall: Quantity,
)

/**
 * 缺口计算（R01 阶段 8）。
 *
 * 把库存量换算到需求单位后相减；换算失败（缺食材级换算）按 0 可用处理，
 * 缺口即为全部需求量，不猜测密度或包装规格。
 */
class InventoryAllocator(
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun allocate(
        requirements: List<RequirementGroup>,
        stock: List<InventoryAvailability>,
        conversions: List<IngredientUnitConversion> = emptyList(),
    ): Outcome<List<RequirementGap>> {
        val gaps =
            requirements.flatMap { group ->
                group.totals.map { required ->
                    val available = availableFor(group.ingredientId, required, stock, conversions)
                    RequirementGap(
                        ingredientId = group.ingredientId,
                        required = required,
                        available = Quantity(available, required.unit),
                        shortfall = Quantity(maxOf(0.0, required.amount - available), required.unit),
                    )
                }
            }
        return Outcome.success(gaps)
    }

    private fun availableFor(
        ingredientId: IngredientId,
        required: Quantity,
        stock: List<InventoryAvailability>,
        conversions: List<IngredientUnitConversion>,
    ): Double =
        stock
            .filter { it.ingredientId == ingredientId }
            .sumOf { availability ->
                when (
                    val converted =
                        unitConverter.convert(
                            availability.quantity,
                            required.unit,
                            conversions,
                            ingredientId,
                        )
                ) {
                    is Outcome.Success -> converted.value.amount
                    is Outcome.Failure -> 0.0
                }
            }
}
