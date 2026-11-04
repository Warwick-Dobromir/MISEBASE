package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.RecipeVersionId

/**
 * 计算记录持久化契约（MB-D-017）。
 *
 * **当前 Schema v1 没有计算记录表**，因此本阶段只提供契约与 fake；
 * 持久化实现需要 Schema 变更授权（新增表/迁移），不在 Stage 2 实施。
 *
 * 契约：
 * - 只接受 `CONFIRMED` 记录（`saveConfirmed`），`PREVIEW` 必须拒绝；
 * - 记录一经保存不可修改：同 ID 再次保存只能返回冲突，不得覆盖；
 * - `payload_hash` 相同的重复提交不得产生第二条记录。
 */
interface CalculationRecordRepository {
    suspend fun saveConfirmed(record: CalculationRecord): Outcome<CalculationRecord>

    suspend fun find(id: CalculationRecordId): Outcome<CalculationRecord?>

    suspend fun findByRecipeVersion(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>>

    suspend fun findByPayloadHash(payloadHash: String): Outcome<CalculationRecord?>
}
