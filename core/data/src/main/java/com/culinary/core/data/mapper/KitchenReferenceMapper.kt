package com.culinary.core.data.mapper

import com.culinary.core.database.entity.KitchenReferenceEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.KitchenReferenceKindDb
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind

/** 新增厨房参考资料 Entity ↔ 领域模型。 */
object KitchenReferenceMapper {
    fun toDomain(entity: KitchenReferenceEntity): KitchenReference =
        KitchenReference(
            id = KitchenReferenceId(entity.id),
            kind =
                when (entity.kind) {
                    KitchenReferenceKindDb.TECHNIQUE -> KitchenReferenceKind.TECHNIQUE
                    KitchenReferenceKindDb.PRODUCTION_STANDARD -> KitchenReferenceKind.PRODUCTION_STANDARD
                    KitchenReferenceKindDb.STORAGE_STANDARD -> KitchenReferenceKind.STORAGE_STANDARD
                },
            name = entity.name,
            normalizedName = entity.normalizedName,
            category = entity.category,
            description = entity.description,
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
            englishName = entity.englishName,
            seedNote = entity.seedNote,
            seedId = entity.seedId,
            seedVersion = entity.seedVersion,
        )

    fun toEntity(
        reference: KitchenReference,
        createdAt: Long,
        updatedAt: Long,
        revision: Int,
    ): KitchenReferenceEntity =
        KitchenReferenceEntity(
            id = reference.id.value,
            kind =
                when (reference.kind) {
                    KitchenReferenceKind.TECHNIQUE -> KitchenReferenceKindDb.TECHNIQUE
                    KitchenReferenceKind.PRODUCTION_STANDARD -> KitchenReferenceKindDb.PRODUCTION_STANDARD
                    KitchenReferenceKind.STORAGE_STANDARD -> KitchenReferenceKindDb.STORAGE_STANDARD
                },
            name = reference.name,
            normalizedName = reference.normalizedName,
            category = reference.category,
            description = reference.description,
            status =
                when (reference.status) {
                    EntityStatus.ACTIVE -> EntityStatusDb.ACTIVE
                    EntityStatus.ARCHIVED -> EntityStatusDb.ARCHIVED
                },
            englishName = reference.englishName,
            seedNote = reference.seedNote,
            seedId = reference.seedId,
            seedVersion = reference.seedVersion,
            createdAt = createdAt,
            updatedAt = updatedAt,
            revision = revision,
        )
}
