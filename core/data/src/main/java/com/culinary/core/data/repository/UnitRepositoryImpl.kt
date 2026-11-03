package com.culinary.core.data.repository

import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.database.dao.IngredientUnitConversionDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.UnitRepository
import javax.inject.Inject

/** 单位与食材级换算的只读实现。 */
class UnitRepositoryImpl
    @Inject
    constructor(
        private val unitDao: UnitDao,
        private val conversionDao: IngredientUnitConversionDao,
    ) : UnitRepository {
        override suspend fun all(): List<Unit> = unitDao.allActive().map(UnitMapper::toDomain)

        override suspend fun find(id: UnitId): Unit? = unitDao.findById(id.value)?.let(UnitMapper::toDomain)

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> =
            conversionDao.listByIngredient(ingredientId.value).map(UnitMapper::toDomain)
    }
