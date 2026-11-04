package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 可选配料缺料口径（MB-D-015）：默认不计入缺口，详情单独可见。 */
class ShortfallCalculatorTest {
    private val calculator = ShortfallCalculator()

    @Test
    fun `optional lines stay out of the default gap and remain visible separately`() {
        val report =
            calculate(
                requirements =
                    listOf(
                        RequirementLine(Flour, Quantity(100.0, Gram)),
                        RequirementLine(Cheese, Quantity(50.0, Gram), isOptional = true),
                    ),
                stock = listOf(InventoryAvailability(Flour, Quantity(100.0, Gram))),
            )

        assertThat(report.required.map { it.ingredientId }).containsExactly(Flour)
        assertThat(report.optional.map { it.ingredientId }).containsExactly(Cheese)
        assertThat(report.optional.single().shortfall.amount).isWithin(1e-9).of(50.0)
        assertThat(report.hasRequiredShortfall).isFalse()
        assertThat(report.hasOptionalShortfall).isTrue()
    }

    @Test
    fun `explicitly including optional lines merges them into the gap`() {
        val report =
            calculate(
                requirements = listOf(RequirementLine(Cheese, Quantity(50.0, Gram), isOptional = true)),
                stock = emptyList(),
                includeOptionalInGap = true,
            )

        assertThat(report.required).hasSize(1)
        assertThat(report.required.single().isOptional).isTrue()
    }

    @Test
    fun `computes the uncovered quantity per requirement line`() {
        val report =
            calculate(
                requirements = listOf(RequirementLine(Flour, Quantity(500.0, Gram))),
                stock = listOf(InventoryAvailability(Flour, Quantity(1.2, Kilogram))),
            )

        val line = report.required.single()
        assertThat(line.available.amount).isWithin(1e-9).of(1200.0)
        assertThat(line.shortfall.amount).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `treats an unconvertible stock unit as zero available`() {
        val report =
            calculate(
                requirements = listOf(RequirementLine(Flour, Quantity(500.0, Gram))),
                stock = listOf(InventoryAvailability(Flour, Quantity(2.0, Cup))),
            )

        assertThat(report.required.single().shortfall.amount).isWithin(1e-9).of(500.0)
    }

    @Test
    fun `applies ingredient level conversions when summing stock`() {
        val report =
            calculate(
                requirements = listOf(RequirementLine(Flour, Quantity(240.0, Gram))),
                stock = listOf(InventoryAvailability(Flour, Quantity(2.0, Cup))),
                conversions = listOf(IngredientUnitConversion(Cup.id, Gram.id, factor = 120.0)),
            )

        assertThat(report.required.single().shortfall.amount).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `maps aggregated groups into requirement lines`() {
        val groups =
            listOf(
                RequirementGroup(Flour, listOf(Quantity(100.0, Gram)), isOptional = false),
                RequirementGroup(Cheese, listOf(Quantity(20.0, Gram)), isOptional = true),
            )

        val lines = calculator.fromGroups(groups)

        assertThat(lines.map { it.isOptional }).containsExactly(false, true).inOrder()
    }

    private fun calculate(
        requirements: List<RequirementLine>,
        stock: List<InventoryAvailability>,
        conversions: List<IngredientUnitConversion> = emptyList(),
        includeOptionalInGap: Boolean = false,
    ): ShortfallReport {
        val result =
            calculator.calculate(
                requirements = requirements,
                stock = stock,
                conversions = conversions,
                includeOptionalInGap = includeOptionalInGap,
            )
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        return (result as Outcome.Success).value
    }

    private companion object {
        val Flour = IngredientId("flour")
        val Cheese = IngredientId("cheese")
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val Kilogram = Unit(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 2)
        val Cup = Unit(UnitId("cup"), "cup", "杯", "cup", UnitDimension.VOLUME, 240.0, false, true, 1)
    }
}
