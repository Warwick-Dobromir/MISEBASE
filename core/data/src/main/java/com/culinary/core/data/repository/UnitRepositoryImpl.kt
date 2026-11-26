package com.culinary.core.data.repository

import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.database.dao.IngredientUnitConversionDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.UnitEntity
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.UnitRepository
import javax.inject.Inject

/** 单位仓储：单位维护直接写入既有 unit 表，不改变数据库结构。 */
class UnitRepositoryImpl
    @Inject
    constructor(
        private val unitDao: UnitDao,
        private val conversionDao: IngredientUnitConversionDao,
    ) : UnitRepository {
        override suspend fun all(): List<Unit> = unitDao.allActive().map(UnitMapper::toDomain)

        override suspend fun allIncludingInactive(): List<Unit> = unitDao.all().map(UnitMapper::toDomain)

        override suspend fun find(id: UnitId): Unit? = unitDao.findById(id.value)?.let(UnitMapper::toDomain)

        override suspend fun create(unit: Unit) {
            require(unitDao.findByCode(unit.code) == null) { "单位编码已存在" }
            unitDao.insert(unit.toEntity(isActive = true, sortOrder = unitDao.count()))
        }

        override suspend fun update(unit: Unit) {
            val existing = unitDao.findById(unit.id.value) ?: return
            unitDao.update(
                existing.copy(
                    name = unit.name,
                    symbol = unit.symbol,
                    dimension = unit.dimension.toDb(),
                    baseFactor = unit.baseFactor,
                    isBase = unit.isBase,
                    isApproximate = unit.isApproximate,
                    displayPrecision = unit.displayPrecision,
                ),
            )
        }

        override suspend fun setActive(id: UnitId, active: Boolean) {
            unitDao.setActive(id.value, active)
        }

        override suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion> =
            conversionDao.listByIngredient(ingredientId.value).map(UnitMapper::toDomain)

        private fun Unit.toEntity(isActive: Boolean, sortOrder: Int) =
            UnitEntity(
                id = id.value,
                code = code,
                name = name,
                symbol = symbol,
                dimension = dimension.toDb(),
                baseFactor = baseFactor,
                isBase = isBase,
                isApproximate = isApproximate,
                displayPrecision = displayPrecision,
                isActive = isActive,
                sortOrder = sortOrder,
                createdAt = System.currentTimeMillis(),
            )

        private fun UnitDimension.toDb(): UnitDimensionDb =
            when (this) {
                UnitDimension.MASS -> UnitDimensionDb.MASS
                UnitDimension.VOLUME -> UnitDimensionDb.VOLUME
                UnitDimension.COUNT -> UnitDimensionDb.COUNT
            }
    }
