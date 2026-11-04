package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.SauceMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.SauceComponentDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.dao.SauceVersionDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.SauceVersionEntity
import com.culinary.core.database.model.VersionStatusDb
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.SauceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 酱汁 Repository 的 Room 实现（Stage 3）。
 *
 * 只使用既有 `sauce` / `sauce_version` / `sauce_component` 表，不新增字段或迁移。
 * 方法为原子片段；事务边界由 Use Case 通过 `TransactionRunner` 控制。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：把未知异常映射为 CUL-UNKNOWN-001
class SauceRepositoryImpl
    @Inject
    constructor(
        private val sauceDao: SauceDao,
        private val versionDao: SauceVersionDao,
        private val componentDao: SauceComponentDao,
        private val unitDao: UnitDao,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : SauceRepository {
        override fun observeActive(): Flow<List<Sauce>> =
            sauceDao.observeActive().map { entities -> entities.map(SauceMapper::toDomain) }

        override suspend fun get(id: SauceId): Outcome<Sauce> {
            val entity =
                sauceDao.findById(id.value)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "sauce", id = id.value))
            return Outcome.success(SauceMapper.toDomain(entity))
        }

        override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> {
            val entity =
                versionDao.findById(id.value)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "sauce_version", id = id.value))
            return loadVersion(entity)
        }

        override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> =
            versionDao.listBySauce(sauceId.value).mapNotNull { entity ->
                (loadVersion(entity) as? Outcome.Success)?.value
            }

        override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? {
            val entity = versionDao.findCurrent(sauceId.value) ?: return null
            return (loadVersion(entity) as? Outcome.Success)?.value
        }

        override suspend fun nextVersionNo(sauceId: SauceId): Int = versionDao.maxVersionNo(sauceId.value) + 1

        override suspend fun save(sauce: Sauce): Outcome<SauceId> =
            try {
                val now = clock.now().toEpochMilli()
                val existing = sauceDao.findById(sauce.id.value)
                if (existing == null) {
                    sauceDao.insert(
                        SauceMapper.toEntity(sauce, createdAt = now, updatedAt = now, revision = 1),
                    )
                } else {
                    sauceDao.update(
                        SauceMapper.toEntity(
                            sauce = sauce,
                            createdAt = existing.createdAt,
                            updatedAt = now,
                            revision = existing.revision + 1,
                        ),
                    )
                }
                Outcome.success(sauce.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> =
            try {
                val now = clock.now().toEpochMilli()
                versionDao.insert(
                    SauceMapper.toVersionEntity(version, publishedAt = null, createdAt = now),
                )
                componentDao.insertAll(
                    version.components.map { component ->
                        SauceMapper.toComponentEntity(
                            versionId = version.id,
                            component = component,
                            id = idGenerator.newId(),
                            createdAt = now,
                        )
                    },
                )
                Outcome.success(version.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> =
            try {
                val now = clock.now().toEpochMilli()
                versionDao.clearCurrent(version.sauceId.value)
                val existing = versionDao.findById(version.id.value)
                if (existing == null) {
                    versionDao.insert(
                        SauceMapper.toVersionEntity(
                            version = version.copy(status = VersionStatus.PUBLISHED),
                            publishedAt = now,
                            createdAt = now,
                        ),
                    )
                    componentDao.insertAll(
                        version.components.map { component ->
                            SauceMapper.toComponentEntity(
                                versionId = version.id,
                                component = component,
                                id = idGenerator.newId(),
                                createdAt = now,
                            )
                        },
                    )
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

        private suspend fun loadVersion(entity: SauceVersionEntity): Outcome<SauceVersion> {
            val components = componentDao.listByVersion(entity.id)
            val units = loadUnits(entity.yieldUnitId, components.map { it.unitId }.toSet())
            return SauceMapper.toDomain(entity, components, units)
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
