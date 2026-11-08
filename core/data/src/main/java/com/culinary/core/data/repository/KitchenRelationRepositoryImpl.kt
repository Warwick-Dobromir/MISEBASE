package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.KitchenRelationMapper
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.KitchenReferenceDao
import com.culinary.core.database.dao.KitchenRelationDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.entity.KitchenRelationEntity
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.KitchenRelationDraft
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 关系读取实现；目标名称按当前档案解析，避免改名后关系标签陈旧。 */
class KitchenRelationRepositoryImpl
    @Inject
    constructor(
        private val dao: KitchenRelationDao,
        private val sauceDao: SauceDao,
        private val ingredientDao: IngredientDao,
        private val recipeDao: RecipeDao,
        private val referenceDao: KitchenReferenceDao,
        private val transactionRunner: TransactionRunner,
        private val idGenerator: IdGenerator,
        private val clock: Clock,
    ) : KitchenRelationRepository {
        override fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>> =
            dao.observeBySource(recordId).map { entities ->
                entities.mapNotNull { entity -> resolve(entity) }
            }

        override suspend fun replaceRelations(
            sourceRecordId: String,
            sourceKind: KitchenRecordKind,
            targetKind: KitchenRecordKind,
            relations: List<KitchenRelationDraft>,
        ): Outcome<Unit> =
            transactionRunner.write {
                dao.deleteBySourceAndTargetKind(sourceRecordId, targetKind.name)
                val now = clock.now().toEpochMilli()
                dao.insertAll(
                    relations
                        .distinctBy { it.targetRecordId }
                        .mapIndexed { index, relation ->
                            KitchenRelationEntity(
                                id = idGenerator.newId(),
                                sourceRecordId = sourceRecordId,
                                sourceKind = sourceKind.name,
                                targetRecordId = relation.targetRecordId,
                                targetKind = targetKind.name,
                                targetLabel = relation.targetLabel,
                                relationLabel = relation.relationLabel,
                                sortOrder = index,
                                seedVersion = null,
                                createdAt = now,
                            )
                        },
                )
                Outcome.success(Unit)
            }

        private suspend fun resolve(entity: KitchenRelationEntity): KitchenRelatedRecord? {
            val base = KitchenRelationMapper.toDomain(entity) ?: return null
            val currentLabel =
                when (base.targetKind) {
                    KitchenRecordKind.SAUCE -> sauceDao.findById(base.targetRecordId)?.name
                    KitchenRecordKind.INGREDIENT -> ingredientDao.findById(base.targetRecordId)?.name
                    KitchenRecordKind.RECIPE -> recipeDao.findById(base.targetRecordId)?.name
                    KitchenRecordKind.TECHNIQUE,
                    KitchenRecordKind.PRODUCTION_STANDARD,
                    KitchenRecordKind.STORAGE_STANDARD,
                    -> referenceDao.findById(base.targetRecordId)?.name
                }
            return base.copy(targetLabel = currentLabel ?: base.targetLabel)
        }
    }
