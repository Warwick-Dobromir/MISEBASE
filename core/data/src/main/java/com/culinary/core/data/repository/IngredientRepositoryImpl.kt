package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.IngredientMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.IngredientVersionDao
import com.culinary.core.database.entity.IngredientVersionEntity
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientVersion
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
        private val versionDao: IngredientVersionDao,
        private val clock: Clock,
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
                val savedEntity =
                    if (existing == null) {
                        IngredientMapper.toEntity(ingredient, createdAt = now, updatedAt = now, revision = 1)
                            .also { dao.insert(it) }
                    } else {
                        IngredientMapper.toEntity(
                            ingredient = ingredient,
                            createdAt = existing.createdAt,
                            updatedAt = now,
                            revision = existing.revision + 1,
                        ).also { dao.update(it) }
                    }
                val versionNo = versionDao.maxVersionNo(ingredient.id.value) + 1
                versionDao.insert(
                    IngredientVersionEntity(
                        id = "${ingredient.id.value}-v${versionNo.toString().padStart(2, '0')}",
                        ingredientId = ingredient.id.value,
                        versionNo = versionNo,
                        name = savedEntity.name,
                        normalizedName = savedEntity.normalizedName,
                        scientificName = savedEntity.scientificName,
                        category = savedEntity.category,
                        description = savedEntity.description,
                        flavorProfile = savedEntity.flavorProfile,
                        culinaryUse = savedEntity.culinaryUse,
                        edibleParts = savedEntity.edibleParts,
                        seasonality = savedEntity.seasonality,
                        origin = savedEntity.origin,
                        selectionNotes = savedEntity.selectionNotes,
                        storageNotes = savedEntity.storageNotes,
                        prepNotes = savedEntity.prepNotes,
                        allergenNotes = savedEntity.allergenNotes,
                        defaultUnitId = savedEntity.defaultUnitId,
                        englishName = savedEntity.englishName,
                        exampleProducts = savedEntity.exampleProducts,
                        seedNote = savedEntity.seedNote,
                        seedId = savedEntity.seedId,
                        seedVersion = savedEntity.seedVersion,
                        ingredientStatus = savedEntity.status.name,
                        sourceCreatedAt = savedEntity.createdAt,
                        sourceUpdatedAt = savedEntity.updatedAt,
                        sourceDeletedAt = savedEntity.deletedAt,
                        sourceRevision = savedEntity.revision,
                        status = "PUBLISHED",
                        createdAt = now,
                    ),
                )
                Outcome.success(ingredient.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun listVersions(id: IngredientId): List<IngredientVersion> =
            versionDao.listByIngredient(id.value).map { version ->
                IngredientVersion(
                    id = version.id,
                    ingredientId = IngredientId(version.ingredientId),
                    versionNo = version.versionNo,
                    status = version.status,
                    createdAt = version.createdAt,
                )
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
