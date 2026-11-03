package com.culinary.core.domain.rule.requirement

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 缺口计算：满足、不足与库存单位换算（R01 阶段 8）。 */
class InventoryAllocatorTest {

    private val allocator = InventoryAllocator()
    private val flour = IngredientId("flour")
    private val gram = unit("g", UnitDimension.MASS, 1.0)
    private val kilogram = unit("kg", UnitDimension.MASS, 1000.0)

    @Test
    fun `reports no shortfall when stock is sufficient`() {
        val gap = allocate(required = 500.0, stock = 800.0)

        assertThat(gap.available.amount).isWithin(1e-9).of(800.0)
        assertThat(gap.shortfall.amount).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `reports the missing amount when stock is short`() {
        val gap = allocate(required = 500.0, stock = 300.0)

        assertThat(gap.shortfall.amount).isWithin(1e-9).of(200.0)
    }

    @Test
    fun `converts kilogram stock to the required gram unit`() {
        val requirements = listOf(
            RequirementGroup(flour, listOf(Quantity(1500.0, gram)), isOptional = false),
        )
        val stock = listOf(InventoryAvailability(flour, Quantity(1.0, kilogram)))

        val result = allocator.allocate(requirements, stock)

        val gap = (result as Outcome.Success).value.single()
        assertThat(gap.available.amount).isWithin(1e-9).of(1000.0)
        assertThat(gap.shortfall.amount).isWithin(1e-9).of(500.0)
    }

    private fun allocate(
        required: Double,
        stock: Double,
    ): RequirementGap {
        val requirements = listOf(
            RequirementGroup(flour, listOf(Quantity(required, gram)), isOptional = false),
        )
        val availability = listOf(InventoryAvailability(flour, Quantity(stock, gram)))

        val result = allocator.allocate(requirements, availability)
        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        return (result as Outcome.Success).value.single()
    }

    private fun unit(
        id: String,
        dimension: UnitDimension,
        factor: Double,
    ): Unit =
        Unit(
            id = UnitId(id),
            code = id,
            name = id,
            symbol = id,
            dimension = dimension,
            baseFactor = factor,
            isBase = factor == 1.0,
            isApproximate = false,
            displayPrecision = 2,
        )
}
