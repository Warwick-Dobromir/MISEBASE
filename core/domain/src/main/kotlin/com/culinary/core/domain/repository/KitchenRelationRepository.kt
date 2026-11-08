package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.model.KitchenRelationDraft
import kotlinx.coroutines.flow.Flow

/** 厨房档案关系读写端口；种子导入器与酱汁编辑器共用同一张关系表。 */
interface KitchenRelationRepository {
    fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>>

    /**
     * 替换某档案的某一类手工关系。
     *
     * 只影响 [sourceKind] 的 [targetKind] 关系；关系 ID 由实现层生成，
     * 同一目标不能重复。用于酱汁“衍生关系”保存。
     */
    suspend fun replaceRelations(
        sourceRecordId: String,
        sourceKind: KitchenRecordKind,
        targetKind: KitchenRecordKind,
        relations: List<KitchenRelationDraft>,
    ): Outcome<Unit>
}
