package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.data.mapper.ArchiveCategoryMapper
import com.culinary.core.database.dao.ArchiveCategoryAssignmentDao
import com.culinary.core.database.dao.ArchiveCategoryDao
import com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity
import com.culinary.core.database.entity.ArchiveCategoryEntity
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 分类树与 assignment 的 Room 实现；分类由迁移/测试 seed 写入，不在编辑器中创建。 */
class ArchiveCategoryRepositoryImpl
    @Inject
    constructor(
        private val categoryDao: ArchiveCategoryDao,
        private val assignmentDao: ArchiveCategoryAssignmentDao,
        private val transactionRunner: TransactionRunner,
        private val clock: Clock,
        private val idGenerator: IdGenerator,
    ) : ArchiveCategoryRepository {
        override fun observeActive(): Flow<List<ArchiveCategory>> =
            categoryDao.observeAllActive().map { entities -> buildPaths(entities) }

        override fun observeAssignments(): Flow<List<ArchiveCategoryAssignment>> =
            assignmentDao.observeAll().map { entities -> entities.map(ArchiveCategoryMapper::assignmentToDomain) }

        override suspend fun get(id: String): ArchiveCategory? {
            val all = categoryDao.listAll()
            val entity = all.firstOrNull { it.id == id } ?: return null
            return buildPaths(all).firstOrNull { it.id == id }
        }

        override suspend fun getForRecord(
            kind: KitchenRecordKind,
            recordId: String,
        ): ArchiveCategory? {
            val assignment = assignmentDao.findByRecord(kind.name, recordId) ?: return null
            return get(assignment.categoryId)
        }

        override suspend fun assign(
            kind: KitchenRecordKind,
            recordId: String,
            categoryId: String,
        ): Outcome<Unit> {
            val result =
                transactionRunner.write<Outcome<Unit>> {
                    val category =
                        categoryDao.findById(categoryId)
                            ?: return@write Outcome.failure(
                                CulinaryError.NotFound(entity = "archive_category", id = categoryId),
                            )
                    val expected = libraryOf(kind)
                    if (category.library != expected.name) {
                        return@write Outcome.failure(
                            CulinaryError.Validation(
                                field = "archive_category.library",
                                rule = "category ${category.library} does not match $expected",
                            ),
                        )
                    }
                    if (category.status != com.culinary.core.database.model.EntityStatusDb.ACTIVE) {
                        return@write Outcome.failure(
                            CulinaryError.Validation(
                                field = "archive_category.status",
                                rule = "category is not active",
                            ),
                        )
                    }
                    val now = clock.now().toEpochMilli()
                    val existing = assignmentDao.findByRecord(kind.name, recordId)
                    if (existing == null) {
                        assignmentDao.insert(
                            ArchiveCategoryAssignmentEntity(
                                id = idGenerator.newId(),
                                recordKind = kind.name,
                                recordId = recordId,
                                categoryId = categoryId,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    } else {
                        assignmentDao.update(existing.copy(categoryId = categoryId, updatedAt = now))
                    }
                    Outcome.success(Unit)
                }
            return result.flatMap { it }
        }

        override suspend fun clearTestBatch(testBatch: String): Outcome<Int> {
            val result =
                transactionRunner.write<Outcome<Int>> {
                    val categories = categoryDao.listByTestBatch(testBatch)
                    if (categories.isEmpty()) return@write Outcome.success(0)
                    val ids = categories.map { it.id }
                    ids.forEach { categoryId -> assignmentDao.deleteByCategory(categoryId) }
                    val deleted = categoryDao.deleteTestBatch(testBatch)
                    Outcome.success(deleted)
                }
            return result.flatMap { it }
        }

        override suspend fun libraryOf(kind: KitchenRecordKind): ArchiveLibrary =
            when (kind) {
                KitchenRecordKind.SAUCE -> ArchiveLibrary.SAUCE
                KitchenRecordKind.INGREDIENT -> ArchiveLibrary.INGREDIENT
                KitchenRecordKind.RECIPE -> ArchiveLibrary.RECIPE
                KitchenRecordKind.TECHNIQUE -> ArchiveLibrary.TECHNIQUE
                KitchenRecordKind.PRODUCTION_STANDARD -> ArchiveLibrary.PRODUCTION_STANDARD
                KitchenRecordKind.STORAGE_STANDARD -> ArchiveLibrary.STORAGE_STANDARD
            }

        private fun buildPaths(entities: List<ArchiveCategoryEntity>): List<ArchiveCategory> {
            val byId = entities.associateBy { it.id }
            val result = mutableListOf<ArchiveCategory>()
            entities.forEach { entity ->
                val path = mutableListOf<String>()
                var cursor: ArchiveCategoryEntity? = entity
                var guard = 0
                while (cursor != null && guard++ < 32) {
                    path.add(0, cursor.name)
                    cursor = cursor.parentId?.let(byId::get)
                }
                if (entity.status == com.culinary.core.database.model.EntityStatusDb.ACTIVE) {
                    result += ArchiveCategoryMapper.toDomain(entity, path, path.size)
                }
            }
            return result
        }
    }
