package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.rule.recipe.ExplodedRequirement
import com.culinary.core.domain.rule.unit.UnitConverter

/** 同一食材的汇总结果；[totals] 保留无法合并的单位行。 */
data class RequirementGroup(
    val ingredientId: IngredientId,
    val totals: List<Quantity>,
    val isOptional: Boolean,
)

/**
 * 需求汇总（A06 §4.8 / R01 阶段 8）。
 *
 * - 同一食材、同一单位：直接相加；
 * - 同一食材、同维度不同单位（MASS/VOLUME）：换算到先出现的单位后相加；
 * - COUNT 维度或跨维度：**不强行合并**，保留为多行，交给调用方展示/决策；
 * - 可选标记只有在组内全部为可选时才为 true。
 */
class RequirementAggregator(
    private val unitConverter: UnitConverter = UnitConverter(),
) {
    fun aggregate(requirements: List<ExplodedRequirement>): Outcome<List<RequirementGroup>> {
        val totals = LinkedHashMap<IngredientId, MutableList<Quantity>>()
        val optional = LinkedHashMap<IngredientId, Boolean>()

        for (requirement in requirements) {
            optional.merge(requirement.ingredientId, requirement.isOptional) { left, right -> left && right }
            val lines = totals.getOrPut(requirement.ingredientId) { mutableListOf() }
            if (!mergeInto(lines, requirement.quantity)) {
                lines += requirement.quantity
            }
        }

        return Outcome.success(
            totals.map { (ingredientId, lines) ->
                RequirementGroup(
                    ingredientId = ingredientId,
                    totals = lines.toList(),
                    isOptional = optional[ingredientId] ?: false,
                )
            },
        )
    }

    private fun mergeInto(
        lines: MutableList<Quantity>,
        quantity: Quantity,
    ): Boolean {
        val sameUnitIndex = lines.indexOfFirst { it.unit.id == quantity.unit.id }
        if (sameUnitIndex >= 0) {
            val current = lines[sameUnitIndex]
            lines[sameUnitIndex] = Quantity(current.amount + quantity.amount, current.unit)
            return true
        }

        val convertibleIndex = lines.indexOfFirst { convertible(it.unit, quantity.unit) }
        if (convertibleIndex < 0) return false

        val target = lines[convertibleIndex].unit
        val converted = unitConverter.convert(quantity, target)
        if (converted !is Outcome.Success) return false

        val current = lines[convertibleIndex]
        lines[convertibleIndex] = Quantity(current.amount + converted.value.amount, target)
        return true
    }

    private fun convertible(
        existing: Unit,
        incoming: Unit,
    ): Boolean = existing.dimension == incoming.dimension && existing.dimension != UnitDimension.COUNT
}
