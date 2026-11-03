package com.culinary.core.domain.rule.unit

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class UnitConverterTest {
    private val converter = UnitConverter()

    @Test
    fun `returns the same quantity when the unit matches`() {
        val result = converter.convert(Quantity(2.5, Gram), Gram)

        assertThat(successAmount(result)).isWithin(1e-9).of(2.5)
    }

    @Test
    fun `converts kilograms to grams within the mass dimension`() {
        val result = converter.convert(Quantity(2.5, Kilogram), Gram)

        assertThat(successAmount(result)).isWithin(1e-9).of(2500.0)
    }

    @Test
    fun `converts grams to kilograms within the mass dimension`() {
        val result = converter.convert(Quantity(500.0, Gram), Kilogram)

        assertThat(successAmount(result)).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `rejects volume to mass without an ingredient level conversion`() {
        val result =
            converter.convert(
                quantity = Quantity(1.0, Cup),
                target = Gram,
                ingredientId = IngredientId("ing-flour"),
            )

        assertThat(failureCode(result)).isEqualTo(ErrorCode.UNIT_001)
        val detail = failureDetail(result)
        assertThat(detail).isInstanceOf(DomainDetail.UnitIncompatible::class.java)
        val incompatible = detail as DomainDetail.UnitIncompatible
        assertThat(incompatible.fromUnit).isEqualTo("cup")
        assertThat(incompatible.toUnit).isEqualTo("g")
        assertThat(incompatible.ingredientId).isEqualTo("ing-flour")
    }

    @Test
    fun `applies an ingredient level conversion from cup to gram`() {
        val conversions = listOf(IngredientUnitConversion(Cup.id, Gram.id, factor = 120.0, note = "面粉实测"))

        val result = converter.convert(Quantity(2.0, Cup), Gram, conversions)

        assertThat(successAmount(result)).isWithin(1e-9).of(240.0)
    }

    @Test
    fun `applies the inverse of an ingredient level conversion`() {
        val conversions = listOf(IngredientUnitConversion(Cup.id, Gram.id, factor = 120.0))

        val result = converter.convert(Quantity(120.0, Gram), Cup, conversions)

        assertThat(successAmount(result)).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `rejects different count units without an explicit conversion`() {
        val result = converter.convert(Quantity(2.0, Piece), Clove)

        assertThat(failureCode(result)).isEqualTo(ErrorCode.UNIT_001)
    }

    @Test
    fun `converts count units when an explicit conversion exists`() {
        val conversions = listOf(IngredientUnitConversion(Piece.id, Clove.id, factor = 10.0))

        val result = converter.convert(Quantity(2.0, Piece), Clove, conversions)

        assertThat(successAmount(result)).isWithin(1e-9).of(20.0)
    }

    @Test
    fun `rejects a conversion with a non positive factor at construction time`() {
        assertThrows(IllegalArgumentException::class.java) {
            IngredientUnitConversion(Cup.id, Gram.id, factor = 0.0)
        }
    }

    @Test
    fun `rejects a conversion that maps a unit onto itself`() {
        assertThrows(IllegalArgumentException::class.java) {
            IngredientUnitConversion(Cup.id, Cup.id, factor = 1.0)
        }
    }

    private fun successAmount(result: Outcome<Quantity>): Double =
        (result as? Outcome.Success)?.value?.amount
            ?: error("expected a successful conversion, was $result")

    private fun failureCode(result: Outcome<Quantity>): ErrorCode =
        (result as? Outcome.Failure)?.error?.code
            ?: error("expected a failed conversion, was $result")

    private fun failureDetail(result: Outcome<Quantity>): DomainDetail =
        ((result as? Outcome.Failure)?.error as? CulinaryError.Domain)?.detail
            ?: error("expected a domain failure, was $result")

    private companion object {
        val Gram = Unit(UnitId("u-g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val Kilogram = Unit(UnitId("u-kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 2)
        val Cup = Unit(UnitId("u-cup"), "cup", "杯", "cup", UnitDimension.VOLUME, 240.0, false, true, 2)
        val Piece = Unit(UnitId("u-piece"), "piece", "个", "个", UnitDimension.COUNT, 1.0, true, false, 0)
        val Clove = Unit(UnitId("u-clove"), "clove", "瓣", "瓣", UnitDimension.COUNT, 1.0, false, false, 0)
    }
}
