package com.culinary.core.domain.usecase.ingredient

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.IngredientDetail
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.UnitRepository

/** 读取食材详情：档案、默认单位与换算依据。 */
class GetIngredientDetailUseCase(
    private val ingredientRepository: IngredientRepository,
    private val unitRepository: UnitRepository,
) {
    suspend operator fun invoke(id: IngredientId): Outcome<IngredientDetail> {
        val ingredient =
            when (val result = ingredientRepository.get(id)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val units = unitRepository.all().associateBy { it.id }
        val conversions =
            unitRepository.conversionsFor(id).map { conversion ->
                IngredientConversionView(
                    fromUnitId = conversion.fromUnit,
                    toUnitId = conversion.toUnit,
                    fromUnit = units[conversion.fromUnit],
                    toUnit = units[conversion.toUnit],
                    factor = conversion.factor,
                    note = conversion.note,
                )
            }
        return Outcome.success(
            IngredientDetail(
                ingredient = ingredient,
                defaultUnit = ingredient.defaultUnitId?.let { units[it] },
                conversions = conversions,
            ),
        )
    }
}
