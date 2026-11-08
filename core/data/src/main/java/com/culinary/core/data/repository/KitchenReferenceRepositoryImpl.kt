package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.KitchenReferenceMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.KitchenReferenceDao
import com.culinary.core.database.model.KitchenReferenceKindDb
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.repository.KitchenReferenceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 新增厨房参考资料 Repository；只使用新表，不触碰既有档案表。 */
@Suppress("TooGenericExceptionCaught")
class KitchenReferenceRepositoryImpl
    @Inject
    constructor(
        private val dao: KitchenReferenceDao,
        private val clock: Clock,
    ) : KitchenReferenceRepository {
        override fun observeActive(kind: KitchenReferenceKind): Flow<List<KitchenReference>> =
            dao.observeActive(kind.toDb()).map { entities -> entities.map(KitchenReferenceMapper::toDomain) }

        override fun observeAllActive(): Flow<List<KitchenReference>> =
            dao.observeAllActive().map { entities -> entities.map(KitchenReferenceMapper::toDomain) }

        override suspend fun get(id: KitchenReferenceId): Outcome<KitchenReference> {
            val entity =
                dao.findById(id.value)
                    ?: return Outcome.failure(CulinaryError.NotFound(entity = "kitchen_reference", id = id.value))
            return Outcome.success(KitchenReferenceMapper.toDomain(entity))
        }

        override suspend fun save(reference: KitchenReference): Outcome<KitchenReferenceId> =
            try {
                val kind = reference.kind.toDb()
                val duplicate = dao.findActiveByNormalizedName(kind, reference.normalizedName)
                if (duplicate != null && duplicate.id != reference.id.value) {
                    Outcome.failure(CulinaryError.Conflict(entity = "kitchen_reference"))
                } else {
                    val now = clock.now().toEpochMilli()
                    val existing = dao.findById(reference.id.value)
                    if (existing == null) {
                        dao.insert(
                            KitchenReferenceMapper.toEntity(
                                reference = reference,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1,
                            ),
                        )
                    } else {
                        dao.update(
                            KitchenReferenceMapper.toEntity(
                                reference = reference,
                                createdAt = existing.createdAt,
                                updatedAt = now,
                                revision = existing.revision + 1,
                            ),
                        )
                    }
                    Outcome.success(reference.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        override suspend fun archive(id: KitchenReferenceId): Outcome<Unit> =
            try {
                val rows = dao.archive(id.value, clock.now().toEpochMilli())
                if (rows == 1) {
                    Outcome.success(Unit)
                } else {
                    Outcome.failure(CulinaryError.NotFound(entity = "kitchen_reference", id = id.value))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }
    }

private fun KitchenReferenceKind.toDb(): KitchenReferenceKindDb =
    when (this) {
        KitchenReferenceKind.TECHNIQUE -> KitchenReferenceKindDb.TECHNIQUE
        KitchenReferenceKind.PRODUCTION_STANDARD -> KitchenReferenceKindDb.PRODUCTION_STANDARD
        KitchenReferenceKind.STORAGE_STANDARD -> KitchenReferenceKindDb.STORAGE_STANDARD
    }
