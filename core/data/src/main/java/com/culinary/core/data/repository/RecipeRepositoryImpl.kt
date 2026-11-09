package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
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
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.RecipeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 菜谱 Repository 的 Room 实现（Stage 3）。
 *
 * 只使用现有 Entity/DAO，不新增字段或表；`SAUCE` 组成行映射随 Stage 3 打通。
 * 方法为原子片段；事务边界由 Use Case 控制，本类不自行开事务。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：把未知异常映射为 CUL-UNKNOWN-001
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
            recipeDao.observeActive().map { entities -> entities.map(RecipeMapper::toDomain) }

        override suspend fun get(id: RecipeId): Outcome<Recipe> {
            val entity =
                recipeDao.findById(id.value)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "recipe", id = id.value))
            return Outcome.success(RecipeMapper.toDomain(entity))
        }

        override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? {
            val version = versionDao.findById(id.value) ?: return null
            return (loadVersion(version) as? Outcome.Success)?.value
        }

        override suspend fun getCurrentVersion(recipeId: RecipeId): RecipeVersion? {
            val version = versionDao.findCurrent(recipeId.value) ?: return null
            return (loadVersion(version) as? Outcome.Success)?.value
        }

        override suspend fun listVersions(recipeId: RecipeId): List<RecipeVersion> =
            versionDao.listByRecipe(recipeId.value).mapNotNull { version ->
                (loadVersion(version) as? Outcome.Success)?.value
            }

        override suspend fun nextVersionNo(recipeId: RecipeId): Int = versionDao.maxVersionNo(recipeId.value) + 1

        override suspend fun save(recipe: Recipe): Outcome<RecipeId> =
            try {
                val now = clock.now().toEpochMilli()
                val existing = recipeDao.findById(recipe.id.value)
                if (existing == null) {
                    recipeDao.insert(
                        RecipeMapper.toEntity(recipe, createdAt = now, updatedAt = now, revision = 1),
                    )
                } else {
                    recipeDao.update(
                        RecipeMapper.toEntity(
                            recipe = recipe,
                            createdAt = existing.createdAt,
                            updatedAt = now,
                            revision = existing.revision + 1,
                        ),
                    )
                }
                Outcome.success(recipe.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId {
            val now = clock.now().toEpochMilli()
            val existing = versionDao.findById(version.id.value)
            if (existing == null) {
                versionDao.insert(
                    RecipeMapper.toVersionEntity(version, publishedAt = null, createdAt = now),
                )
            } else {
                // 同一份草稿重复保存时更新内容，不产生重复版本；已发布版本由用例与触发器共同保护。
                versionDao.updateDraft(
                    id = version.id.value,
                    yieldQty = version.yield.amount,
                    yieldUnitId = version.yield.unit.id.value,
                    minRangeQty = version.minRange?.amount,
                    minRangeUnitId = version.minRange?.unit?.id?.value,
                    servings = version.servings,
                    instructionsMd = version.instructions,
                    stepsJson = version.stepsJson,
                )
                componentDao.deleteByVersion(version.id.value)
            }
            insertComponents(version, now)
            return version.id
        }

        override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> =
            try {
                val now = clock.now().toEpochMilli()
                versionDao.clearCurrent(version.recipeId.value)
                val existing = versionDao.findById(version.id.value)
                if (existing == null) {
                    versionDao.insert(
                        RecipeMapper.toVersionEntity(
                            version = version.copy(status = VersionStatus.PUBLISHED),
                            publishedAt = now,
                            createdAt = now,
                        ),
                    )
                    insertComponents(version, now)
                } else {
                    // 已存在的草稿只允许状态/当前指针变化；内容以库内为准，不覆盖。
                    versionDao.setStatus(existing.id, VersionStatusDb.PUBLISHED, now)
                }
                versionDao.setCurrent(version.id.value, true)
                Outcome.success(version.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

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
                    sortOrder = component.lineNo,
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

                is ComponentTarget.Sauce ->
                    base.copy(
                        componentType = ComponentTypeDb.SAUCE,
                        referencedSauceId = target.sauceId.value,
                        referencedSauceVersionId = target.versionId.value,
                    )
            }
        }

        private suspend fun loadVersion(version: RecipeVersionEntity): Outcome<RecipeVersion> {
            val components = componentDao.listByVersion(version.id)
            val units =
                loadUnits(
                    yieldUnitId = version.yieldUnitId,
                    componentUnitIds = components.map { it.unitId }.toSet(),
                    minRangeUnitId = version.minRangeUnitId,
                )
            return RecipeMapper.toDomain(version, components, units)
        }

        private suspend fun loadUnits(
            yieldUnitId: String,
            componentUnitIds: Set<String>,
            minRangeUnitId: String?,
        ): Map<String, Unit> {
            val units = mutableMapOf<String, Unit>()
            (componentUnitIds + yieldUnitId + listOfNotNull(minRangeUnitId)).forEach { unitId ->
                unitDao.findById(unitId)?.let { units[unitId] = UnitMapper.toDomain(it) }
            }
            return units
        }
    }
