package com.culinary.core.data.mapper

import com.culinary.core.database.entity.ArchiveCategoryAssignmentEntity
import com.culinary.core.database.entity.ArchiveCategoryEntity
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveCategoryAssignment
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.KitchenRecordKind

object ArchiveCategoryMapper {
    fun toDomain(
        entity: ArchiveCategoryEntity,
        path: List<String> = listOf(entity.name),
        depth: Int = path.size,
    ): ArchiveCategory =
        ArchiveCategory(
            id = entity.id,
            library = ArchiveLibrary.valueOf(entity.library),
            parentId = entity.parentId,
            name = entity.name,
            normalizedName = entity.normalizedName,
            englishName = entity.englishName,
            sortOrder = entity.sortOrder,
            status =
                when (entity.status) {
                    EntityStatusDb.ACTIVE -> EntityStatus.ACTIVE
                    EntityStatusDb.ARCHIVED -> EntityStatus.ARCHIVED
                },
            testBatch = entity.testBatch,
            depth = depth,
            path = path,
        )

    fun assignmentToDomain(entity: ArchiveCategoryAssignmentEntity): ArchiveCategoryAssignment =
        ArchiveCategoryAssignment(
            recordKind = KitchenRecordKind.valueOf(entity.recordKind),
            recordId = entity.recordId,
            categoryId = entity.categoryId,
        )
}
