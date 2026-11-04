package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.RecipeMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.RecipeComponentDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.RecipeVersionDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.RecipeComponentEntity
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.model.ComponentTypeDb
import com.culinary.core.database.model.VersionStatusDb
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * 菜谱 Repository 基础实现（阶段 2）。
 *
 * 只使用现有 Entity/DAO，不新增字段或表；`SAUCE` 组成行返回 `CUL-REP-004`。
 * 事务边界由 Use Case 控制，本类不自行开事务（E02 §4.3）。
 */
@Suppress("TooGenericExceptionCaught")
class RecipeRepositoryImpl
    @Inject
    constructor(
        private val recipeDao: RecipeDao,
        private val versionDao: RecipeVersionDao,
        private val componentDao: RecipeComponentDao,
        private val unitDao: UnitDao,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : RecipeRepository {
        override fun observeActive(): Flow<List<Recipe>> =
            flow {
                emit(recipeDao.listActive().map(RecipeMapper::toDomain))
            }

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? {
            val version = versionDao.findById(id.value) ?: return null
            val components = componentDao.listByVersion(id.value)
            val units = loadUnits(version.yieldUnitId, components.map { it.unitId }.toSet())
            return when (val mapped = RecipeMapper.toDomain(version, components, units)) {
                is Outcome.Success -> mapped.value
                is Outcome.Failure -> null
            }
        }

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId {
            val now = clock.now().toEpochMilli()
            versionDao.insert(toEntity(version, isCurrent = false, publishedAt = null, createdAt = now))
            insertComponents(version, now)
            return version.id
        }

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> =
            try {
                val now = clock.now().toEpochMilli()
                versionDao.findCurrent(version.recipeId.value)?.let {
                    versionDao.setCurrent(it.id, false)
                }
                versionDao.insert(toEntity(version, isCurrent = true, publishedAt = now, createdAt = now))
                insertComponents(version, now)
                Outcome.success(version.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        private fun toEntity(
            version: RecipeVersion,
            isCurrent: Boolean,
            publishedAt: Long?,
            createdAt: Long,
        ): RecipeVersionEntity =
            RecipeVersionEntity(
                id = version.id.value,
                recipeId = version.recipeId.value,
                versionNo = version.versionNo,
                status =
                    when (version.status) {
                        VersionStatus.DRAFT -> VersionStatusDb.DRAFT
                        VersionStatus.PUBLISHED -> VersionStatusDb.PUBLISHED
                        VersionStatus.SUPERSEDED -> VersionStatusDb.SUPERSEDED
                    },
                yieldQty = version.yield.amount,
                yieldUnitId = version.yield.unit.id.value,
                servings = version.servings,
                isCurrent = isCurrent,
                createdAt = createdAt,
                publishedAt = publishedAt,
            )

        private suspend fun insertComponents(
            version: RecipeVersion,
            createdAt: Long,
        ) {
            version.components.forEach { component ->
                componentDao.insert(toComponentEntity(version.id, component, createdAt))
            }
        }

        private fun toComponentEntity(
            versionId: RecipeVersionId,
            component: RecipeComponent,
            createdAt: Long,
        ): RecipeComponentEntity {
            val base =
                RecipeComponentEntity(
                    id = idGenerator.newId(),
                    recipeVersionId = versionId.value,
                    lineNo = component.lineNo,
                    componentType = ComponentTypeDb.INGREDIENT,
                    quantity = component.quantity.amount,
                    unitId = component.quantity.unit.id.value,
                    isOptional = component.isOptional,
                    createdAt = createdAt,
                )
            return when (val target = component.target) {
                is ComponentTarget.Ingredient ->
                    base.copy(
                        componentType = ComponentTypeDb.INGREDIENT,
                        ingredientId = target.id.value,
                    )

                is ComponentTarget.Recipe ->
                    base.copy(
                        componentType = ComponentTypeDb.RECIPE,
                        referencedRecipeId = target.recipeId.value,
                        referencedRecipeVersionId = target.versionId.value,
                    )
            }
        }

        private suspend fun loadUnits(
            yieldUnitId: String,
            componentUnitIds: Set<String>,
        ): Map<String, Unit> {
            val units = mutableMapOf<String, Unit>()
            (componentUnitIds + yieldUnitId).forEach { unitId ->
                unitDao.findById(unitId)?.let { units[unitId] = UnitMapper.toDomain(it) }
            }
            return units
        }
    }
