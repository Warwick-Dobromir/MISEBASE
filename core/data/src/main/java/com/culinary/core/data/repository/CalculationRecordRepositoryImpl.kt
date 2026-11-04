package com.culinary.core.data.repository

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.data.mapper.CalculationRecordMapper
import com.culinary.core.data.mapper.UnitMapper
import com.culinary.core.data.transaction.DbErrorMapper
import com.culinary.core.database.dao.CalculationRecordDao
import com.culinary.core.database.dao.UnitDao
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.CalculationRecordRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * 计算记录 Repository 的 Room 实现（MB-SC-001）。
 *
 * 契约保持阶段 2 的定义：只接受 `CONFIRMED`，同 ID 不覆盖，同 `payload_hash`
 * 返回既有记录。写入头与需求行在 DAO 的单个事务内完成；确认后的不可变由
 * 领域模型、本类与数据库触发器三层保证。
 *
 * 本类只提供持久化，不接入任何计算记录业务 UI / Use Case 页面。
 */
@Suppress("TooGenericExceptionCaught") // 错误边界：把未知异常映射为 CUL-UNKNOWN-001
class CalculationRecordRepositoryImpl
    @Inject
    constructor(
        private val dao: CalculationRecordDao,
        private val unitDao: UnitDao,
        private val idGenerator: IdGenerator,
    ) : CalculationRecordRepository {
        override suspend fun saveConfirmed(record: CalculationRecord): Outcome<CalculationRecord> {
            if (!record.isConfirmed) {
                return Outcome.failure(
                    CulinaryError.Domain(
                        detail =
                            DomainDetail.InvalidState(
                                entity = "calculation_record",
                                expected = "CONFIRMED",
                                actual = record.status.name,
                            ),
                        code = ErrorCode.INV_003,
                    ),
                )
            }
            if (dao.findById(record.id.value) != null) {
                return Outcome.failure(CulinaryError.Conflict(entity = "calculation_record"))
            }
            dao.findByPayloadHash(record.payloadHash)?.let { existing ->
                return load(existing)
            }
            return try {
                val lineIds = record.lines.map { idGenerator.newId() }
                val inserted =
                    dao.insertConfirmed(
                        CalculationRecordMapper.toEntity(record),
                        CalculationRecordMapper.toLineEntities(record, lineIds),
                    )
                if (inserted) {
                    Outcome.success(record)
                } else {
                    val existing = dao.findByPayloadHash(record.payloadHash)
                    if (existing == null) {
                        Outcome.failure(CulinaryError.Conflict(entity = "calculation_record"))
                    } else {
                        load(existing)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                dao.findByPayloadHash(record.payloadHash)?.let { existing -> return load(existing) }
                Outcome.failure(DbErrorMapper.map(e))
            }
        }

        override suspend fun find(id: CalculationRecordId): Outcome<CalculationRecord?> {
            val entity = dao.findById(id.value) ?: return Outcome.success(null)
            return load(entity)
        }

        override suspend fun findByRecipeVersion(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>> {
            val entities = dao.findByRecipeVersion(recipeVersionId.value)
            val records = mutableListOf<CalculationRecord>()
            for (entity in entities) {
                when (val loaded = load(entity)) {
                    is Outcome.Failure -> return loaded
                    is Outcome.Success -> records += loaded.value
                }
            }
            return Outcome.success(records)
        }

        override suspend fun findByPayloadHash(payloadHash: String): Outcome<CalculationRecord?> {
            val entity = dao.findByPayloadHash(payloadHash) ?: return Outcome.success(null)
            return load(entity)
        }

        private suspend fun load(entity: CalculationRecordEntity): Outcome<CalculationRecord> {
            val lines: List<CalculationRecordLineEntity> = dao.findLines(entity.id)
            val units = loadUnits(lines)
            return CalculationRecordMapper.toDomain(entity, lines, units)
        }

        private suspend fun loadUnits(lines: List<CalculationRecordLineEntity>): Map<String, Unit> {
            val units = mutableMapOf<String, Unit>()
            lines.map { it.unitId }.toSet().forEach { unitId ->
                unitDao.findById(unitId)?.let { units[unitId] = UnitMapper.toDomain(it) }
            }
            return units
        }
    }
