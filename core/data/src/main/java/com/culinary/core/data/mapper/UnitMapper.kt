package com.culinary.core.data.mapper

import com.culinary.core.database.entity.IngredientUnitConversionEntity
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId

/** 单位与食材级换算的 Entity ↔ 领域模型映射。 */
object UnitMapper {
    fun toDomain(entity: UnitEntity): Unit =
        Unit(
            id = UnitId(entity.id),
            code = entity.code,
            name = entity.name,
            symbol = entity.symbol,
            dimension =
                when (entity.dimension) {
                    UnitDimensionDb.MASS -> UnitDimension.MASS
                    UnitDimensionDb.VOLUME -> UnitDimension.VOLUME
                    UnitDimensionDb.COUNT -> UnitDimension.COUNT
                },
            baseFactor = entity.baseFactor,
            isBase = entity.isBase,
            isApproximate = entity.isApproximate,
            displayPrecision = entity.displayPrecision,
        )

    fun toDomain(entity: IngredientUnitConversionEntity): IngredientUnitConversion =
        IngredientUnitConversion(
            fromUnit = UnitId(entity.fromUnitId),
            toUnit = UnitId(entity.toUnitId),
            factor = entity.factor,
            note = entity.note,
        )
}
