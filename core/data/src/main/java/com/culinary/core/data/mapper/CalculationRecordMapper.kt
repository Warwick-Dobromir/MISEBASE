package com.culinary.core.data.mapper

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.database.entity.CalculationRecordEntity
import com.culinary.core.database.entity.CalculationRecordLineEntity
import com.culinary.core.database.model.CalculationRecordStatusDb
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.Servings
import com.culinary.core.domain.model.Unit
import java.time.Instant

/**
 * 计算记录 Entity ↔ 领域模型（MB-D-017）。
 *
 * 计算记录是只读历史：`CONFIRMED` 的时间字段必须存在；行按 `line_no` 稳定排序。
 */
object CalculationRecordMapper {
    fun toEntity(record: CalculationRecord): CalculationRecordEntity =
        CalculationRecordEntity(
            id = record.id.value,
            recipeVersionId = record.recipeVersionId.value,
            servings = record.servings.count,
            engineVersion = record.engineVersion,
            payloadHash = record.payloadHash,
            status = toDb(record.status),
            createdAt = record.createdAt.toEpochMilli(),
            confirmedAt = record.confirmedAt?.toEpochMilli(),
        )

    fun toLineEntities(
        record: CalculationRecord,
        lineIds: List<String>,
    ): List<CalculationRecordLineEntity> {
        require(lineIds.size == record.lines.size) { "line id count must match requirement lines" }
        return record.lines.mapIndexed { index, line ->
            CalculationRecordLineEntity(
                id = lineIds[index],
                calculationRecordId = record.id.value,
                lineNo = index + 1,
                ingredientId = line.ingredientId.value,
                quantity = line.quantity.amount,
                unitId = line.quantity.unit.id.value,
                isOptional = line.isOptional,
            )
        }
    }

    fun toDomain(
        entity: CalculationRecordEntity,
        lines: List<CalculationRecordLineEntity>,
        units: Map<String, Unit>,
    ): Outcome<CalculationRecord> {
        val mapped = mutableListOf<RequirementLine>()
        for (line in lines) {
            val unit =
                units[line.unitId]
                    ?: return Outcome.failure(
                        CulinaryError.NotFound(entity = "unit", id = line.unitId),
                    )
            mapped +=
                RequirementLine(
                    ingredientId = IngredientId(line.ingredientId),
                    quantity = Quantity(line.quantity, unit),
                    isOptional = line.isOptional,
                )
        }
        return Outcome.success(
            CalculationRecord(
                id = CalculationRecordId(entity.id),
                recipeVersionId = RecipeVersionId(entity.recipeVersionId),
                servings = Servings(entity.servings),
                lines = mapped,
                engineVersion = entity.engineVersion,
                payloadHash = entity.payloadHash,
                status = toDomain(entity.status),
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                confirmedAt = entity.confirmedAt?.let(Instant::ofEpochMilli),
            ),
        )
    }

    private fun toDb(status: CalculationRecordStatus): CalculationRecordStatusDb =
        when (status) {
            CalculationRecordStatus.PREVIEW -> CalculationRecordStatusDb.PREVIEW
            CalculationRecordStatus.CONFIRMED -> CalculationRecordStatusDb.CONFIRMED
        }

    private fun toDomain(status: CalculationRecordStatusDb): CalculationRecordStatus =
        when (status) {
            CalculationRecordStatusDb.PREVIEW -> CalculationRecordStatus.PREVIEW
            CalculationRecordStatusDb.CONFIRMED -> CalculationRecordStatus.CONFIRMED
        }
}
