package com.culinary.core.domain.rule.unit

import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.DisplayQuantity
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 单位换算的精度与舍入（阶段 2）；计算保留精度，展示按单位精度 HALF_UP。 */
class UnitConverterDisplayTest {
    private val converter = UnitConverter()

    @Test
    fun `display rounds half up to the unit precision`() {
        assertThat(converter.display(Quantity(0.25, Gram)).amount).isWithin(1e-9).of(0.3)
        assertThat(converter.display(Quantity(0.24, Gram)).amount).isWithin(1e-9).of(0.2)
    }

    @Test
    fun `display honours a zero decimal precision unit`() {
        assertThat(converter.display(Quantity(999.5, Piece)).amount).isWithin(1e-9).of(1000.0)
    }

    @Test
    fun `convert for display rounds after conversion using the target precision`() {
        val result = converter.convertForDisplay(Quantity(1.2345, Kilogram), Gram)

        assertThat(result).isInstanceOf(Outcome.Success::class.java)
        val display = (result as Outcome.Success).value
        assertThat(display.amount).isWithin(1e-9).of(1234.5)
        assertThat(display.unit.id).isEqualTo(Gram.id)
    }

    @Test
    fun `convert for display flags approximate source units`() {
        val result = converter.convertForDisplay(Quantity(1.0, Cup), Milliliter)

        val display = (result as Outcome.Success).value
        assertThat(display.amount).isWithin(1e-9).of(240.0)
        assertThat(display.isApproximate).isTrue()
    }

    @Test
    fun `convert for display keeps exact units unmarked`() {
        val display = (converter.convertForDisplay(Quantity(2.0, Kilogram), Gram) as Outcome.Success).value

        assertThat(display.isApproximate).isFalse()
    }

    @Test
    fun `convert for display propagates an unconvertible unit error`() {
        val result =
            converter.convertForDisplay(
                quantity = Quantity(1.0, Cup),
                target = Gram,
                ingredientId = IngredientId("flour"),
            )

        assertThat((result as Outcome.Failure).error.code).isEqualTo(ErrorCode.UNIT_001)
    }

    @Test
    fun `convert keeps full precision for calculation`() {
        val result = converter.convert(Quantity(1.0, Kilogram), Gram)

        assertThat((result as Outcome.Success).value.amount).isWithin(1e-9).of(1000.0)
        val third = converter.convert(Quantity(1.0, Gram), Ounce)
        assertThat((third as Outcome.Success).value.amount).isWithin(1e-6).of(0.0352739619)
    }

    @Test
    fun `display quantity rejects a negative amount`() {
        runCatching { DisplayQuantity(-1.0, Gram, isApproximate = false) }
            .onSuccess { error("expected DisplayQuantity to reject a negative amount") }
    }

    private companion object {
        val Gram = Unit(UnitId("g"), "g", "克", "g", UnitDimension.MASS, 1.0, true, false, 1)
        val Kilogram = Unit(UnitId("kg"), "kg", "千克", "kg", UnitDimension.MASS, 1000.0, false, false, 3)
        val Ounce = Unit(UnitId("oz"), "oz", "盎司", "oz", UnitDimension.MASS, 28.349523125, false, true, 3)
        val Milliliter = Unit(UnitId("ml"), "ml", "毫升", "ml", UnitDimension.VOLUME, 1.0, true, false, 1)
        val Cup = Unit(UnitId("cup"), "cup", "杯", "cup", UnitDimension.VOLUME, 240.0, false, true, 1)
        val Piece = Unit(UnitId("piece"), "piece", "个", "个", UnitDimension.COUNT, 1.0, true, false, 0)
    }
}
