package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.IngredientMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.ChangeLogDao
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.IngredientUnitConversionDao
import com.culinary.core.database.entity.IngredientUnitConversionEntity
import com.culinary.core.database.entity.ChangeLogEntity
import com.culinary.core.database.model.ChangeActionDb
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientChangeRecord
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.repository.IngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 食材 Repository 实现（阶段 4）。
 *
 * 方法本身是原子操作片段；事务边界由 Use Case 通过 `TransactionRunner` 控制，
 * 这里不自行开事务（E02 §4.3）。错误统一映射为 [CulinaryError]。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：E03 §5 要求把未知异常映射为 CUL-UNKNOWN-001
class IngredientRepositoryImpl
    @Inject
    constructor(
        private val dao: IngredientDao,
        private val conversionDao: IngredientUnitConversionDao,
        private val changeLogDao: ChangeLogDao,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : IngredientRepository {
        override fun observeActive(): Flow<List<Ingredient>> =
            dao.observeActive().map { entities -> entities.map(IngredientMapper::toDomain) }

        override suspend fun get(id: IngredientId): Outcome<Ingredient> {
            val entity =
                dao.findById(id.value)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "ingredient", id = id.value))
            return Outcome.success(IngredientMapper.toDomain(entity))
        }

        override suspend fun save(ingredient: Ingredient): Outcome<IngredientId> =
            try {
                val now = clock.now().toEpochMilli()
                val existing = dao.findById(ingredient.id.value)
                val previous = existing?.let(IngredientMapper::toDomain)
                if (previous == ingredient) return Outcome.success(ingredient.id)
                if (existing == null) {
                    dao.insert(IngredientMapper.toEntity(ingredient, createdAt = now, updatedAt = now, revision = 1))
                } else {
                    dao.update(
                        IngredientMapper.toEntity(
                            ingredient = ingredient,
                            createdAt = existing.createdAt,
                            updatedAt = now,
                            revision = existing.revision + 1,
                        ),
                    )
                }
                changeLogDao.insert(
                    ChangeLogEntity(
                        id = idGenerator.newId(),
                        entityType = "INGREDIENT",
                        entityId = ingredient.id.value,
                        action = if (existing == null) ChangeActionDb.CREATE else ChangeActionDb.UPDATE,
                        createdAt = now,
                    ),
                )
                Outcome.success(ingredient.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun saveWithConversions(
            ingredient: Ingredient,
            conversions: List<IngredientUnitConversion>,
        ): Outcome<IngredientId> =
            try {
                val existingIngredient = dao.findById(ingredient.id.value)
                val ingredientChanged = existingIngredient?.let(IngredientMapper::toDomain) != ingredient
                val existingConversions = conversionDao.listByIngredient(ingredient.id.value)
                val current = existingConversions.map {
                    listOf(it.fromUnitId, it.toUnitId, it.factor.toString(), it.note.orEmpty()).joinToString("|")
                }.sorted()
                val requested = conversions.map {
                    listOf(it.fromUnit.value, it.toUnit.value, it.factor.toString(), it.note.orEmpty()).joinToString("|")
                }.sorted()
                when (val saved = save(ingredient)) {
                    is Outcome.Failure -> return saved
                    is Outcome.Success -> Unit
                }
                if (current != requested) {
                    conversionDao.deleteByIngredient(ingredient.id.value)
                    val now = clock.now().toEpochMilli()
                    conversions.forEach { conversion ->
                        conversionDao.insert(
                            IngredientUnitConversionEntity(
                                id = idGenerator.newId(),
                                ingredientId = ingredient.id.value,
                                fromUnitId = conversion.fromUnit.value,
                                toUnitId = conversion.toUnit.value,
                                factor = conversion.factor,
                                note = conversion.note,
                                createdAt = now,
                            ),
                        )
                    }
                    if (!ingredientChanged) {
                        changeLogDao.insert(
                            ChangeLogEntity(
                                id = idGenerator.newId(),
                                entityType = "INGREDIENT",
                                entityId = ingredient.id.value,
                                action = ChangeActionDb.UPDATE,
                                createdAt = now,
                            ),
                        )
                    }
                }
                Outcome.success(ingredient.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
        override suspend fun changeHistory(id: IngredientId): List<IngredientChangeRecord> =
            changeLogDao.listByEntity("INGREDIENT", id.value).map { entry ->
                IngredientChangeRecord(action = entry.action.name, createdAt = entry.createdAt)
            }
        override suspend fun archive(id: IngredientId): Outcome<Unit> =
            try {
                val rows = dao.archive(id.value, clock.now().toEpochMilli())
                if (rows == 1) {
                    Outcome.success(Unit)
                } else {
                    Outcome.failure(CulinaryError.NotFound(entity = "ingredient", id = id.value))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
    }
