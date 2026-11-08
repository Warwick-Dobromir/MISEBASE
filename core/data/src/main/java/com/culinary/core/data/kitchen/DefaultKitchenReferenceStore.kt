package com.culinary.core.data.kitchen

import com.culinary.core.common.Outcome
import com.culinary.core.common.map
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind
import com.culinary.core.domain.usecase.kitchen.ArchiveKitchenReferenceUseCase
import com.culinary.core.domain.usecase.kitchen.GetKitchenReferenceUseCase
import com.culinary.core.domain.usecase.kitchen.ListKitchenReferencesUseCase
import com.culinary.core.domain.usecase.kitchen.SaveKitchenReferenceInput
import com.culinary.core.domain.usecase.kitchen.SaveKitchenReferenceUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 新增参考资料 app 端口实现：只转发 domain 用例。 */
class DefaultKitchenReferenceStore
    @Inject
    constructor(
        private val listReferences: ListKitchenReferencesUseCase,
        private val getReference: GetKitchenReferenceUseCase,
        private val saveReference: SaveKitchenReferenceUseCase,
        private val archiveReference: ArchiveKitchenReferenceUseCase,
    ) : KitchenReferenceStore {
        override fun observe(type: KitchenReferenceType): Flow<List<KitchenReferenceRecord>> =
            listReferences(type.toDomain()).map { references -> references.map(KitchenReference::toRecord) }

        override suspend fun get(id: String): Outcome<KitchenReferenceRecord> =
            getReference(KitchenReferenceId(id)).map(KitchenReference::toRecord)

        override suspend fun save(
            type: KitchenReferenceType,
            id: String?,
            name: String,
            category: String?,
            description: String?,
            englishName: String?,
        ): Outcome<String> =
            saveReference(
                SaveKitchenReferenceInput(
                    kind = type.toDomain(),
                    name = name,
                    id = id?.let(::KitchenReferenceId),
                    category = category,
                    description = description,
                    englishName = englishName,
                ),
            ).map { it.value }

        override suspend fun archive(id: String): Outcome<Unit> = archiveReference(KitchenReferenceId(id))
    }

private fun KitchenReference.toRecord(): KitchenReferenceRecord =
    KitchenReferenceRecord(
        id = id.value,
        type = kind.toAppType(),
        name = name,
        category = category,
        description = description,
        englishName = englishName,
        seedNote = seedNote,
        isSeed = isSeed,
    )

private fun KitchenReferenceKind.toAppType(): KitchenReferenceType =
    when (this) {
        KitchenReferenceKind.TECHNIQUE -> KitchenReferenceType.TECHNIQUE
        KitchenReferenceKind.PRODUCTION_STANDARD -> KitchenReferenceType.PRODUCTION_STANDARD
        KitchenReferenceKind.STORAGE_STANDARD -> KitchenReferenceType.STORAGE_STANDARD
    }

private fun KitchenReferenceType.toDomain(): KitchenReferenceKind =
    when (this) {
        KitchenReferenceType.TECHNIQUE -> KitchenReferenceKind.TECHNIQUE
        KitchenReferenceType.PRODUCTION_STANDARD -> KitchenReferenceKind.PRODUCTION_STANDARD
        KitchenReferenceType.STORAGE_STANDARD -> KitchenReferenceKind.STORAGE_STANDARD
    }
