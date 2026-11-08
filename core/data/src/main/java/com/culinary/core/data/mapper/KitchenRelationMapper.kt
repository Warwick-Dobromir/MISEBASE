package com.culinary.core.data.mapper

import com.culinary.core.database.entity.KitchenRelationEntity
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord

/** 关系 Entity ↔ 领域模型；未知类型跳过，不让脏行阻塞详情页。 */
object KitchenRelationMapper {
    fun toDomain(entity: KitchenRelationEntity): KitchenRelatedRecord? {
        val sourceKind = kind(entity.sourceKind) ?: return null
        val targetKind = kind(entity.targetKind) ?: return null
        return KitchenRelatedRecord(
            sourceRecordId = entity.sourceRecordId,
            sourceKind = sourceKind,
            targetRecordId = entity.targetRecordId,
            targetKind = targetKind,
            targetLabel = entity.targetLabel,
            relationLabel = entity.relationLabel,
            sortOrder = entity.sortOrder,
        )
    }

    fun kind(value: String): KitchenRecordKind? = KitchenRecordKind.entries.firstOrNull { it.name == value }
}
