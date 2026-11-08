package com.culinary.core.domain.usecase.archive

import com.culinary.core.domain.model.ArchiveImage
import com.culinary.core.domain.model.ArchiveImageOwner
import com.culinary.core.domain.repository.ArchiveImageRepository

class ArchiveImageUseCase(
    private val repository: ArchiveImageRepository? = null,
) {
    suspend fun list(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): List<ArchiveImage> = repository?.list(owner, ownerId).orEmpty()

    suspend fun read(imageId: String): ByteArray? = repository?.read(imageId)

    suspend fun primary(
        owner: ArchiveImageOwner,
        ownerId: String,
    ): ByteArray? {
        val images = repository?.list(owner, ownerId).orEmpty()
        val image = images.firstOrNull { it.role == "PRIMARY" } ?: images.firstOrNull()
        return image?.let { repository?.read(it.id) }
    }
}
