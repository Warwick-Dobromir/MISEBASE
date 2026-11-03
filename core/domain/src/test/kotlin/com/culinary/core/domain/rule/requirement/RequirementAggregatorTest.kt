package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.rule.recipe.ExplodedRequirement
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 需求汇总：可合并与不可合并（R01 阶段 8）。 */
class RequirementAggregatorTest {
    private val aggregator = RequirementAggregator()
    private val flour = IngredientId("flour")
    private val egg = IngredientId("egg")

    private val gram = unit("g", "g", UnitDimension.MASS, 1.0)
    private val kilogram = unit("kg", "kg", UnitDimension.MASS, 1000.0)
    private val piece = unit("piece", "piece", UnitDimension.COUNT, 1.0)
    private val pack = unit("pack", "pack", UnitDimension.COUNT, 1.0)

    @Test
    fun `merges the same ingredient in the same unit`() {
        val groups =
            aggregate(
                requirement(flour, 100.0, gram),
                requirement(flour, 50.0, gram),
            )

        assertThat(groups).hasSize(1)
        assertThat(groups.single().totals).hasSize(1)
        assertThat(
            groups
                .single()
                .totals
                .single()
                .amount,
        ).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `merges different mass units into the first unit`() {
        val groups =
            aggregate(
                requirement(flour, 100.0, gram),
                requirement(flour, 1.0, kilogram),
            )

        val total = groups.single().totals.single()
        assertThat(total.unit).isEqualTo(gram)
        assertThat(total.amount).isWithin(1e-9).of(1100.0)
    }

    @Test
    fun `keeps count units separate instead of guessing a conversion`() {
        val groups =
            aggregate(
                requirement(egg, 2.0, piece),
                requirement(egg, 3.0, pack),
            )

        assertThat(groups.single().totals).hasSize(2)
    }

    @Test
    fun `marks a group optional only when every line is optional`() {
        val mixed =
            aggregate(
                requirement(flour, 1.0, gram, isOptional = true),
                requirement(flour, 1.0, gram),
            )
        val allOptional =
            aggregate(
                requirement(egg, 1.0, piece, isOptional = true),
                requirement(egg, 2.0, piece, isOptional = true),
            )

        assertThat(mixed.single().isOptional).isFalse()
        assertThat(allOptional.single().isOptional).isTrue()
    }

    private fun aggregate(vararg requirements: ExplodedRequirement): List<RequirementGroup> {
        val result = aggregator.aggregate(requirements.toList())
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        return (result as Outcome.Success).value
    }

    private fun requirement(
        ingredient: IngredientId,
        amount: Double,
        unit: Unit,
        isOptional: Boolean = false,
    ): ExplodedRequirement = ExplodedRequirement(ingredient, Quantity(amount, unit), isOptional)

    private fun unit(
        id: String,
        code: String,
        dimension: UnitDimension,
        factor: Double,
    ): Unit =
        Unit(
            id = UnitId(id),
            code = code,
            name = code,
            symbol = code,
            dimension = dimension,
            baseFactor = factor,
            isBase = factor == 1.0,
            isApproximate = false,
            displayPrecision = 2,
        )
}
