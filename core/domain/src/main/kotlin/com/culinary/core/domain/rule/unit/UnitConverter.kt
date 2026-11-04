package com.culinary.core.domain.rule.unit

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.DisplayQuantity
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 单位换算（D-008 / ADR-005）。
 *
 * 规则：
 * 1. 同一单位直接返回；
 * 2. 同一维度（MASS/VOLUME）按 `baseFactor` 比例换算；
 * 3. COUNT 维度**不**通过 `baseFactor` 互转，不同 COUNT 单位必须走食材级换算；
 * 4. 跨维度只允许食材级显式换算（`1 from = factor × to`），反向用 `1/factor`；
 * 5. 没有依据时返回 `CUL-UNIT-001`，绝不猜密度或默认值。
 *
 * 计算保留原始精度；展示用 [display] / [convertForDisplay]，按目标单位
 * `displayPrecision` 做 HALF_UP 舍入，并标记近似单位。
 */
class UnitConverter {
    fun convert(
        quantity: Quantity,
        target: Unit,
        ingredientConversions: List<IngredientUnitConversion> = emptyList(),
        ingredientId: IngredientId? = null,
    ): Outcome<Quantity> {
        val source = quantity.unit
        if (source.id == target.id) {
            return Outcome.success(quantity)
        }

        val factor =
            if (source.dimension == target.dimension && source.dimension != UnitDimension.COUNT) {
                source.baseFactor / target.baseFactor
            } else {
                explicitFactor(source, target, ingredientConversions)
                    ?: return incompatible(source, target, ingredientId)
            }
        return scaled(quantity, target, factor, ingredientId)
    }

    /** 同单位展示：按该单位精度舍入，近似单位打标。 */
    fun display(quantity: Quantity): DisplayQuantity =
        DisplayQuantity(
            amount = round(quantity.amount, quantity.unit),
            unit = quantity.unit,
            isApproximate = quantity.unit.isApproximate,
        )

    /** 换算 + 按目标单位精度舍入；换算失败时透传 `CUL-UNIT-001/002`。 */
    fun convertForDisplay(
        quantity: Quantity,
        target: Unit,
        ingredientConversions: List<IngredientUnitConversion> = emptyList(),
        ingredientId: IngredientId? = null,
    ): Outcome<DisplayQuantity> =
        when (val converted = convert(quantity, target, ingredientConversions, ingredientId)) {
            is Outcome.Failure -> converted
            is Outcome.Success ->
                Outcome.success(
                    DisplayQuantity(
                        amount = round(converted.value.amount, target),
                        unit = target,
                        isApproximate = quantity.unit.isApproximate || target.isApproximate,
                    ),
                )
        }

    private fun round(
        amount: Double,
        unit: Unit,
    ): Double = BigDecimal.valueOf(amount).setScale(unit.displayPrecision, RoundingMode.HALF_UP).toDouble()

    private fun scaled(
        quantity: Quantity,
        target: Unit,
        factor: Double,
        ingredientId: IngredientId?,
    ): Outcome<Quantity> {
        if (!factor.isFinite() || factor <= 0.0) {
            return invalidFactor(quantity, target, ingredientId)
        }
        return Outcome.success(Quantity(quantity.amount * factor, target))
    }

    private fun explicitFactor(
        source: Unit,
        target: Unit,
        conversions: List<IngredientUnitConversion>,
    ): Double? {
        val direct = conversions.firstOrNull { it.fromUnit == source.id && it.toUnit == target.id }
        if (direct != null) return direct.factor

        val reverse = conversions.firstOrNull { it.fromUnit == target.id && it.toUnit == source.id }
        return reverse?.let { 1.0 / it.factor }
    }

    private fun incompatible(
        source: Unit,
        target: Unit,
        ingredientId: IngredientId?,
    ): Outcome<Quantity> =
        Outcome.failure(
            CulinaryError.Domain(
                detail =
                    DomainDetail.UnitIncompatible(
                        fromUnit = source.code,
                        toUnit = target.code,
                        ingredientId = ingredientId?.value,
                    ),
                code = ErrorCode.UNIT_001,
            ),
        )

    private fun invalidFactor(
        quantity: Quantity,
        target: Unit,
        ingredientId: IngredientId?,
    ): Outcome<Quantity> =
        Outcome.failure(
            CulinaryError.Domain(
                detail =
                    DomainDetail.UnitIncompatible(
                        fromUnit = quantity.unit.code,
                        toUnit = target.code,
                        ingredientId = ingredientId?.value,
                    ),
                code = ErrorCode.UNIT_002,
            ),
        )
}
