package com.culinary.core.domain.snapshot

import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.CalculationRecordStatus
import com.culinary.core.domain.model.RequirementLine
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Servings
import java.time.Instant

/** 计算引擎版本常量；变更算法时必须递增，旧记录继续以旧版本回放。 */
object CalculationEngine {
    const val VERSION: String = "stage2-1"
}

/**
 * 计算记录工厂（MB-D-017）。
 *
 * 先构造 `PREVIEW`；用户确认后由 [CalculationRecord.confirm] 转为 `CONFIRMED`。
 * `payload_hash` 在构造时一次算定，之后不再变化。
 */
class CalculationRecordFactory(
    private val hasher: CalculationPayloadHasher = CalculationPayloadHasher(),
) {
    fun preview(
        id: CalculationRecordId,
        recipeVersionId: RecipeVersionId,
        servings: Servings,
        lines: List<RequirementLine>,
        engineVersion: String = CalculationEngine.VERSION,
        createdAt: Instant,
    ): CalculationRecord =
        CalculationRecord(
            id = id,
            recipeVersionId = recipeVersionId,
            servings = servings,
            lines = lines,
            engineVersion = engineVersion,
            payloadHash = hasher.hash(engineVersion, recipeVersionId, servings, lines),
            status = CalculationRecordStatus.PREVIEW,
            createdAt = createdAt,
        )
}
