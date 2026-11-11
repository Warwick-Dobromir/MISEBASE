package com.culinary.core.data.repository

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.common.flatMap
import com.culinary.core.data.mapper.ArchiveCategoryMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.ArchiveCategoryAssignmentDao
import com.culinary.core.database.dao.ArchiveCategoryDao
import com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity
import com.culinary.core.database.entity.ArchiveCategoryEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.rule.common.NameText
import com.culinary.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 分类树与 assignment 的 Room 实现；库存分类（v9）支持同库内的增删改与排序。 */
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
                    if (category.status != EntityStatusDb.ACTIVE) {
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

        override suspend fun listByLibrary(library: ArchiveLibrary): Outcome<List<ArchiveCategory>> =
            read { buildPaths(categoryDao.listAll()).filter { it.library == library } }

        override suspend fun createCategory(
            library: ArchiveLibrary,
            parentId: String?,
            name: String,
            englishName: String?,
        ): Outcome<ArchiveCategory> =
            write {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    return@write invalid("name", "must not be blank")
                }
                val all = categoryDao.listAll()
                if (parentId != null) {
                    val parent =
                        all.firstOrNull { it.id == parentId }
                            ?: return@write missing(parentId)
                    if (parent.library != library.name) {
                        return@write invalid("parentId", "parent category does not belong to ${library.name}")
                    }
                }
                val normalizedName = NameText.normalize(trimmed)
                val duplicate = siblingOf(all, library.name, parentId, normalizedName, excludeId = null)
                if (duplicate != null) {
                    return@write Outcome.failure(CulinaryError.Conflict(entity = "archive_category"))
                }
                val now = clock.now().toEpochMilli()
                val entity =
                    ArchiveCategoryEntity(
                        id = idGenerator.newId(),
                        library = library.name,
                        parentId = parentId,
                        name = trimmed,
                        normalizedName = normalizedName,
                        englishName = englishName?.trim()?.takeIf { it.isNotEmpty() },
                        sortOrder = nextSortOrder(all, library.name, parentId, excludeId = null),
                        status = EntityStatusDb.ACTIVE,
                        testBatch = null,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1,
                    )
                categoryDao.insert(entity)
                Outcome.success(withPath(all + entity, entity.id))
            }

        override suspend fun renameCategory(
            id: String,
            name: String,
            englishName: String?,
        ): Outcome<ArchiveCategory> =
            write {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    return@write invalid("name", "must not be blank")
                }
                val all = categoryDao.listAll()
                val entity =
                    all.firstOrNull { it.id == id }
                        ?: return@write missing(id)
                val normalizedName = NameText.normalize(trimmed)
                val duplicate = siblingOf(all, entity.library, entity.parentId, normalizedName, excludeId = id)
                if (duplicate != null) {
                    return@write Outcome.failure(CulinaryError.Conflict(entity = "archive_category"))
                }
                val updated =
                    entity.copy(
                        name = trimmed,
                        normalizedName = normalizedName,
                        englishName = englishName?.trim()?.takeIf { it.isNotEmpty() } ?: entity.englishName,
                        updatedAt = clock.now().toEpochMilli(),
                        revision = entity.revision + 1,
                    )
                categoryDao.update(updated)
                Outcome.success(withPath(all.map { if (it.id == id) updated else it }, id))
            }

        override suspend fun moveCategory(id: String, newParentId: String?): Outcome<ArchiveCategory> =
            write {
                val all = categoryDao.listAll()
                val entity =
                    all.firstOrNull { it.id == id }
                        ?: return@write missing(id)
                if (newParentId != null) {
                    if (newParentId == id) {
                        return@write invalid("newParentId", "category cannot be moved under itself")
                    }
                    val parent =
                        all.firstOrNull { it.id == newParentId }
                            ?: return@write missing(newParentId)
                    if (parent.library != entity.library) {
                        return@write invalid("newParentId", "target parent belongs to another library")
                    }
                    if (isDescendant(all, rootId = id, candidateId = newParentId)) {
                        return@write invalid("newParentId", "category cannot be moved under its descendant")
                    }
                }
                val duplicate = siblingOf(all, entity.library, newParentId, entity.normalizedName, excludeId = id)
                if (duplicate != null) {
                    return@write Outcome.failure(CulinaryError.Conflict(entity = "archive_category"))
                }
                val updated =
                    entity.copy(
                        parentId = newParentId,
                        sortOrder = nextSortOrder(all, entity.library, newParentId, excludeId = id),
                        updatedAt = clock.now().toEpochMilli(),
                        revision = entity.revision + 1,
                    )
                categoryDao.update(updated)
                Outcome.success(withPath(all.map { if (it.id == id) updated else it }, id))
            }

        override suspend fun reorderCategories(orderedIds: List<String>): Outcome<Unit> =
            write {
                val now = clock.now().toEpochMilli()
                orderedIds.forEachIndexed { index, categoryId ->
                    categoryDao.updateSortOrder(id = categoryId, sortOrder = index, updatedAt = now)
                }
                Outcome.success(Unit)
            }

        override suspend fun deleteCategory(id: String): Outcome<Unit> =
            write {
                val all = categoryDao.listAll()
                all.firstOrNull { it.id == id } ?: return@write missing(id)
                if (all.any { it.parentId == id }) {
                    return@write invalid("id", "category still has child categories")
                }
                categoryDao.deleteById(id)
                Outcome.success(Unit)
            }

        /** 同 library 同父级下重名的节点；`excludeId` 用于重命名/移动时忽略自身。 */
        private fun siblingOf(
            all: List<ArchiveCategoryEntity>,
            library: String,
            parentId: String?,
            normalizedName: String,
            excludeId: String?,
        ): ArchiveCategoryEntity? =
            all.firstOrNull {
                it.id != excludeId &&
                    it.library == library &&
                    it.parentId == parentId &&
                    it.normalizedName == normalizedName
            }

        /** 同级下一个 `sort_order`：最大兄弟值 + 1，没有兄弟时从 0 开始。 */
        private fun nextSortOrder(
            all: List<ArchiveCategoryEntity>,
            library: String,
            parentId: String?,
            excludeId: String?,
        ): Int =
            all.filter { it.id != excludeId && it.library == library && it.parentId == parentId }
                .maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0

        /** `candidateId` 是否为 `rootId` 的后代；用于禁止把分类移动到自己的子树里。 */
        private fun isDescendant(
            all: List<ArchiveCategoryEntity>,
            rootId: String,
            candidateId: String,
        ): Boolean {
            val byId = all.associateBy { it.id }
            var cursor = byId[candidateId]
            var guard = 0
            while (cursor != null && guard++ < MAX_DEPTH) {
                if (cursor.parentId == rootId) return true
                cursor = cursor.parentId?.let(byId::get)
            }
            return false
        }

        private fun withPath(
            entities: List<ArchiveCategoryEntity>,
            id: String,
        ): ArchiveCategory =
            buildPaths(entities).firstOrNull { it.id == id }
                ?: throw NoSuchElementException("missing archive_category: $id")

        private fun missing(id: String): Outcome<Nothing> =
            Outcome.failure(CulinaryError.NotFound(entity = "archive_category", id = id))

        private fun invalid(field: String, rule: String): Outcome<Nothing> =
            Outcome.failure(CulinaryError.Validation(field = field, rule = rule))

        private suspend fun <T> read(block: suspend () -> T): Outcome<T> =
            try {
                Outcome.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.failure(DbErrorMapper.map(e))
            }

        private suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> =
            transactionRunner.write<Outcome<T>> { block() }.flatMap { it }

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
                if (entity.status == EntityStatusDb.ACTIVE) {
                    result += ArchiveCategoryMapper.toDomain(entity, path, path.size)
                }
            }
            return result
        }

        private companion object {
            const val MAX_DEPTH = 64
        }
    }
