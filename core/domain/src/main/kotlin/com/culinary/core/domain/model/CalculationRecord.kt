package com.culinary.core.domain.model

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import java.time.Instant

/** 计算记录状态；`CONFIRMED` 之后不可再修改内容（MB-D-017）。 */
enum class CalculationRecordStatus {
    PREVIEW,
    CONFIRMED,
}

/**
 * 计算记录（MB-D-017）：保存 `recipe_version_id`、输入份数、需求行、引擎版本与
 * `payload_hash`，确认后不可变，供旧结果回放。
 *
 * 不可变由三处共同保证：
 * 1. 所有字段 `val`，不提供修改方法；
 * 2. [confirm] 只接受 `PREVIEW`，重复确认返回 `CUL-INV-003`；
 * 3. 持久化契约要求 insert-once（见 `CalculationRecordRepository`）。
 */
data class CalculationRecord(
    val id: CalculationRecordId,
    val recipeVersionId: RecipeVersionId,
    val servings: Servings,
    val lines: List<RequirementLine>,
    val engineVersion: String,
    val payloadHash: String,
    val status: CalculationRecordStatus,
    val createdAt: Instant,
    val confirmedAt: Instant? = null,
) {
    init {
        require(engineVersion.isNotBlank()) { "engineVersion must not be blank" }
        require(PAYLOAD_HASH.matches(payloadHash)) {
            "payloadHash must be 64 lowercase hex characters"
        }
        require(lines.map { it.ingredientId to it.quantity.unit.id }.toSet().size == lines.size) {
            "calculation record lines must be unique per ingredient and unit"
        }
        when (status) {
            CalculationRecordStatus.PREVIEW -> require(confirmedAt == null) {
                "preview record must not carry confirmedAt"
            }

            CalculationRecordStatus.CONFIRMED -> require(confirmedAt != null) {
                "confirmed record must carry confirmedAt"
            }
        }
    }

    val isConfirmed: Boolean get() = status == CalculationRecordStatus.CONFIRMED

    /**
     * 确认记录：只允许 `PREVIEW → CONFIRMED`，且必须提供确认时间。
     * 已确认记录再次确认会被拒绝，保证"确认后不可变"。
     */
    fun confirm(at: Instant): Outcome<CalculationRecord> =
        when (status) {
            CalculationRecordStatus.CONFIRMED ->
                Outcome.failure(
                    CulinaryError.Domain(
                        detail =
                            DomainDetail.InvalidState(
                                entity = "calculation_record",
                                expected = "PREVIEW",
                                actual = status.name,
                            ),
                        code = ErrorCode.INV_003,
                    ),
                )

            CalculationRecordStatus.PREVIEW ->
                Outcome.success(
                    copy(status = CalculationRecordStatus.CONFIRMED, confirmedAt = at),
                )
        }

    private companion object {
        val PAYLOAD_HASH = Regex("^[0-9a-f]{64}$")
    }
}
