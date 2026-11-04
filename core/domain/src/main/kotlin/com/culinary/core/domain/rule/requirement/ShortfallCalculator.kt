package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.ShortfallLine
import com.culinary.core.domain.rule.unit.UnitConverter

/** 缺口报告：默认缺口与可选配料缺口分开（MB-D-015）。 */
data class ShortfallReport(
    val required: List<ShortfallLine>,
    val optional: List<ShortfallLine>,
) {
    val hasRequiredShortfall: Boolean get() = required.any { !it.isCovered }
    val hasOptionalShortfall: Boolean get() = optional.any { !it.isCovered }
}

/**
 * 缺口计算与可选配料口径（MB-D-015）。
 *
 * - 默认：可选配料**不计入**缺口（[ShortfallReport.required]），只在
 *   [ShortfallReport.optional] 中单独展示；
 * - `includeOptionalInGap = true` 时把可选行并入缺口，仅用于显式"含可选"视图；
 * - 库存量换算失败按 0 可用处理，缺口即为整行需求，不猜密度。
 */
class ShortfallCalculator(
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun calculate(
        requirements: List<RequirementLine>,
        stock: List<InventoryAvailability>,
        conversions: List<IngredientUnitConversion> = emptyList(),
        includeOptionalInGap: Boolean = false,
    ): Outcome<ShortfallReport> {
        val lines =
            requirements.map { requirement ->
                val available = availableFor(requirement, stock, conversions)
                ShortfallLine(
                    ingredientId = requirement.ingredientId,
                    required = requirement.quantity,
                    available = Quantity(available, requirement.quantity.unit),
                    shortfall =
                        Quantity(
                            maxOf(0.0, requirement.quantity.amount - available),
                            requirement.quantity.unit,
                        ),
                    isOptional = requirement.isOptional,
                )
            }
        val required = lines.filter { !it.isOptional || includeOptionalInGap }
        val optional = lines.filter { it.isOptional }
        return Outcome.success(ShortfallReport(required = required, optional = optional))
    }

    private fun availableFor(
        requirement: RequirementLine,
        stock: List<InventoryAvailability>,
        conversions: List<IngredientUnitConversion>,
    ): Double =
        stock
            .filter { it.ingredientId == requirement.ingredientId }
            .sumOf { availability ->
                when (
                    val converted =
                        unitConverter.convert(
                            availability.quantity,
                            requirement.quantity.unit,
                            conversions,
                            requirement.ingredientId,
                        )
                ) {
                    is Outcome.Success -> converted.value.amount
                    is Outcome.Failure -> 0.0
                }
            }

    /** 把汇总结果转成需求行；可选标记沿用汇总语义（组内全可选才为可选）。 */
    fun fromGroups(groups: List<RequirementGroup>): List<RequirementLine> =
        groups.flatMap { group ->
            group.totals.map { total ->
                RequirementLine(
                    ingredientId = group.ingredientId,
                    quantity = total,
                    isOptional = group.isOptional,
                )
            }
        }
}
